# Android 규칙 (Jetpack Compose · Orbit MVI · Gradle)


## 기본

- 화면은 Jetpack Compose, 상태 관리는 **Orbit MVI**, 의존성 주입은 Koin을 쓴다.
- MVI를 직접 구현하지 않는다(`MutableStateFlow` + `Channel` 조합 금지). 항상 Orbit Container를 쓴다.
- 모듈 공통 설정은 `build-logic/`의 convention plugin에만 쓴다. 모듈 `build.gradle.kts`에 같은 설정을 복사하지 않는다.
- 라이브러리 버전은 `gradle/libs.versions.toml`에서만 관리한다.

참고 문서: Orbit 공식 저장소 `orbit-mvi/orbit-mvi`의 README, `website/docs/Compose`, `website/docs/Test`.
아래 예시는 main 브랜치 문서의 API(`OrbitContainerHost`, `orbitContainer`) 기준이다.
버전은 도입 PR에서 최신 안정판을 확인해 `libs.versions.toml`에 고정하고, API 이름이 다르면 이 문서를 같이 고친다.

### Orbit 의존성

| 아티팩트 | 쓰는 모듈 | 용도 |
|----------|-----------|------|
| `orbit-core` | feature `sources` | Container, `intent`, `reduce`, `postSideEffect` |
| `orbit-viewmodel` | feature `sources` | `ViewModel`용 `orbitContainer` 팩토리 |
| `orbit-compose` | feature `sources` | `collectAsState()`, `collectSideEffect()` |
| `orbit-test` | feature `sources` 테스트 | `test(this) { expectState { } }` |

convention plugin `festicore.android.feature`가 위 의존성을 한 번에 추가한다.

## Feature 모듈 구조 (interface / sources)

iOS `Projects/Feature/{기능}`의 Interface / Sources와 같은 방식으로 나눈다.

```
feature/auth/
├── interface/     다른 모듈에 공개하는 계약
│   └── src/main/  AuthRoute(Navigation 3 `NavKey`) · 진입 인자 · 결과 이벤트
└── sources/
    ├── src/main/  Coordinator · Screen(Compose) · ViewModel · Contract(State · SideEffect) · di/
    └── src/test/  ViewModel 테스트 (orbit-test)
```

- `interface`에는 Compose UI, ViewModel, Koin을 두지 않는다. 경로와 데이터 타입만 둔다.
- 다른 feature가 이 화면으로 이동해야 하면 `:feature:auth:interface`만 의존한다.
- `sources`는 `:app`만 의존한다. 화면 전환은 기능별 Coordinator + Navigation 3로 한다. (`.claude/docs/navigation.md`)
- iOS `Feature/Auth`와 같은 이름을 쓴다.

## MVI 계약 (Contract)

한 화면마다 Contract 파일 하나에 State와 SideEffect를 둔다.

```kotlin
// sources/src/main/.../login/LoginContract.kt
data class LoginState(
    val id: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface LoginSideEffect {
    data object NavigateToHome : LoginSideEffect
    data class ShowToast(val message: String) : LoginSideEffect
}
```

- State는 불변 `data class`로 두고, 화면에 그릴 데이터는 State에만 둔다.
- SideEffect는 한 번만 처리할 일(화면 이동, 토스트)에 쓴다. iOS의 `Action.Delegate`에 해당한다.
- Intent는 별도 sealed class로 만들지 않는다. Orbit에서는 ViewModel의 공개 함수 하나가 Intent 하나다.
  함수 이름은 사용자 행동으로 짓고 iOS `Action.View` 케이스와 맞춘다. (예: `onLoginClicked()` ↔ `.view(.loginButtonTapped)`)

## ViewModel

### iOS Action과 맞추기

ViewModel 함수를 iOS Reducer의 Action 구분(FestiCore-iOS: `view` · `async` · `inner` · `delegate` · `scope`)과 같은 순서 · 역할로 둔다. 같은 기능이면 두 파일을 나란히 놓고 읽을 수 있어야 한다.

| iOS Action | Android ViewModel | 예 |
|------------|-------------------|----|
| `BindingAction` (있을 때) | 입력 값 함수 `on{필드}Changed(value)` | `onIdChanged` ↔ `$store.id` |
| `view` | 공개 함수 `on{행동}()` = `intent { }` | `onLoginClicked` ↔ `.loginButtonTapped` |
| `async` | `private suspend fun {동작}() = subIntent { }` (UseCase 호출) | `login()` ↔ `.async(.login)` |
| `inner` | 결과를 반영하는 `reduce { }` (길면 `private suspend fun on{결과}()`) | `onLoginResponse` ↔ `.inner(.loginResponse)` |
| `delegate` | `postSideEffect(...)` → Screen 콜백 → Coordinator | `LoggedIn` ↔ `.delegate(.loggedIn)` |
| `scope` | 자식 화면 콜백 · 다이얼로그 State | |

