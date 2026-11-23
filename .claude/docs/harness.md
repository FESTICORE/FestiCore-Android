# 에이전트 하네스

하네스는 에이전트가 일하는 환경 전체다. 규칙 파일, 경로별 규칙, 훅, 검증 스크립트로 이루어진다.

- 프롬프트 지시는 확률이고, 실행되는 검사는 보장이다. 실수가 나오면 프롬프트를 고치기보다 그 실수가 구조적으로 불가능하게 만든다.
- 하네스는 세 축으로 관리한다. **Context**(무엇을 읽게 할지) · **Control**(무엇을 막고 확인할지) · **Observability**(무엇이 쓰이는지 보고 정리).
- 규칙 파일은 짧게 유지하고(200줄 미만), 경로별로 나눠 필요한 파일을 다룰 때만 읽게 한다.
- 코드만 봐서는 알 수 없는 팀 규칙만 적는다.
- 규칙은 반드시 지킬 것(Hard)과 권장(Soft)으로 나눈다. Soft는 기존 코드가 다르면 기존 코드를 따른다.
- 별도 오케스트레이터 에이전트를 두지 않는다. 대화 중인 메인 세션이 필요한 에이전트를 직접 부른다.
- 평가 장치(verify · 검사 · 훅 · CI)는 고쳐지는 대상 밖에 둔다. 실패 기록으로 하네스를 고치되, 평가를 약하게 해서 통과하지 않는다(아래 자기개선 루프).

## 구성

| 종류 | 위치 | 역할 | 언제 읽히나 |
|------|------|------|-------------|
| 프로젝트 규칙 | `CLAUDE.md` | 개요 · Hard 규칙 · 문서 색인 | 세션 시작 시 항상 |
| Codex 진입점 | `AGENTS.md` | CLAUDE.md와 .claude/docs를 읽으라는 안내 | Codex 세션 시작 시 |
| 리뷰 기준 | `REVIEW.md` | 심각도 · 제외 · 항상 확인 | 리뷰 시 |
| 상세 문서 | `.claude/docs/*.md` | 영역별 규칙과 예시 | 작업에 맞는 문서를 골라 읽음 |
| 설계 | `.claude/docs/specs/*.md` | 구현 전 합의한 설계 | 해당 작업 시 |
| 경로별 규칙 | `.claude/rules/*.md` | `paths`에 맞는 파일을 다룰 때만 적용 | 자동 (Claude) · 직접 (Codex) |
| 훅 | `.claude/hooks/*` + `.claude/settings.json` · `.githooks/` | 규칙을 실행으로 강제 · 사용 기록 | 도구 실행 전후 · 커밋 · push 직전 · 응답 종료 시 |
| 스크립트 | `.claude/scripts/verify` · `check-boundaries` · `check-test-integrity` · `harness-report` | 검증 실행 · 모듈 경계 · 테스트 끄기 검사 · 사용 · 실패 리포트 | 직접 실행 |
| 로컬 상태 | `.claude/state/` (git 제외) | 검증 기록 · 사용 기록 · 검증 로그 · 검증 실행 기록(`verify-history.jsonl`) | 훅 · 스크립트가 읽고 씀 |

## 경로별 규칙

| 파일 | 적용 경로 |
|------|-----------|
| `.claude/rules/app.md` | `app/**` |
| `.claude/rules/feature.md` | `feature/**` |
| `.claude/rules/domain.md` | `domain/**` |
| `.claude/rules/service-core.md` | `service/**`, `core/**`, `build-logic/**` |
| `.claude/rules/ui.md` | `ui/**` |

파일 맨 위 frontmatter의 `paths`로 적용 범위를 정한다. 본문은 Hard / Soft 순서로 쓰고 상세 문서 경로를 적는다.

## 훅

| 이벤트 | 스크립트 | 검사 내용 |
|--------|----------|-----------|
| `PreToolUse` (Bash) | `check-git-rules.sh` → `check_git_rules.py` | 보호 브랜치(`main` · `develop`) 커밋 · 푸시 금지, 브랜치 이름, 커밋 제목(한국어 · 끝에 ` #이슈`), `.DS_Store` 금지, 커밋 · PR 본문 AI 표시(작성자 무관 `Co-Authored-By` 트레일러 · Claude · ChatGPT/Codex · Gemini 세션 링크) 금지, 훅 건너뛰기 · `core.hooksPath` 변경 금지, **검증 안 된 코드 커밋 금지** |
| `PostToolUse` (Read · Skill) | `usage_log.py` | 하네스 문서 · 규칙 · 스킬 · `.spec` 사용을 `.claude/state/usage.jsonl`에 기록 |
| git `pre-commit` | `.githooks/pre-commit` → `pre_commit_gate.py` | 커밋 순간의 브랜치가 `main` · `develop`이면 커밋 금지, 커밋될 index(`commit -a`의 임시 index 포함)의 코드가 verify 통과 시점과 다르면 커밋 금지 |
| git `pre-push` | `.githooks/pre-push` | push 대상이 `main` · `develop`이면 push 금지 |
| git `pre-merge-commit` | `.githooks/pre-merge-commit` → `pre_commit_gate.py --branch-only` | 보호 브랜치에서 병합 커밋 금지 |
| `Stop` | `stop_guard.py` | 검증 안 된 코드 변경이 있으면 응답 종료를 한 번 막고 verify를 안내 |

