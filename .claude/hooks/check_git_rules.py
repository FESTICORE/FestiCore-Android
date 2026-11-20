"""Git 규칙 검사 (.claude/docs/git-workflow.md). check-git-rules.sh가 git commit / push가 보일 때만 호출한다.

  1) 보호 브랜치(main, develop)에 직접 커밋 · 푸시 금지, main · develop 삭제 금지
  2) 작업 브랜치 이름: feature/{영문 소문자 kebab-case} (이슈 번호를 넣지 않는다)
  3) 커밋 제목: {한국어 설명} #{이슈번호} (타입 접두사 없음)
  4) .DS_Store 커밋 금지
  5) 커밋 메시지 · PR 본문에 AI 표시(세션 링크 · 작성자와 관계없는 Co-Authored-By 트레일러 등) 금지
  6) pre-commit 검증 게이트 우회 금지(--no-verify · -n · core.hooksPath 변경)

위반하면 stderr에 이유를 쓰고 exit 2 → Claude Code가 명령을 막는다.
직접 실행: python3 check_git_rules.py --check-message "설명을 추가한다 #1" / --check-branch "feature/x"
          printf '%s' "$본문" | python3 check_git_rules.py --check-body
"""
import json
import os
import re
import shlex
import subprocess
import sys

ISSUE_SUFFIX_RE = re.compile(r"\S #[0-9]+$")
BRANCH_RE = re.compile(r"^feature/[a-z0-9]+(-[a-z0-9]+)*$")
PROTECTED = {"main", "develop"}
HANGUL_RE = re.compile(r"[가-힣]")
SQUASH_SUFFIX_RE = re.compile(r" \(#[0-9]+\)$")

GIT_OPTS = r"(?:\s+(?:-C|-c|--git-dir|--work-tree)\s+\S+|\s+-\S+)*"   # git -C 경로 commit 등
# 명령이 시작되는 자리(처음 · && · || · ; · | · 줄바꿈 · $( · ( 뒤)에 오는 git만 실제 명령으로 본다.
# echo "git commit" 같은 문자열이나 heredoc 본문 속 문장은 검사하지 않는다.
CMD_START = r"(?:^|&&|\|\||;|\||\n|\$\(|\()\s*(?:\w+=\S+\s+)*"
COMMIT_RE = re.compile(CMD_START + r"git" + GIT_OPTS + r"\s+commit(?:\s|$)")
PUSH_RE = re.compile(CMD_START + r"git" + GIT_OPTS + r"\s+push(?:\s|$)")
SEPARATOR_RE = re.compile(r"&&|\|\||;|\||\n")
PUSH_DELETE_RE = re.compile(r"(?:--delete|-d)\s.*\b(?:main|develop)\b|\s:(?:main|develop)(?:\s|$)")
PUSH_PROTECTED_RE = re.compile(r"[\s:](?:main|develop)(?:\s|$)")
HEREDOC_RE = re.compile(r"<<-?\s*['\"]?(\w+)['\"]?\s*\n(.*?)\n\s*\1", re.S)
PLACEHOLDER_RE = re.compile(r"@@HEREDOC(\d+)@@")
GH_PR_RE = re.compile(CMD_START + r"gh\s+pr\s+(?:create|edit|merge)(?:\s|$)")
BODY_FILE_RE = re.compile(r"(?:--body-file|-F)\s+(\"[^\"]+\"|'[^']+'|\S+)")
# 바꾸는 경우만 본다: -c core.hooksPath=값, config core.hooksPath 값, --unset · --replace-all 등.
# 조회와 게이트를 켜는 값(.githooks)은 통과.
GATE_VALUE = r"(?!(?-i:\.githooks|\"\.githooks\"|'\.githooks')(?:\s|$|[;&|]))"  # 따옴표 포함 값 전체가 정확히 .githooks
HOOKS_PATH_CHANGE_RE = re.compile(
    r"core\.hooksPath[\"']?\s*=\s*" + GATE_VALUE + r"|config\b[^;&|\n]*?(?:--unset|--replace-all|--add)[^;&|\n]*core\.hooksPath"
    r"|config\b(?:\s+--(?:local|global|system|worktree))*\s+[\"']?core\.hooksPath[\"']?\s+" + GATE_VALUE + r"[^\s;&|]",
    re.I)
SHELL_STRING_PREV = {"-c", "eval"}  # bash -c '…' · eval '…' 문자열 안의 명령도 본다
NO_VERIFY = "--no-" + "verify"
VALUE_OPTS = {"-m", "--message", "-F", "--file", "-C", "-c", "--reuse-message", "--reedit-message",
              "-t", "--template", "--author", "--date", "--fixup", "--squash", "--cleanup"}
