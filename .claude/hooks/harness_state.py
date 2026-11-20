"""하네스 공용 상태: 코드 변경 판별 · 작업 트리 지문 · 검증 기록. (.claude/docs/harness.md)

verify(.claude/scripts/verify) · 커밋 게이트(check_git_rules.py) · 종료 확인(stop_guard.py)이 함께 쓴다.
상태는 .claude/state/(git 제외)에 둔다.
"""
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import time

# 검증이 필요한 코드 경로. 문서(*.md)만 바뀌면 검증하지 않는다. 하네스 훅 · 스크립트 · 워크플로도 코드로 본다.
CODE_DIRS = ("app", "feature", "domain", "service", "core", "ui", "build-logic", "gradle",
             ".claude/hooks", ".claude/scripts", ".githooks", ".github/workflows")
CODE_FILES = ("settings.gradle.kts", "build.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
              ".mise.toml", ".editorconfig", ".claude/settings.json")
STATE_DIR = os.path.join(".claude", "state")
VERIFY_FILE = os.path.join(STATE_DIR, "verify.json")
GIT_HOOKS_DIR = ".githooks"
DELETED = "deleted"
MAX_FAILURES = 2  # 같은 단계가 이 횟수만큼 연속 실패하면 사용자에게 보고한다


def git(root, *args, env=None):
    # 개인 전역 gitignore(예: .claude/)는 보지 않는다. 저장소 .gitignore만 적용해 하네스 파일 변경도 코드 변경으로 본다.
    return subprocess.run(
        ["git", "-C", root, "-c", "core.excludesFile=/dev/null", *args],
        capture_output=True, text=True, env=env, check=True, timeout=30, stdin=subprocess.DEVNULL,
    ).stdout


def is_code(path):
    if path.endswith(".md"):
        return False
    return path in CODE_FILES or any(path.startswith(d + "/") for d in CODE_DIRS)


def repo_root(start):
    return git(start, "rev-parse", "--show-toplevel").strip()


def base_ref(root):
    """비교 기준: develop과 갈라진 지점. 원격이 없으면 로컬 develop."""
    for ref in ("origin/develop", "develop"):
        try:
            return git(root, "merge-base", "HEAD", ref).strip()
        except subprocess.CalledProcessError:
            continue
    return "HEAD"


def changed_code_files(root):
    """develop 이후 바뀐 코드 파일(커밋 · 스테이징 · 작업 중 · 새 파일 모두)."""
    tracked = git(root, "diff", "--name-only", base_ref(root)).splitlines()
    untracked = git(root, "ls-files", "--others", "--exclude-standard").splitlines()
    return sorted({p for p in tracked + untracked if is_code(p)})


def fingerprint(root):
    """작업 트리의 코드 파일 내용 지문. 커밋으로 나눠 담아도 내용이 같으면 같은 값이다."""
    return hashlib.sha256("\n".join(working_code(root)).encode()).hexdigest()


def code_entries(root):
    """작업 트리의 코드 파일 {경로: "모드 blob"}. HEAD에 있는데 지운 파일은 DELETED. pre-commit 훅이 index와 대조한다."""
    entries = {}
    for line in working_code(root):
        meta, path = line.split("\t", 1)
        mode, _, sha = meta.split()
        entries[path] = f"{mode} {sha}"
    head = git(root, "ls-tree", "-r", "-z", "--name-only", "HEAD", "--", *CODE_DIRS, *CODE_FILES) if has_head(root) else ""
    for path in head.split("\0"):
        if path and path not in entries and not path.endswith(".md"):
            entries[path] = DELETED
    return entries


def has_head(root):
    try:
        git(root, "rev-parse", "--verify", "-q", "HEAD")
        return True
    except subprocess.CalledProcessError:
        return False


def working_code(root):
    fd, index = tempfile.mkstemp(prefix="harness-index-")
    os.close(fd)
    try:
        real_index = git(root, "rev-parse", "--path-format=absolute", "--git-path", "index").strip()
        if os.path.exists(real_index):
            shutil.copyfile(real_index, index)  # stat 정보를 살려 다시 해시하지 않게 한다
        else:
            os.unlink(index)
        env = dict(os.environ, GIT_INDEX_FILE=index)
        git(root, "add", "-A", "--", *present_code_paths(root, env), env=env)
        tree = git(root, "write-tree", env=env).strip()
        listing = git(root, "ls-tree", "-r", "-z", tree, "--", *CODE_DIRS, *CODE_FILES).split("\0")
        return [line for line in listing if line and not line.endswith(".md")]  # .spec 등 문서는 지문에서 뺀다
    finally:
        if os.path.exists(index):
            os.unlink(index)


def present_code_paths(root, env=None):
    """디스크에 있거나 index에 남은 코드 경로. 지운 루트 파일도 넣어야 삭제가 반영된다."""
    tracked = [p for p in git(root, "ls-files", "-z", "--", *CODE_DIRS, *CODE_FILES, env=env).split("\0") if p]

    def present(path):
        return os.path.exists(os.path.join(root, path)) or any(t == path or t.startswith(path + "/") for t in tracked)

    return [p for p in CODE_DIRS + CODE_FILES if present(p)]


def load(root):
    try:
        with open(os.path.join(root, VERIFY_FILE), encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def save(root, state):
    os.makedirs(os.path.join(root, STATE_DIR), exist_ok=True)
    state["at"] = time.strftime("%Y-%m-%dT%H:%M:%S")
    with open(os.path.join(root, VERIFY_FILE), "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2)


def unverified(root):
    """검증이 필요한데 마지막 통과 기록과 지문이 다르면 바뀐 코드 파일 목록을 돌려준다."""
    files = changed_code_files(root)
    if not files:
        return []
    state = load(root)
    if state.get("passed") and state.get("fingerprint") == fingerprint(root):
        return []
    return files


def staged_unverified(root):
    """커밋될 index(GIT_INDEX_FILE 포함)의 코드 변경 중 verify 통과 시점과 내용이 다른 경로."""
    staged = [p for p in git(root, "diff", "--cached", "--name-only", "--no-renames", "-z").split("\0") if p and is_code(p)]
    if not staged:
        return []
    state = load(root)
    if not state.get("passed") or "code" not in state:
        return staged  # 통과 기록이 없으면 삭제만 담은 커밋도 막는다
    verified = state["code"]
    index = {}
    for line in git(root, "ls-files", "-s", "-z", "--", *staged).split("\0"):
        if line:
            meta, path = line.split("\t", 1)
            mode, sha, _ = meta.split()
            index[path] = f"{mode} {sha}"
    return [p for p in staged if index.get(p, DELETED) != verified.get(p)]  # 삭제는 verify 때도 지워져 있어야 한다


def install_git_hooks(root):
    """pre-commit 훅(.githooks)을 켠다. verify와 커밋 게이트가 부른다."""
    if git(root, "config", "--default", "", "--get", "core.hooksPath").strip() != GIT_HOOKS_DIR:
        git(root, "config", "core.hooksPath", GIT_HOOKS_DIR)
