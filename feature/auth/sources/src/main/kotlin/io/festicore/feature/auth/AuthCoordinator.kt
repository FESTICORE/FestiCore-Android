package io.festicore.feature.auth

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import io.festicore.feature.web.WebPage
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

data class AuthCoordinatorState(val routes: List<NavKey>)

sealed interface AuthCoordinatorSideEffect {
    /** 부모(app)에 알린다. iOS `LoginDelegate` · `SignUpDelegate`가 부모로 올라가는 자리. */
    data class Finished(val result: LaunchLoginResult) : AuthCoordinatorSideEffect
}

/**
 * 첫 진입 로그인 흐름(iOS `AuthCoordinator`의 launchLogin ↔ 가입 정보). [start]에서 시작한다.
 * LaunchLogin과 SignUp은 서로를 대체한다. 약관 상세(LegalDocument)만 가입 정보 위에 쌓인다. LaunchLogin 뒤로는 앱을 닫는다(iOS 첫 진입 화면에는 닫기 제스처가 없다).
 * [start]가 LaunchLogin이면 첫 진입 흐름(iOS `isLaunchFlow`), SignUp이면 로그인 상태 재확인으로 뜬 가입 덮개다.
 */
class AuthCoordinator(private val start: AuthRoute) :
    ViewModel(),
    OrbitContainerHost<AuthCoordinatorState, AuthCoordinatorState, AuthCoordinatorSideEffect> {
    override val container = orbitContainer<AuthCoordinatorState, AuthCoordinatorSideEffect>(AuthCoordinatorState(listOf(start)))

    fun goBack() = intent { reduce { state.copy(routes = state.routes.dropLast(1).ifEmpty { state.routes }) } }

    fun replaceCurrent(route: AuthRoute) = intent { reduce { state.copy(routes = state.routes.dropLast(1) + route) } }

    fun onSignUpRequired(route: AuthRoute.SignUp) = replaceCurrent(route)

    /** 가입 정보의 약관 '보기'(iOS `legalDocumentRequested`). 이미 열려 있으면 다시 쌓지 않는다. 닫기는 [goBack]. */
    fun onLegalDocumentRequested(page: WebPage) = intent {
        if (state.routes.any { it is AuthRoute.LegalDocument }) return@intent
        reduce { state.copy(routes = state.routes + AuthRoute.LegalDocument(page)) }
    }

    /**
     * 가입 정보에서 뒤로 나왔거나 가입 중 로그인을 잃었다(iOS `dismissFlow`). 세션은 지우지 않는다.
     * 첫 진입 흐름이면 로그인 화면으로 돌아가고, 재확인으로 뜬 가입이면 [LaunchLoginResult.SignUpCancelled]로 덮개를 닫는다.
     */
    fun onSignUpClosed() = intent {
        if (state.routes.lastOrNull() !is AuthRoute.SignUp) return@intent
        if (start is AuthRoute.LaunchLogin) {
            reduce { state.copy(routes = state.routes.dropLast(1) + AuthRoute.LaunchLogin) }
        } else {
            postSideEffect(AuthCoordinatorSideEffect.Finished(LaunchLoginResult.SignUpCancelled))
        }
    }

    fun onFinished(result: LaunchLoginResult) = intent { postSideEffect(AuthCoordinatorSideEffect.Finished(result)) }
}