# Co-Authored-By 트레일러는 작성자 이름과 관계없이 막는다(Claude · Codex · Gemini · 사람 모두).
# 세션 링크는 도구와 관계없이 막는다(Claude · ChatGPT/Codex · Gemini).
AI_MARK_RE = re.compile(
    r"claude\.ai/(?:code/session|chat/|share/)|Claude-Session:|chatgpt\.com/(?:c|codex|share)/|chat\.openai\.com/"
    r"|gemini\.google\.com/(?:app|share)/|g\.co/gemini/share/"
    r"|Co-Authored-By\s*:|Generated with \[?Claude|noreply@anthropic\.com", re.I)


def check_ai_mark(text, where):
    if AI_MARK_RE.search(text or ""):
        fail(f"{where}에 AI 표시(세션 링크 · Co-Authored-By 트레일러 등)를 넣지 않습니다. 해당 줄을 지우세요.")


def fail(reason):
    sys.stderr.write(f"[git 규칙 위반] {reason}\n규칙: .claude/docs/git-workflow.md\n")
    sys.exit(2)


def skips_hooks(command):
    """명령 안의 git commit에 훅 건너뛰기 옵션(긴 이름 또는 묶음 short 옵션 속 -n)이 있는지.
    따옴표 안의 ; 로 잘리지 않게 명령 전체를 토큰으로 나누고, bash -c 등에 넘긴 문자열도 다시 본다."""
    try:
        lexer = shlex.shlex(command, posix=True, punctuation_chars=";&|()")
        lexer.whitespace_split = True
        tokens = list(lexer)
    except ValueError:
        return NO_VERIFY in command
    in_commit = skip = False
    prev = ""
    for token in tokens:
        before, prev = prev, token
        if skip:  # 값을 받는 옵션 뒤 토큰은 값이다(-m ";" 포함)
            skip = False
            continue
        if token and set(token) <= set(";&|()\n"):
            in_commit = False
            continue
        if before in SHELL_STRING_PREV and "commit" in token and skips_hooks(token):
            return True
        if not in_commit:
            in_commit = token == "commit"
            continue
        if token.startswith("--no-v") and NO_VERIFY.startswith(token):  # git은 긴 옵션 축약을 받는다
            return True
        elif token in VALUE_OPTS:
            skip = True
        elif token.startswith("-") and not token.startswith("--"):
            for flag in token[1:]:
                if flag == "n":
                    return True
                if f"-{flag}" in VALUE_OPTS:  # -am "메시지"처럼 값을 받는 옵션 뒤 글자는 값이다
                    break
    return False


def check_message(subject):
    if subject.startswith(("Merge ", "Revert ")):
        return
    subject = SQUASH_SUFFIX_RE.sub("", subject)   # 스쿼시 머지가 붙인 " (#PR번호)"는 규칙 밖이다
    if not HANGUL_RE.search(subject):
        fail(f"커밋 제목은 한국어로 씁니다. 입력: '{subject}'")
    if not ISSUE_SUFFIX_RE.search(subject):
        fail(f"커밋 제목 끝에 작업 이슈 번호를 '#12'처럼 붙입니다. 입력: '{subject}'")


def check_branch(branch):
    if branch in PROTECTED:
        fail(f"보호 브랜치 '{branch}'에는 직접 커밋 · 푸시하지 않습니다. feature/<작업명> 브랜치에서 develop으로 PR을 보내세요.")
    if not BRANCH_RE.match(branch):
        fail(f"브랜치 이름은 'feature/<작업명>'(영문 소문자 kebab-case, 이슈 번호 없음)입니다. 현재: '{branch}'")


def strip_heredocs(cmd):
    """heredoc 본문을 자리표시(@@HEREDOC{n}@@)로 바꾸고 본문 목록을 돌려준다."""
    bodies = []

    def keep(match):
        bodies.append(match.group(2))
        return f"<<{match.group(1)}\n@@HEREDOC{len(bodies) - 1}@@\n{match.group(1)}"

    return HEREDOC_RE.sub(keep, cmd), bodies


def segment(skeleton, match):
    """git 명령이 시작된 곳부터 다음 명령 구분자 전까지. $( 가 열려 있으면 끝까지 본다."""
    start = match.end()
    end = SEPARATOR_RE.search(skeleton, start)
    if end is None or "$(" in skeleton[start:end.start()]:
        return skeleton[start:]
    return skeleton[start:end.start()]


def first_line(text):
    lines = [line.strip() for line in (text or "").splitlines() if line.strip()]
    return lines[0] if lines else ""


