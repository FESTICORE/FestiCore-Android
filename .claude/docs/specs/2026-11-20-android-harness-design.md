# FestiCore Android 하네스 선작업 설계

## 목표

FestiCore-iOS를 Android로 옮기기 전에, 에이전트가 일하는 환경(하네스)을 먼저 만든다. CIRQLE-Native-Mobile(`/Users/suhwonji/Desktop/SideProject/CirQle/CIRQLE-Native-Mobile`)이 기능 코드 전에 하네스 → 버전 고정 → Gradle 골격 순서로 쌓은 방식을 따른다.

이 문서의 범위는 하네스(H0~H2)까지다. 화면 이식(스플래시 → 온보딩 → Google 로그인 → 메인 탭)은 H2 머지 후 별도 설계 문서로 진행한다.

## 수용 기준 (관찰 가능한 결과)

1. GitHub `FESTICORE/FestiCore-Android`가 private로 있고 `main` · `develop` 브랜치가 있다.
2. `develop`에 H1 · H2 PR이 squash merge되어 있다. 각 PR은 이슈에서 시작했고 담당자는 `Roy-wonji`다.
3. 훅이 실제 Claude 세션에서 동작한다.
   - `develop`에서 `git commit` → 차단(exit 2)
   - 제목 `하네스 추가`(이슈 번호 없음) → 차단, `하네스 규칙과 훅을 추가한다 #1` → 통과
   - 커밋 메시지에 `Co-Authored-By`(Claude · Codex · Gemini 등 작성자 무관) · 세션 링크 → 차단, `--no-verify` · `-n` · `core.hooksPath` 변경 → 차단
   - verify 통과 후 코드를 고쳐 커밋 → git `pre-commit`이 차단
4. `python3 -m unittest discover -s .claude/hooks/tests` 통과.
5. H2 후 `.claude/scripts/verify --all` 통과(경계 검사 · ktlint · `./gradlew testStageDebugUnitTest assembleStageDebug`).
6. PR CI(`pr-rules` · `android-pr`)가 H2 PR에서 성공한다.

## 결정 사항

| 항목 | 결정 | 근거 |
|------|------|------|
| 저장소 | `FESTICORE/FestiCore-Android` private, 기본 `main`, 작업 기준 `develop` | 사용자 선택 |
| 스택 | Kotlin · Jetpack Compose · Orbit MVI · Navigation 3 + 기능별 Coordinator · Koin · Ktor · kotlinx.serialization · build-logic convention plugin | 사용자 선택(CIRQLE 스택). TCA Action ↔ Orbit intent, TCAFlow ↔ Nav3 Coordinator로 대응 |
| 버전 | CIRQLE `gradle/libs.versions.toml` · wrapper(Gradle 9.9.1, AGP 9.5.1, Kotlin 2.4.21)를 기준으로 시작 | 이미 빌드가 검증된 조합 |
| 모듈 배치 | 루트에 `app/`, `feature/{기능}/{interface,sources}`, `domain/{기능}/{interface,sources}`, `service/*`, `core/*`, `ui/*`. 이름은 iOS 모듈과 맞춘다(`Feature/Auth` ↔ `feature/auth`) | Android 단독 저장소라 `android/` 상위 폴더가 필요 없다. iOS 대응 추적 |
| 환경 | `stage` · `prod` product flavor | iOS `Config/` Stage · Prod |
| Git 규칙 | FestiCore-iOS와 같다: 브랜치 `feature/<영문 kebab>`, 보호 `main` · `develop`, 제목 한국어 + 끝에 ` #이슈`, 타입 접두사 없음 | 같은 조직 저장소끼리 규칙 일치 |
| AI 표시 | 커밋 · PR 본문에 `Co-Authored-By` 트레일러(작성자 이름과 관계없이) · 세션 링크 금지. CIRQLE `AI_MARK_RE`는 `Co-Authored-By: Claude|Codex`만 잡으므로 FestiCore-iOS `CO_AUTHOR_RE`처럼 `Co-Authored-By:` 전체로 넓힌다 | 사용자 전역 규칙 + FestiCore-iOS `check_git_rules.py` `CO_AUTHOR_RE` |
| 로그인 | Google만. Apple 로그인 이식 안 함 | 사용자 요구 |

## 단계

### H0 — 저장소 생성 (PR 없음)

