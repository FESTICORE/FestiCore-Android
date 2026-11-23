# 화면 전환 (Navigation 3 · 기능별 Coordinator)

iOS TCAFlow와 같이 **기능마다 Coordinator 하나가 화면 스택(routes)을 State로 갖고**, 자식 화면은 "무슨 일이 일어났는지"만 알린다.
어디로 이동할지는 Coordinator가 정한다. 화면끼리 서로를 직접 열지 않는다.

| 개념 | iOS (TCAFlow) | Android (Navigation 3 + Orbit) |
|------|---------------|--------------------------------|
| 기능 흐름 담당 | `@FlowCoordinator` `{기능}Coordinator` | `{기능}Coordinator` (Orbit `ContainerHost`) |
| 화면 스택 | `State.routes: [Route<{기능}Screen.State>]` | `CoordinatorState.routes: List<NavKey>` |
| 화면 목록 | `@Reducer enum {기능}Screen` | `@Serializable sealed interface {기능}Route : NavKey` |
| 이동 | `routes.push` · `presentSheet` · `presentCover` · `goBack` · `goBackToRoot` | Coordinator `push` · `goBack` · `goBackToRoot` (+ 필요할 때만 Android `replaceCurrent`) |
| 자식 → 부모 | `delegate` Action | 화면 SideEffect → Coordinator 함수 호출 |
| 그리기 | TCAFlow 뷰 | `NavDisplay(backStack = state.routes, ...)` |
| 다른 기능으로 | 부모 Coordinator로 `delegate` | 부모 Coordinator로 콜백 |


## Android: Navigation 3 + Orbit Coordinator

참고: Android 공식 문서 Navigation 3 (`developer.android.com/guide/navigation/navigation-3`).
Navigation 3는 백스택을 앱이 소유하는 리스트로 다룬다. TCAFlow의 `routes`와 같은 개념이다.
버전은 도입 PR에서 최신 안정판을 확인해 `libs.versions.toml`에 고정한다.

### 경로 (feature `interface`)

```kotlin
// feature/auth/interface/.../AuthRoute.kt
@Serializable
sealed interface AuthRoute : NavKey {
    @Serializable data object Login : AuthRoute
    @Serializable data class SignUp(val inviteCode: String? = null) : AuthRoute
}
```

- 경로는 `interface` 모듈에 둔다. 다른 기능이 이 기능 화면으로 들어올 때 이 타입만 쓴다.
- 경로에 넘기는 값은 id 같은 작은 값만 둔다. 큰 객체는 UseCase로 다시 조회한다.

### Coordinator (feature `sources`)

```kotlin
// feature/auth/sources/.../AuthCoordinator.kt
data class AuthCoordinatorState(val routes: List<NavKey> = listOf(AuthRoute.Login))

sealed interface AuthCoordinatorSideEffect {
    data object Finished : AuthCoordinatorSideEffect   // 부모 Coordinator에 알림 (iOS delegate)
}

class AuthCoordinator : OrbitContainerHost<AuthCoordinatorState, AuthCoordinatorState, AuthCoordinatorSideEffect>, ViewModel() {
    override val container = orbitContainer<AuthCoordinatorState, AuthCoordinatorSideEffect>(AuthCoordinatorState())

    fun push(route: AuthRoute) = intent { reduce { state.copy(routes = state.routes + route) } }
    fun goBack() = intent { reduce { state.copy(routes = state.routes.dropLast(1).ifEmpty { state.routes }) } }
    fun goBackToRoot() = intent { reduce { state.copy(routes = state.routes.take(1)) } }
    fun replaceCurrent(route: AuthRoute) = intent { reduce { state.copy(routes = state.routes.dropLast(1) + route) } }

    // 자식 화면 이벤트 → 이동 결정 (iOS handleRouterAction과 같은 자리)
    fun onSignUpRequested() = push(AuthRoute.SignUp())
    fun onLoggedIn() = intent { postSideEffect(AuthCoordinatorSideEffect.Finished) }
}
```

### 그리기

```kotlin
@Composable
fun AuthFlow(coordinator: AuthCoordinator = koinViewModel(), onFinished: () -> Unit) {
    val state by coordinator.collectAsState()
    coordinator.collectSideEffect { if (it == AuthCoordinatorSideEffect.Finished) onFinished() }

    NavDisplay(
        backStack = state.routes,
        onBack = coordinator::goBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),   // 화면마다 ViewModel 범위를 나눈다
        ),
        entryProvider = entryProvider {
            entry<AuthRoute.Login> {
                LoginScreen(
                    onSignUpRequested = coordinator::onSignUpRequested,
                    onLoggedIn = coordinator::onLoggedIn,
                )
            }
            entry<AuthRoute.SignUp> { key -> SignUpScreen(inviteCode = key.inviteCode, onDone = coordinator::goBack) }
        },
    )
}
```

- 화면(`LoginScreen`)은 자기 ViewModel의 SideEffect를 받아 콜백(`onSignUpRequested`)만 부른다. 백스택을 직접 바꾸지 않는다.
- `rememberViewModelStoreNavEntryDecorator()`를 반드시 넣는다. 없으면 화면 ViewModel이 Activity 범위로 공유된다.
- Coordinator 이동 함수 이름은 iOS TCAFlow와 같게 둔다(`push` · `goBack` · `goBackToRoot`). `replaceCurrent`는 iOS에 없어 Android만 쓴다(iOS는 `goBack()` + `push`).