함수 순서도 위 표 순서(입력 → 사용자 행동 → 비동기 → 결과)로 둔다.

```kotlin
// sources/src/main/.../login/LoginViewModel.kt
class LoginViewModel(
    private val loginUseCase: LoginUseCase,   // domain:auth:interface
) : OrbitContainerHost<LoginState, LoginState, LoginSideEffect>, ViewModel() {

    override val container = orbitContainer<LoginState, LoginSideEffect>(LoginState())

    fun onIdChanged(value: String) = intent {
        reduce { state.copy(id = value) }
    }

    fun onLoginClicked() = intent {
        reduce { state.copy(isLoading = true, errorMessage = null) }
        login(state.id)
    }

    private suspend fun login(id: String) = subIntent {
        onLoginResponse(loginUseCase(id))
    }

    private suspend fun onLoginResponse(result: Result<Unit>) = subIntent {
        reduce { state.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message) }
        if (result.isSuccess) postSideEffect(LoginSideEffect.NavigateToHome)
    }
}
```

- State 변경은 `intent { reduce { state.copy(...) } }` 안에서만 한다.
- 일회성 이벤트는 `postSideEffect`로만 보낸다.
- 비동기 호출은 `intent` 블록 안에서 바로 `suspend` 함수를 부른다. 별도 `viewModelScope.launch`를 쓰지 않는다.
- UseCase는 `domain:{기능}:interface`에서 가져와 생성자로 주입한다.
- ViewModel은 그 feature `sources`의 `di/{기능}FeatureModule.kt`에서 직접 등록한다. (`.claude/docs/dependency-injection.md`)

## Screen (Compose)

Orbit 문서 기준: `collectAsState()`와 `collectSideEffect()`는 Composable 생명주기를 따르며,
화면이 최소 `STARTED`일 때만 구독한다.

```kotlin
// sources/src/main/.../login/LoginScreen.kt
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = koinViewModel(),
    onNavigateToHome: () -> Unit,
) {
    val state by viewModel.collectAsState()

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            LoginSideEffect.NavigateToHome -> onNavigateToHome()
            is LoginSideEffect.ShowToast -> { /* 토스트 표시 */ }
        }
    }

    LoginContent(
        state = state,
        onIdChanged = viewModel::onIdChanged,
        onLoginClicked = viewModel::onLoginClicked,
    )
}
```

- 화면은 `{기능}Screen`(ViewModel 연결)과 `{기능}Content`(State만 받는 순수 UI)로 나눈다. Preview와 Demo는 `Content`를 쓴다.
- `collectSideEffect`로만 SideEffect를 받는다. `LaunchedEffect`에서 `sideEffectFlow`를 직접 수집하지 않는다.

## 테스트 (orbit-test)

```kotlin
// sources/src/test/.../login/LoginViewModelTest.kt
@Test
fun `로그인 성공 시 홈으로 이동한다`() = runTest {
    LoginViewModel(FakeLoginUseCase(Result.success(user))).test(this) {
        containerHost.onLoginClicked()

        expectState { copy(isLoading = true) }
        expectState { copy(isLoading = false) }
        expectSideEffect(LoginSideEffect.NavigateToHome)
    }
}
```

- `test(this) { }` 블록 안에서 `containerHost`의 함수를 부르고 `expectState` · `expectSideEffect`로 검증한다.
- Orbit 문서 기준: 테스트가 끝날 때 확인하지 않은 State나 SideEffect가 남아 있으면 실패한다. 모든 변화를 검증한다.
- UseCase는 가짜 구현으로 바꾼다. 실제 네트워크를 호출하지 않는다.
- 새 ViewModel에는 성공 흐름과 실패 흐름 테스트를 함께 쓴다.

## 지원 범위

- `minSdk 24`, 성능 · QA 기준 기기는 Galaxy S21급으로 한다.
- 출시 후 Play Console에서 Android 11 미만 비중이 2~3% 아래로 내려가면 `minSdk 30`으로 올린다.

## 금지

- `MutableStateFlow` · `Channel`로 MVI 직접 구현 금지. Orbit Container를 쓴다.
- `intent { }` 밖에서 State 변경 금지.
- Composable 안에서 UseCase 호출 금지. ViewModel을 거친다.
- `!!` 금지.
- feature `interface`에서 Compose · ViewModel · Koin 의존 금지.
- feature에서 다른 feature의 `sources`, `domain:{기능}:sources`, `core/*` 직접 의존 금지.