- `gh repo create FESTICORE/FestiCore-Android --private`
- `main`에 Initial commit(`README.md` 최소본 · `.gitignore`) 한 번만 직접 푸시한다. 훅이 아직 없는 유일한 직접 커밋이다.
- `develop`을 `main`에서 만들어 푸시하고 기본 브랜치를 `develop`으로 바꾼다.

### H1 — 하네스 규칙 · 훅 · 문서 (이슈 #1 → `feature/harness` → PR)

CIRQLE 하네스를 가져오고 FestiCore 규칙으로 바꾼다. KMP · iOS · Jira · Slack · Confluence · 파일 헤더(`Created by VisionX Company`)는 가져오지 않는다.

| 파일 | 출처 | 바꿀 점 |
|------|------|---------|
| `CLAUDE.md` (200줄 미만) | CIRQLE · FestiCore-iOS | 색인 · Hard 규칙 · 명령. Android 단독 |
| `AGENTS.md` | CIRQLE | 경로별 규칙 표를 Android 경로로 |
| `REVIEW.md` | CIRQLE | Android 항목만 |
| `.claude/settings.json` | CIRQLE | 같음(PreToolUse Bash · PostToolUse Read/Skill · Stop) |
| `.claude/hooks/check-git-rules.sh` · `check_git_rules.py` | CIRQLE | 메시지 규칙을 FestiCore 형식으로, 보호 브랜치에서 `release` 제거, Jira 키 브랜치 제거, `AI_MARK_RE`의 `Co-Authored-By`를 작성자 무관으로 |
| `.claude/hooks/harness_state.py` | CIRQLE | `CODE_DIRS`를 Android 루트 배치로 |
| `.claude/hooks/stop_guard.py` · `usage_log.py` · `pre_commit_gate.py` | CIRQLE | 경로만 |
| `.claude/hooks/tests/test_check_git_rules.py` | FestiCore-iOS | 새 메시지 · 브랜치 · AI 표시(`Co-Authored-By: Gemini <…>` 등 임의 작성자 포함) · 우회 규칙 케이스 |
| `.githooks/pre-commit` | CIRQLE | 같음 |
| `.claude/scripts/verify` | CIRQLE | 단계: `hooks-test`(훅 단위 테스트) · `boundaries` · `format-kotlin` · `android`. iOS · KMP 단계 제거. Gradle이 아직 없으면 `format-kotlin` · `android`는 건너뛰고 그 사실을 출력 |
| `.claude/scripts/check-boundaries` | CIRQLE | feature → 다른 feature `sources` · domain `sources` 의존 금지, 저장소 API는 `core/datastore` · `core/database`만 |
| `.claude/scripts/harness-report` | CIRQLE | 같음 |
| `.claude/rules/{app,feature,domain,service-core,ui}.md` | FestiCore-iOS 이름 + CIRQLE `android.md` 내용 | `paths` frontmatter를 Android 경로로 |
| `.claude/docs/` | 아래 표 | — |
| `.github/pull_request_template.md` · `ISSUE_TEMPLATE/{feature,bug,task}.md` | CIRQLE | FestiCore 형식 |
| `.github/workflows/pr-rules.yml` | CIRQLE | PR 제목 · 커밋 제목 규칙을 FestiCore 형식으로, 커밋 · PR 본문의 `Co-Authored-By`(작성자 무관) · 세션 링크 검사 |
| `.github/workflows/assign-pr-owner.yml` | FestiCore-iOS | 같음 |
| `.editorconfig` · `.gitignore` · `.mise.toml` | CIRQLE | `.mise.toml`은 `java = "temurin-21"` · `ktlint`만 |

H1 문서(각 200줄 미만, 코드에서 알 수 있는 내용은 쓰지 않는다):

| 문서 | 내용 |
|------|------|
| `harness.md` | 구성 · 훅 · verify · 재시도 한도 · 관측(CIRQLE 기준) |
| `git-workflow.md` | 이슈 · 브랜치 · 커밋 · PR · squash merge(FestiCore-iOS 기준, 저장소 이름만 교체) |
| `architecture.md` | 모듈 배치 · 의존 방향 · iOS 모듈 대응표 |
| `ios-parity.md` | iOS 화면 하나를 옮기는 절차: iOS Reducer · View · Coordinator 경로 → Orbit Contract · ViewModel · Screen · Coordinator 대응, Apple 로그인 제외 규칙 |
| `android-mvi.md` · `navigation.md` · `dependency-injection.md` · `gradle.md` · `code-convention.md` · `spec-guide.md` | CIRQLE에서 KMP · iOS 부분을 빼고 가져온다 |

