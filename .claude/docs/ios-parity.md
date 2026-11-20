# iOS 화면을 Android로 옮기기

기준 소스는 `FESTICORE/FestiCore-iOS`(로컬 `../FestiCore-iOS`, 읽기 전용)다. 동작 · 문구 · 오류 처리는 iOS를 따르고, 구현 방식은 이 저장소 규칙을 따른다.

## 순서

1. 이슈에 옮길 iOS 경로를 적는다(Reducer · View · Coordinator · Domain · Service).
2. iOS 파일을 열어 아래 대응표대로 Android 파일 목록을 먼저 정한다. 기능 모듈 이름은 iOS와 같게 둔다.
3. 동작 규칙이 iOS 코드만 봐서 분명하지 않으면 소유 모듈 `.spec/{기능}.md`에 먼저 적는다(`.claude/docs/spec-guide.md`).
4. domain `interface` → domain `sources` → feature `interface` → feature `sources` → app 연결 순서로 만든다.
5. ViewModel 테스트에 iOS Reducer 테스트(`*Tests.swift`)의 성공 · 실패 흐름을 같은 이름으로 옮긴다.
6. PR 본문 `Why`에 iOS 대응 경로를 적는다.

## 대응표

| iOS | Android |
|-----|---------|
| `@Reducer struct {화면}Feature` | `{화면}ViewModel` (Orbit `OrbitContainerHost`) |
| `State` | `{화면}Contract.kt`의 `{화면}State` |
| `Action.view(...)` | ViewModel 공개 함수 `on{행동}()` |
| `Action.async(...)` | `private suspend fun {동작}() = subIntent { }` |
| `Action.inner(...)` | `reduce { }` (길면 `private suspend fun on{결과}()`) |
| `Action.delegate(...)` | `postSideEffect(...)` → Screen 콜백 → Coordinator |
| `Action.scope(...)` · 자식 Store | 자식 화면 콜백 · 다이얼로그 State |
| `{화면}View` | `{화면}Screen`(ViewModel 연결) + `{화면}Content`(순수 UI) |
| TCAFlow `@FlowCoordinator {기능}Coordinator` | `{기능}Coordinator` + `NavDisplay` (`.claude/docs/navigation.md`) |
| `@Dependency(\.x)` · `DependencyKey` | 생성자 주입 + 모듈 `di/` Koin 정의 (`.claude/docs/dependency-injection.md`) |
| `UseCase` · `Repository` (Domain Interface / Sources) | `domain/{기능}/interface` · `sources` |
| Alamofire `FestiCoreNetwork` · `APIEndpoint` | Ktor `core/network` · `service/api` |
| Keychain · `@Shared(.persistent)` | `core/datastore` |
| SQLiteData | Room `core/database` |
| `TestStore` 테스트 | `orbit-test` `test(this) { expectState { } }` |

## 옮기지 않는 것

- Apple 로그인(`AppleOAuthRepositoryImpl` · Sign in with Apple 버튼 · 관련 Action). 로그인 화면은 Google 버튼만 둔다. iOS에서 Apple 로그인 분기에 걸린 상태 · 문구는 빼고, 남는 흐름은 iOS와 같게 둔다.
- Live Activity · 위젯 · 알림 서비스 확장. Android 대응이 필요하면 별도 이슈로 결정한다.

## 다르게 두는 것

- 플랫폼 관례가 다른 곳(뒤로 가기 · 권한 요청 · 시스템 바 · 알림 채널)은 Android 관례를 따르고 PR 본문에 차이를 적는다.
- 차이가 동작 규칙이면 `.spec/`에도 적는다.