공용 판정(코드 변경 · 지문 · 검증 기록)은 `harness_state.py` 하나에 둔다. 코드 경로는 `CODE_DIRS` · `CODE_FILES`다. Gradle 모듈 폴더와 함께 하네스 훅 · 스크립트 · 워크플로(`.claude/hooks` · `.claude/scripts` · `.githooks` · `.github/workflows` · `.claude/settings.json`)도 코드로 본다. `*.md`만 바뀌면 검증하지 않는다.

- PreToolUse 훅은 명령이 실행되기 **전** 작업 트리를 본다. 같은 명령 안에서 고친 뒤 커밋하거나, 명령 시작 뒤 같은 작업 트리를 쓰는 다른 세션이 브랜치를 바꾸면 놓친다. 그래서 실제 차단은 git `pre-commit` · `pre-push` 훅이 커밋 · push 순간에 한다.
- `core.hooksPath=.githooks`는 verify · Claude 커밋 게이트가 켠다. 켠 뒤에는 사람이 터미널에서 커밋해도 같은 검사를 받는다.
- git은 clone 때 훅을 자동으로 켜지 않는다. 새 clone에서는 `git config core.hooksPath .githooks`(또는 `.claude/scripts/verify`)를 먼저 실행한다. 그 전의 Codex · 터미널 커밋은 게이트를 거치지 않는다(PR CI가 규칙 검사를 한 번 더 한다).
- 훅은 빨라야 한다. git commit · push · `gh pr`가 아니면 bash 문자열 비교만 하고 끝낸다. 검사가 필요할 때만 python을 한 번(`-I -S`) 실행한다.
- `$( … )` 안에 heredoc으로 스크립트를 넘기지 않는다. 스크립트는 파일로 둔다.
- 알려진 한계: 문자열 안의 git 명령은 실제 명령 위치(명령 시작)에 올 때만 검사한다.
- 위반하면 `exit 2`와 stderr 메시지로 명령을 막는다. 에이전트는 메시지를 보고 고친 뒤 다시 실행한다.
- 훅을 고치면 단위 테스트(`.claude/hooks/tests`)와 실제 세션 차단을 함께 확인한다.
- CI(`pr-rules.yml`)는 같은 스크립트를 `--check-branch` · `--check-message` · `--check-body`로 부른다.

직접 검사:

```bash
bash .claude/hooks/check-git-rules.sh --check-message "하네스 규칙과 훅을 추가한다 #1"   # 제목 한 줄만
bash .claude/hooks/check-git-rules.sh --check-branch "feature/harness"
git log -1 --format=%B | bash .claude/hooks/check-git-rules.sh --check-body
python3 -m unittest discover -s .claude/hooks/tests
```

## 검증 게이트 (Control)

```bash
.claude/scripts/verify          # develop 이후 바뀐 코드에 맞는 단계만
.claude/scripts/verify --all    # 모든 단계
```

| 바뀐 경로 | 단계 | 명령 |
|-----------|------|------|
| `.claude/hooks` · `.claude/scripts` · `.githooks` · `.claude/settings.json` | hooks-test | `python3 -m unittest discover -s .claude/hooks/tests` |
| 코드 변경이 있으면 항상 | boundaries | `.claude/scripts/check-boundaries` |
| 코드 변경이 있으면 항상 (PR CI에서도) | test-integrity | `.claude/scripts/check-test-integrity` (이슈 참조 없이 테스트를 끈 곳, 아래 평가 장치 보호) |
| `*.kt` · `*.kts` · `gradle/format` · `.editorconfig` · `.mise.toml` | format-kotlin | `gradle/format --check` (ktlint) |
| `app` · `feature` · `domain` · `service` · `core` · `ui` · Gradle 공용 | android | `./gradlew testDebugUnitTest testStageDebugUnitTest :app:assembleStageDebug` |