네트워크 · 저장소 · 디자인 토큰 · 분석 문서는 해당 모듈을 만드는 PR에서 쓴다.

### H2 — 버전 고정 · Gradle 골격 · CI (이슈 #3 → `feature/gradle-skeleton` → PR)

- Gradle wrapper, `gradle/libs.versions.toml`(CIRQLE 기준에서 KMP · iOS 전용 항목 제거), `gradle/format`(ktlint)
- `settings.gradle.kts`: CIRQLE처럼 `build.gradle.kts`가 있는 폴더를 자동 등록(`app` · `feature` · `domain` · `service` · `core` · `ui`)
- `build-logic`: `festicore.android.application` · `festicore.android.library` · `festicore.android.compose` · `festicore.android.feature`
- `app`: 빈 `MainActivity`(Compose 텍스트 한 줄) · `stage`/`prod` flavor · `applicationId` `io.festiCore.co`(iOS 번들 ID와 같게, flavor별 suffix는 stage만 `.stage`) · ViewModel 없는 최소 단위 테스트 1개
- `core/logger`: 첫 라이브러리 모듈(경계 · convention plugin 검증용)
- verify의 `android` 단계를 `./gradlew testStageDebugUnitTest assembleStageDebug`로 켠다
- `.github/workflows/android-pr.yml`: PR에서 ktlint · 단위 테스트 · `assembleStageDebug`

## 비목표

- 화면 · 도메인 · 네트워크 이식(다음 설계 문서)
- 브랜치 보호 규칙(private 저장소 무료 플랜에서 불가할 수 있음. 훅으로 대신한다)
- AI PR 리뷰 워크플로(Codex · Gemini · Claude Code Review). 비밀 값이 필요해 별도 이슈로 둔다
- 서명 · 배포(fastlane · Play Console)
- Live Activity · 위젯 대응

## 가정과 출처

| 가정 | 출처 |
|------|------|
| CIRQLE 하네스 파일은 Android 부분만 떼어도 독립 동작한다 | CIRQLE `.claude/docs/harness.md`, `.claude/hooks/*` |
| FestiCore 커밋 규칙: 한국어 제목 + ` #이슈`, 타입 접두사 없음, `Co-Authored-By` 금지 | FestiCore-iOS `.claude/hooks/check_git_rules.py:23-59`, `.claude/docs/git-workflow.md` |
| 로그인 제공자는 Google · Apple 두 개 | FestiCore-iOS `Projects/Domain/AuthDomain/Sources/Repository/{Google,Apple}OAuthRepositoryImpl.swift` |
| 로컬 도구: JDK 17, Android SDK, Gradle, mise, `gh`(Roy-wonji, FESTICORE 쓰기 권한), codex-cli 0.157.1 | 이 세션에서 확인 |
| mise로 temurin-21을 설치할 수 있다 | 미확인. 실패하면 로컬 JDK 17로 빌드하고 CI는 21을 쓴다(AGP 9는 JDK 17 이상) |

## 남은 위험

- AGP 9.5.1 · Gradle 9.9.1이 로컬 JDK 17에서 동작하지 않을 수 있다 → H2에서 확인, 안 되면 mise temurin-21.
- private 저장소에서 GitHub Actions 분 사용량이 든다.
- 훅은 단위 테스트만 통과하고 실제 세션에서 형식 오류로 안 걸릴 수 있다 → 수용 기준 3을 실제 세션에서 확인한다.

## 검증 명령

```bash
python3 -m unittest discover -s .claude/hooks/tests                      # H1
bash .claude/hooks/check-git-rules.sh --check-message "하네스 규칙과 훅을 추가한다 #1"   # exit 0
bash .claude/hooks/check-git-rules.sh --check-message "하네스 추가"                        # exit 2
bash .claude/hooks/check-git-rules.sh --check-branch develop                               # exit 2
.claude/scripts/verify                                                    # H1: hooks-test · boundaries
.claude/scripts/verify --all                                              # H2: + format-kotlin · android
./gradlew testStageDebugUnitTest assembleStageDebug                       # H2
gh pr checks <PR>                                                         # H2 CI
```
