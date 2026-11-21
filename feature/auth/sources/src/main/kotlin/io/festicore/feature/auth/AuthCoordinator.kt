package io.festicore.feature.auth

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

data class AuthCoordinatorState(val routes: List<NavKey>)

sealed interface AuthCoordinatorSideEffect {
    /** 부모(app)에 알린다. iOS `LoginDelegate` · `SignUpDelegate`가 부모로 올라가는 자리. */
    data class Finished(val result: LaunchLoginResult) : AuthCoordinatorSideEffect
}

/**
 * 첫 진입 로그인 흐름(iOS `AuthCoordinator`의 launchLogin ↔ 가입 정보). [start]에서 시작한다.
 * LaunchLogin과 SignUp은 서로를 대체해 스택은 항상 한 화면이다. 그래서 SignUp 뒤로는 화면이 처리하고(세션 폐기),
 * LaunchLogin 뒤로는 앱을 닫는다(iOS 첫 진입 화면에는 닫기 제스처가 없다).
 */
class AuthCoordinator(start: AuthRoute) :
    ViewModel(),
    OrbitContainerHost<AuthCoordinatorState, AuthCoordinatorState, AuthCoordinatorSideEffect> {
    override val container = orbitContainer<AuthCoordinatorState, AuthCoordinatorSideEffect>(AuthCoordinatorState(listOf(start)))

    fun goBack() = intent { reduce { state.copy(routes = state.routes.dropLast(1).ifEmpty { state.routes }) } }

    fun replaceCurrent(route: AuthRoute) = intent { reduce { state.copy(routes = state.routes.dropLast(1) + route) } }

    fun onSignUpRequired(route: AuthRoute.SignUp) = replaceCurrent(route)

    /** 가입 정보에서 뒤로 나왔거나 로그인을 잃었다. */
    fun onReturnToLaunchLogin() = intent {
        if (state.routes.lastOrNull() !is AuthRoute.SignUp) return@intent
        reduce { state.copy(routes = state.routes.dropLast(1) + AuthRoute.LaunchLogin) }
    }

    fun onFinished(result: LaunchLoginResult) = intent { postSideEffect(AuthCoordinatorSideEffect.Finished(result)) }
}