## 앱 단위 흐름

- iOS `AppCoordinator`처럼 `app`의 루트 Coordinator가 기능 흐름(스플래시 → 온보딩 → `AuthFlow` → 메인 탭)을 전환한다.
- 기능 밖으로 나가는 이동은 항상 부모 Coordinator가 정한다.
- 메인 탭 선택은 `app` `MainTabCoordinator`(iOS 같은 이름, `selectedTab`)가 가진다. `MainTabScreen`은 선택을 인자로 받는 껍데기다.
- 탭 흐름 Coordinator(깃발 `FlagCoordinator` · 마이 `ProfileCoordinator`)는 탭 밖(`AppFlow` 메인 탭 범위)에서 소유해 탭을 바꿔도 스택을 유지한다. 탭 바 숨김은 흐름이 콜백으로 알린다(라이브 `onHidesTabBarChanged(Boolean)`, 마이 `(hides, reservesSpace)` — 팝업으로만 숨기면 보이지 않는 탭 바로 자리를 남긴다).
- 한 탭 흐름이 다른 탭 화면을 열 때는 `MainTabCoordinator`가 탭을 고르고 요청 값(`liveRequest` · `flagRequest` · `packDirectionsRequest`)을 두며, 그 흐름이 받아 처리한 뒤 비운다(예: 라이브 보드 "가는 길" → 마이 팩 상세).

## 딥링크

iOS 4단 그대로 둔다(규칙: `core/utility/.spec/deeplink.md`).

1. 파싱: `core/utility` `FestiCoreDeeplink` · `FestiCoreDeeplinkParser`(스킴 `festicore`).
2. 대기: `core/storage` `PendingDeeplinkStore`(최신 하나, 꺼내면 비움).
3. 브리지: `app` `AppDeeplinkBridge`. `MainActivity`(`launchMode="singleTop"`)의 `onCreate` · `onNewIntent` `ACTION_VIEW` 인텐트를 받아 대기에 넣고 신호를 보낸다.
4. 라우팅: `AppCoordinator`가 홈이고 덮개가 없을 때만 꺼내 `OpenDeeplink` SideEffect로 보낸다. 홈 진입 · 덮개 닫힘 때 다시 꺼낸다. `MainTabCoordinator.open`이 탭을 고르고 라이브 경로는 `FestivalCoordinator.onDeeplinkFestival(List)Opened` · `onCrewInviteOpened`로, 깃발 경로는 `FlagCoordinator.openDetail`로 넘긴다.

## 화면 전환 애니메이션

iOS 전환 값을 그대로 옮긴다. 값은 상수로 두고 단위 테스트한다(`app/.../navigation/LaunchTransitions.kt`, `feature/auth/sources/.../AuthTransitions.kt`, `feature/profile/sources/.../ProfileTransitions.kt`). 탭 안 스택(라이브 · 깃발 · 마이)은 iOS 시스템 push라 같은 값(350ms `FastOutSlowInEasing`, 오른쪽에서 들어오고 아래 화면 1/3 이동)을 쓴다.

- 곡선: SwiftUI `easeInOut` → Compose `EaseInOut`(`CubicBezierEasing(0.42, 0, 0.58, 1)`), `spring(response:dampingFraction:)` → `spring(dampingRatio = dampingFraction, stiffness = (2π / response)²)`(질량 1 근사, `blendDuration`은 대응 없음). `move(edge:)` → `slideIn/OutHorizontally { ±it }`, `opacity` → `fadeIn/fadeOut`.
- SwiftUI는 들어오는 화면의 등장 전환과 나가는 화면의 퇴장 전환을 따로 쓴다. 루트 `transitionSpec`도 엔트리 `metadata`로 두 화면을 알아 각각 고른다.
- Navigation 3(1.2.1 `NavDisplay`)는 새 스택이 이전 스택의 앞부분이고 더 짧을 때만 pop으로 본다. 맨 앞이 바뀌거나 같은 길이로 바꿔 끼우면 `transitionSpec`을 쓰고 새 화면을 위에(zIndex +1) 그린다. pop은 `popTransitionSpec`, 제스처 중에는 `predictivePopTransitionSpec`을 쓰고 나가는 화면을 위에 그린다.
- 화면 위에 쌓는 화면은 `replaceCurrent` 대신 `push` · `goBack`으로 두어 아래 화면 상태를 유지하고 시스템 뒤로 · predictive back이 pop 전환을 따르게 한다. 화면이 자기 `BackHandler`를 두면 `NavDisplay`의 predictive back이 가려지므로, 스택 첫 화면일 때만 켠다.

## 테스트

- `orbit-test`로 Coordinator 함수 호출 → `routes` State와 `Finished` SideEffect를 검증한다.
- 딥링크 수동 확인: `adb shell am start -W -a android.intent.action.VIEW -d "festicore://festival/<축제 ID>" io.festiCore.co.stage`(앱 꺼짐 · 켜짐 각각).

## 금지

- 화면(Feature · Screen)에서 다른 화면을 직접 열기 금지. Coordinator를 거친다.
- Navigation Compose 2(`NavHost` · `NavController`)와 Navigation 3 섞어 쓰기 금지.
- 경로(`NavKey`)를 `sources`에 두기 금지. 다른 기능이 볼 수 있게 `interface`에 둔다.