def has_heredoc(part):
    """따옴표 밖의 << 만 heredoc이다(-m "a << b"는 아니다)."""
    try:
        lexer = shlex.shlex(part, posix=True, punctuation_chars="<>;&|()")
        lexer.whitespace_split = True
        return any(token in ("<<", "<<-") for token in lexer)
    except ValueError:
        return False


def message_file(tokens):
    """git commit의 -F 값: -F 경로 · -F경로 · --file 경로 · --file=경로 · -aF 경로."""
    skip = False
    for i, token in enumerate(tokens):
        nxt = tokens[i + 1] if i + 1 < len(tokens) else None
        if skip:
            skip = False
        elif token in ("-F", "--file"):
            return nxt
        elif token.startswith("--file="):
            return token.split("=", 1)[1]
        elif token in VALUE_OPTS:
            skip = True
        elif token.startswith("-") and not token.startswith("--"):
            for j, flag in enumerate(token[1:]):
                if flag == "F":
                    return token[j + 2:] or nxt
                if f"-{flag}" in VALUE_OPTS:  # -am "메시지": 남은 글자나 다음 토큰은 값이다
                    skip = not token[j + 2:]
                    break
    return None


def commit_subject(skeleton, match, part, bodies):
    """실제로 쓰일 메시지의 첫 줄. heredoc은 메시지 입력일 때만 본다:
    -F - <<EOF(구간이 줄바꿈에서 끊겨 본문은 커밋 뒤 첫 heredoc) · -m "$(cat <<EOF …)"(값 안의 자리표시)."""
    tokens = []
    # 구간은 따옴표를 모르고 ; 등에서 잘리므로(-m 'a; b') 따옴표가 안 닫히면 명령 끝까지 다시 읽는다.
    for text in (part, skeleton[match.end():]):
        try:  # 따옴표 밖 ; & | 는 공백 없이 붙어 있어도 따로 자른다
            lexer = shlex.shlex(text, posix=True, punctuation_chars=";&|")
            lexer.whitespace_split = True
            tokens = list(lexer)
            break
        except ValueError:
            continue
    for i, token in enumerate(tokens):   # $( 때문에 구간이 끝까지 이어져도 이 커밋의 인자만 본다
        if set(token) <= set(";&|"):
            tokens = tokens[:i]
            break
    path = message_file(tokens)
    if path == "-":
        placeholder = PLACEHOLDER_RE.search(skeleton, match.end()) if has_heredoc(part) else None
        return first_line(bodies[int(placeholder.group(1))]) if placeholder else ""
    if path:
        try:
            with open(os.path.expanduser(path), encoding="utf-8") as f:
                return first_line(f.read())   # 파일 내용은 셸이 바꾸지 않으므로 $도 그대로 검사한다
        except OSError:
            return ""
    for i, token in enumerate(tokens):
        value = None
        if token in ("-m", "--message") and i + 1 < len(tokens):
            value = tokens[i + 1]
        elif token.startswith("--message="):
            value = token.split("=", 1)[1]
        if value is not None:
            placeholder = PLACEHOLDER_RE.search(value)
            if placeholder:
                return first_line(bodies[int(placeholder.group(1))])
            first = value.splitlines()[0] if value else ""
            return "" if "$" in first else first   # 셸 변수 메시지는 실행 전에 알 수 없다
    return ""


def git(*args):
    try:
        return subprocess.run(["git", *args], capture_output=True, text=True, timeout=3).stdout.strip()
    except (OSError, subprocess.TimeoutExpired):
        return ""


def current_branch():
    """.git/HEAD를 직접 읽는다(프로세스 생성 없음). worktree · 하위 폴더처럼 .git 폴더가 없을 때만 git을 부른다."""
    if os.path.isdir(".git"):
        try:
            with open(".git/HEAD", encoding="utf-8") as head:
                ref = head.read().strip()
            return ref[len("ref: refs/heads/"):] if ref.startswith("ref: refs/heads/") else "HEAD"
        except OSError:
            pass
    return git("rev-parse", "--abbrev-ref", "HEAD")


CD_RE = re.compile(r"(?:^|&&|;|\n)\s*cd\s+(\"[^\"]+\"|'[^']+'|\S+)")
GIT_C_RE = re.compile(r"git\s+-C\s+(\"[^\"]+\"|'[^']+'|\S+)")


def command_dir(skeleton, first_git, base):
    """git 명령이 실제로 도는 폴더: 그 앞의 마지막 `cd 경로` 또는 `git -C 경로`. 없으면 훅 입력의 cwd."""
    def resolve(path):
        path = os.path.expanduser(path.strip("\"'"))
        return path if os.path.isabs(path) else os.path.join(base, path)

    target = base
    for m in CD_RE.finditer(skeleton[:first_git.start()]):
        target = resolve(m.group(1))
    git_c = GIT_C_RE.search(skeleton, first_git.start(), first_git.end())
    if git_c:
        target = resolve(git_c.group(1))
    return target if os.path.isdir(target) else base


