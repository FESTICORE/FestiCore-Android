---
paths:
  - "feature/**"
---

# feature 경로 규칙

상세 문서: `.claude/docs/android-mvi.md`, `.claude/docs/navigation.md`, `.claude/docs/ios-parity.md`

## Hard

- MVI는 Orbit(`OrbitContainerHost` · `orbitContainer` · `intent` · `reduce` · `postSideEffect`)으로만 쓴다. State 변경은 `intent { reduce { } }` 안에서만 한다.
- 화면은 `collectAsState()` · `collectSideEffect()`로 구독한다.
- feature는 `interface` / `sources`로 나눈다. 다른 feature는 `interface`만 의존하고, `sources`는 `app` · `feature/featureassembly`(Koin 목록)만 의존한다. `sources` 패키지는 iOS `Sources/<화면>` 폴더와 같은 이름(`festivallist` · `notificationinbox` 등)으로 나눈다.
- feature는 `domain/{기능}/interface`만 의존한다. `domain/{기능}/sources` · `service` · `core` 직접 의존 금지. 예외는 분석 계약 `:service:analytics:interface`뿐이다(ViewModel이 `AnalyticsUseCase`를 생성자로 받는다, verify `boundaries`가 검사).
- 화면 전환은 Navigation 3 + 기능별 Coordinator로만 한다. `NavKey` 경로는 `interface`에 둔다. Navigation Compose 2(`NavHost`) 금지.
- Composable에서 UseCase 직접 호출 금지. `!!` 금지.
- 모듈 이름은 iOS `Projects/Feature/{기능}`과 같게 둔다. 옮기기 전에 iOS 대응 파일을 연다.

## Soft (기존 코드가 다르면 기존 코드를 따른다)

- 화면마다 Contract 파일 하나에 State · SideEffect를 둔다.
- ViewModel 함수 이름은 iOS `Action.View` 케이스와 맞추고, 순서는 iOS Action 구분(view · async · inner · delegate · scope)을 따른다.
- 화면은 `{기능}Screen`(ViewModel 연결)과 `{기능}Content`(순수 UI)로 나눈다.
- ViewModel 테스트는 `orbit-test`의 `test(this) { expectState { } }`로 쓴다.