- `gradle/format` · `gradlew`가 아직 없으면 해당 단계는 건너뛰고 그 사실을 출력한다(Gradle 골격 전).
- 통과하면 작업 트리 코드 내용의 지문과 파일별 blob 목록을 `.claude/state/verify.json`에 남긴다. 커밋을 나눠도 각 파일 내용이 검증한 그대로면 다시 돌리지 않는다.
- 통과 후 코드를 한 줄이라도 고쳐 커밋하면 pre-commit 훅이 막는다. 다시 verify를 돌린다.
- 각 단계 출력은 `.claude/state/verify/{단계}.log`에 남기고 단계별 걸린 시간을 출력한다.
- 실행마다 단계 결과(실패한 단계는 이유와 로그 마지막 10줄)를 `.claude/state/verify-history.jsonl`에 한 줄씩 더한다. 단계 로그는 다음 실행이 덮어쓰지만 이 기록은 지우지 않는다.

모듈 경계 규칙(`check-boundaries`):

| 규칙 | 허용 위치 |
|------|-----------|
| Keystore · `EncryptedSharedPreferences` | `core/datastore` |
| `SharedPreferences` · DataStore | `core/datastore` |
| Room | `core/database` |
| feature `build.gradle.kts`에서 `*.sources` 의존 | 없음 (app만) |

재시도 한도:

| 단계 | 최대 | 넘으면 |
|------|------|--------|
| 빌드 · 테스트 (verify의 같은 단계) | 연속 2번 실패 | 멈추고 시도한 내용 · 실패 원인을 사용자에게 보고, 승인 후 세 번째 시도 |
| 리뷰 (Codex 교차 검토) | 3번 | 남은 지적을 PR 본문에 적고 사용자에게 판단을 맡긴다 |

## 관측과 정리 (Observability)

```bash
.claude/scripts/harness-report            # 최근 30일
.claude/scripts/harness-report --days 90
```

- 문서 · 규칙 · 스킬별 읽은 횟수와 마지막 사용일, 기간 안에 한 번도 쓰이지 않은 항목을 보여준다.
- verify 단계별 실패 / 실행 횟수와 마지막 실패 이유를 보여준다. 같은 단계가 반복해 실패하면 겪은 문제에 적을 후보다.
- 기록에는 경로와 스킬 이름만 남긴다. 사용자 입력은 남기지 않는다.
- 한 달에 한 번 리포트를 보고, 오래 안 읽힌 문서는 합치거나 지운다. 정리는 이슈 · PR로 한다.

## 자기개선 루프