def main():
    if len(sys.argv) == 3 and sys.argv[1] == "--check-message":
        check_message(sys.argv[2]); print("OK"); return
    if len(sys.argv) == 3 and sys.argv[1] == "--check-branch":
        check_branch(sys.argv[2]); print("OK"); return
    if len(sys.argv) == 2 and sys.argv[1] == "--check-body":  # CI: 커밋 · PR 본문 전체를 stdin으로 받는다
        check_ai_mark(sys.stdin.read(), "커밋 메시지 · PR 본문"); print("OK"); return

    try:
        data = json.load(sys.stdin)
    except ValueError:
        return
    cmd = data.get("tool_input", {}).get("command", "")

    skeleton, bodies = strip_heredocs(cmd)
    if skips_hooks(cmd):
        fail("pre-commit 검증 게이트를 건너뛰는 옵션(-n 등)은 쓰지 않습니다. verify를 통과시킨 뒤 커밋하세요.")
    if HOOKS_PATH_CHANGE_RE.search(skeleton):
        fail("core.hooksPath를 바꾸면 pre-commit 검증 게이트가 꺼집니다. 바꾸지 않습니다.")
    marked = [segment(skeleton, m) for m in list(GH_PR_RE.finditer(skeleton)) + list(COMMIT_RE.finditer(skeleton))]
    for part in marked:
        check_ai_mark(PLACEHOLDER_RE.sub(lambda m: bodies[int(m.group(1))], part), "커밋 메시지 · PR 본문")
        if "<<" in part:  # git commit -F - <<EOF 처럼 본문을 heredoc으로 넘기는 경우
            for body in bodies:
                check_ai_mark(body, "커밋 메시지 · PR 본문")
        for m in BODY_FILE_RE.finditer(part):
            path = os.path.expanduser(m.group(1).strip("\"'"))
            try:
                with open(path, encoding="utf-8") as f:
                    check_ai_mark(f.read(), "PR 본문 파일")
            except OSError:
                pass
    commit_matches = list(COMMIT_RE.finditer(skeleton))
    push_matches = list(PUSH_RE.finditer(skeleton))
    commits = [segment(skeleton, m) for m in commit_matches]
    pushes = [segment(skeleton, m) for m in push_matches]
    if not (commits or pushes):
        return

    base = data.get("cwd") or os.environ.get("CLAUDE_PROJECT_DIR") or "."
    first_git = min(commit_matches + push_matches, key=lambda m: m.start())
    os.chdir(command_dir(skeleton, first_git, base))
    branch = current_branch()
    if branch and branch != "HEAD":
        check_branch(branch)

    if any(PUSH_DELETE_RE.search(part) for part in pushes):
        fail("main · develop 브랜치는 삭제하지 않습니다.")
    if any(PUSH_PROTECTED_RE.search(part) for part in pushes):
        fail("보호 브랜치(main · develop)로 직접 푸시하지 않습니다. PR을 사용하세요.")
    if not commits:
        return

    staged = git("diff", "--cached", "--name-only").splitlines()
    if any(path == ".DS_Store" or path.endswith("/.DS_Store") for path in staged):
        fail(".DS_Store가 스테이징돼 있습니다. 'git rm --cached <경로>'로 빼세요.")
    for m, part in zip(commit_matches, commits):
        subject = commit_subject(skeleton, m, part, bodies)
        if subject:  # -m · -F(heredoc · 파일) 없이 편집기로 쓰는 커밋은 메시지를 검사하지 않는다
            check_message(subject)
    check_verified()


def check_verified():
    """검증 게이트: 코드 변경이 있는데 verify 통과 기록과 지문이 다르면 커밋을 막는다."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # -I 실행이라 직접 넣는다
    import harness_state as hs
    try:
        root = hs.repo_root(".")
        hs.install_git_hooks(root)  # 실제 차단은 커밋 직전 index를 보는 pre-commit 훅이 한다
        files = hs.unverified(root)
    except Exception:
        return  # 판단할 수 없으면 막지 않는다 (git 오류는 git이 직접 알린다)
    if files:
        shown = ", ".join(files[:5]) + (f" 외 {len(files) - 5}개" if len(files) > 5 else "")
        fail(f"검증하지 않은 코드 변경이 있습니다({shown}). "
             ".claude/scripts/verify로 통과를 확인한 뒤 커밋하세요. 통과 후 코드를 고쳤으면 다시 실행합니다.")


if __name__ == "__main__":
    main()
