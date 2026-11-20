# FestiCore Android

페스티벌 앱 FestiCore의 Android 저장소. 앱 ID `io.festiCore.co`(stage는 `.stage` 접미사). iOS 앱(`FESTICORE/FestiCore-iOS`)과 같은 기능을 Android로 옮긴다. Apple 로그인은 옮기지 않는다.

이 파일은 색인이다. 세부 규칙은 아래 문서에 있고, 작업에 해당하는 문서만 읽는다. 문서의 예시보다 실제 Gradle 설정과 소스가 우선한다.

## 스택

| 영역 | 기술 | 빌드 |
| --- | --- | --- |
| 화면 | Kotlin · Jetpack Compose · Orbit MVI · Navigation 3 + 기능별 Coordinator | Gradle + `build-logic` convention plugin |
| 주입 · 통신 | Koin · Ktor · kotlinx.serialization | `gradle/libs.versions.toml` |
| 관측 | Sentry(오류) · Mixpanel(행동) | `service/analytics`에서만 |

환경은 `stage` · `prod` product flavor 두 개다.

## 저장소 구조

```
app/        진입점 (Application · MainActivity · 루트 Coordinator · Koin 목록 합치기)
feature/    화면 {기능}/{interface,sources}. 이름은 iOS Projects/Feature와 같다
domain/     기능별 계약(interface)과 구현(sources)
service/    API · 인증 세션 · 분석 등 서비스
core/       network · datastore · database · logger 등 공통
ui/         designkit(토큰 · 컴포넌트) · sharedui
build-logic/  Gradle convention plugin
.claude/    에이전트 규칙 · 문서 · 훅
```

자세한 구조와 의존 방향은 `.claude/docs/architecture.md`에 있다.

## 문서 색인

| 작업 | 먼저 읽을 문서 |
| --- | --- |
| 이슈 · 브랜치 · 커밋 · PR · 스쿼시 머지 | `.claude/docs/git-workflow.md` |
| 모듈 추가 · 의존 방향 · iOS 모듈 대응 | `.claude/docs/architecture.md` |
| iOS 화면을 Android로 옮기기 | `.claude/docs/ios-parity.md` |
| 화면 · ViewModel (Orbit) | `.claude/docs/android-mvi.md` |
| 화면 전환 (Navigation 3 · Coordinator) | `.claude/docs/navigation.md` |
| 의존성 등록 (Koin) | `.claude/docs/dependency-injection.md` |
| Gradle · 버전 · convention plugin · 명령 | `.claude/docs/gradle.md` |
| 포맷 · 이름 · 주석 (ktlint) | `.claude/docs/code-convention.md` |
| 디자인 토큰 · 폰트 · 아이콘 · 테마 · 동기화 | `.claude/docs/design-system.md` |
| 기능 spec · 설계 문서 | `.claude/docs/spec-guide.md` → 모듈 `.spec/` · `.claude/docs/specs/` |
| 코드 리뷰 (심각도 · 제외 · 항상 확인) | `REVIEW.md` |
| 에이전트 · 훅 · 검증 게이트 | `.claude/docs/harness.md` |

네트워크 · 저장소 · 분석 문서는 해당 모듈을 만드는 PR에서 추가한다.

경로별 규칙은 `.claude/rules/`에 있다(`app` · `feature` · `domain` · `service-core` · `ui`). 해당 경로의 파일을 다룰 때 자동으로 적용된다.

## 반드시 지킬 규칙 (Hard)

1. 모든 작업은 이슈에서 시작한다. 브랜치는 `feature/<작업명>`(영문 소문자 kebab-case, 이슈 번호 없음), PR 대상은 `develop`이다.
2. `develop` · `main`에 직접 커밋하거나 푸시하지 않는다. 훅이 막는다.
3. 커밋 제목과 본문은 한국어로 쓰고 제목 끝에 해당 작업 이슈 번호를 `#27`처럼 붙인다. 타입 접두사(`feat:`)는 쓰지 않는다. 커밋 · PR 본문에 `Co-Authored-By` 트레일러(작성자 무관)와 세션 링크를 넣지 않는다. 훅과 CI가 검사한다.
4. PR을 만들 때 작성자 `Roy-wonji`를 담당자로 지정하고 생성 후 할당을 확인한다.
5. squash merge만 쓰고, 커밋은 PR 제목 + ` (#PR번호)` 한 줄, 본문은 비운다(저장소 설정 `PR_TITLE` · `BLANK`). 머지 후 원격 · 로컬 브랜치를 지운다.
6. 코드를 바꿨으면 `.claude/scripts/verify`를 통과한 뒤 커밋한다. 통과 기록 없이 커밋하면 훅이 막는다.
7. 검증하지 않은 결과를 완료로 보고하지 않는다. 단위 테스트 · CI · 에뮬레이터 · 실기기 검증을 구별하고 실제로 확인한 범위만 보고한다.
8. 자격 증명과 로컬 설정(`google-services.json` · 서명 키 · `local.properties`)을 추적 파일에 넣지 않는다. 토큰 · 인가 코드 · 개인정보는 로그 · 분석 이벤트 · 테스트 실패 메시지에 넣지 않는다.
9. 기능 모듈 이름은 iOS와 같게 둔다(`Projects/Feature/Auth` ↔ `feature/auth`). 화면을 옮기기 전에 iOS의 같은 Reducer · View · Coordinator를 열어 대조한다(`.claude/docs/ios-parity.md`).
10. 디자인 토큰 원천은 `FestiCore-DesignSystem` 저장소다. Android 모듈에 토큰 값을 손으로 복제하거나 고치지 않는다.
11. 같은 단계가 2번 연속 실패하면 멈추고 시도한 내용과 실패 원인을 사용자에게 보고한다. 승인 없이 세 번째 시도를 하지 않는다.
12. 운영 배포 · 라이선스 승인만 사람의 승인을 기다린다.

## 명령

```bash
git config core.hooksPath .githooks                  # clone 직후 한 번: pre-commit 검증 게이트 켜기
./gradlew testStageDebugUnitTest assembleStageDebug   # 단위 테스트 · stage 디버그 빌드
gradle/format                                         # Kotlin 포맷(ktlint)
.claude/scripts/verify                                # 바뀐 경로에 맞는 커밋 전 검증
.claude/scripts/verify --all                          # 모든 단계
```

도구 버전은 `.mise.toml`(`mise install`)로 맞춘다.

## 문서 갱신

규칙을 바꾸면 같은 PR에서 해당 문서와 훅을 고친다. `AGENTS.md`에는 규칙을 다시 쓰지 않는다. 이 파일은 200줄 아래로 유지한다.