모델은 그대로 두고 하네스(문서 · 스킬 · 훅 · 검사)를 실패 기록으로 고친다. 참고: Lilian Weng "Harness Engineering for Self-Improvement"(Lil'Log, 2026-07-04)와 거기 인용된 ACE(플레이북을 항목 단위로 갱신) · AHE(평가자와 권한은 루프 밖, 고칠 때마다 반증 가능한 예측) · Self-Harness(하네스 변경은 회귀 확인 후 채택).

| 단계 | 하는 일 | 도구 |
|------|---------|------|
| 관찰 | 무엇을 읽었고 어디서 실패했는지 남긴다. 실패한 시도도 지우지 않는다 | `usage.jsonl` · `verify-history.jsonl` · `harness-report` |
| 회고 | 실패를 검증기가 본 증상, 에이전트가 한 행동, 그렇게 된 원인으로 나눠 적는다 | 겪은 문제 항목 |
| 큐레이션 | 겪은 문제를 항목 단위로 더하고 합치고 지운다 | 아래 규칙 |
| 강제 | 두 번째 같은 실수는 문서, 세 번째부터 훅 · 검사 | `.claude/hooks` · `.claude/scripts/check-*` |
| 평가 | 하네스를 고친 변경이 의도한 실패를 막고 정상 작업을 깨지 않는지 확인 | 아래 PR 형식 · `.claude/hooks/tests` |

### 겪은 문제 큐레이션

- 항목 하나는 bullet 하나로 증상 → 원인 → 대처를 쓰고, 끝에 강제 수단(문서 · 훅 · 검사 이름)과 이슈 번호를 단다.
- 항목 단위로만 고친다. 절 전체를 요약해 다시 쓰지 않는다. 요약하면 구체 사실(명령 · 옵션 · 이슈 번호)이 사라진다.
- 더하기: 같은 원인의 실수가 두 번째거나, 처음이라도 다시 겪으면 비용이 큰 문제. 합치기: 원인이 같은 항목. 줄이기 · 지우기: 훅 · 검사가 막게 되면 검사 이름 참조로 줄이고, 원인이 사라지면 지우고 PR 본문에 이유를 적는다.

### 평가 장치 보호

- 평가 장치(`verify` · `.claude/scripts/check-*` · `.claude/hooks` · `.githooks` · `.github/workflows`)를 약하게 하는 변경(단계 삭제 · 조건 완화 · 실패를 성공으로)은 기능 PR에 섞지 않는다. 별도 PR로 이유를 적고 사람의 승인을 받는다. 리뷰는 이 경로를 먼저 본다(`REVIEW.md`).
- 테스트를 꺼서 통과시키지 않는다. `check-test-integrity`가 테스트 파일의 `@Ignore` · `@Disabled`(JUnit · kotlin.test)에 이슈 참조가 없으면 verify와 PR CI(Android PR)에서 실패시킨다. 꼭 꺼야 하면 사용자 승인을 받고 같은 줄이나 바로 윗줄에 이유가 된 이슈 `#번호`를 적는다.
- 한계: PR CI는 그 PR이 바꾼 검사 스크립트를 실행한다. `check-test-integrity`는 줄 단위 글자 검사라 여러 줄 주석 · 문자열 속 예시는 잘못 판정할 수 있다. 두 경우 모두 리뷰가 마지막 확인이다.

### 하네스를 고치는 PR

PR 본문에 막으려는 실패 · 예상 회귀(오탐) · 확인 방법을 적는다. 다음 `harness-report`에서 예측이 맞았는지 본다.

## 겪은 문제

- 같은 작업 트리를 쓰던 다른 세션이 `checkout develop` · `pull`을 한 7초 뒤 feature 브랜치용 커밋 · 푸시가 develop에서 실행됐다(CIRQLE-Native-Mobile, PreToolUse 훅은 명령 시작 때 브랜치를 봐서 못 막음) → 여러 세션이 동시에 일하면 세션마다 git worktree를 쓴다. 강제: `.githooks` `pre-commit` · `pre-push` · `pre-merge-commit` 보호 브랜치 검사(#140)
- 검증 명령 뒤 `| tail`은 종료 코드를 가린다. 검증은 로그 파일로 남기고 종료 코드를 직접 본다(verify가 그렇게 한다).
- `git stash`는 같은 작업 트리를 쓰는 다른 세션의 변경을 날릴 수 있다. 쓰지 않는다. 지문 계산도 임시 index로 한다.
- 개인 전역 gitignore가 `.claude/`를 무시하면 하네스 파일 변경을 놓친다. `harness_state.py`는 `core.excludesFile=/dev/null`로 저장소 `.gitignore`만 본다.
- Codex를 `codex exec`로 부를 때 stdin을 닫지 않으면 입력을 기다리며 멈춘다. `< /dev/null`을 붙인다.

## Claude와 Codex

- 규칙 원본은 `CLAUDE.md`와 `.claude/docs/` 하나다. `AGENTS.md`에는 규칙을 복사하지 않고 읽을 위치만 적는다.
- Codex에는 Claude 훅이 걸리지 않는다. Codex는 커밋 전에 위 직접 검사와 `.claude/scripts/verify`를 실행한다. git `pre-commit` 게이트는 `core.hooksPath`를 켠 뒤부터 Codex에도 걸린다.
- 교차 검토: 한쪽이 계획하거나 구현하면 다른 쪽이 같은 문서를 기준으로 검토한다.

## 코드 리뷰

리뷰 기준은 루트 `REVIEW.md` 하나다. iOS 저장소와 같은 레인을 쓴다.

| 레인 | 실행 | 비고 |
|------|------|------|
| Codex PR 리뷰 | `.github/workflows/codex-pr-review.yml` (`openai/codex-action`, `OPENAI_API_KEY`) | PR 댓글 하나를 갱신한다 |
| Gemini 리뷰 | `.github/workflows/gemini-code-review.yml` (`GEMINI_API_KEY`, `*.kt` · `*.kts` · 워크플로 diff) | 줄 단위 P1 · P2 댓글 |
| 로컬 교차 검토 | Codex CLI `exec -s read-only` | 계획 · 구현을 다른 쪽이 검토 |

- 두 워크플로는 프롬프트 · diff 대상만 빼고 iOS 저장소와 같게 둔다. 키(`OPENAI_API_KEY` · `GEMINI_API_KEY`)는 iOS와 같은 값을 쓴다.
- 리뷰가 실패하거나 할당량을 넘으면 해당 HEAD의 AI 리뷰를 미완료로 적는다(`.claude/docs/git-workflow.md`).
- 리뷰어 의견은 실제 코드로 확인한 뒤 반영한다. 여러 리뷰어가 같은 말을 했다는 것만으로 맞다고 보지 않는다.

## 하네스 관리

- 규칙을 바꾸면 같은 PR에서 문서와 훅을 함께 고친다.
- 에이전트가 같은 실수를 두 번 하면 문서에 적고, 세 번째부터는 훅으로 만든다.
- 모델이 나아지면 필요 없어진 규칙과 훅을 지운다. 검증과 보호 브랜치 규칙은 남긴다.
