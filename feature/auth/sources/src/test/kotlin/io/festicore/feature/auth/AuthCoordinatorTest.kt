package io.festicore.feature.auth

import io.festicore.feature.web.WebPage
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.orbitmvi.orbit.test.test

class AuthCoordinatorTest {
    private val signUp = AuthRoute.SignUp(userId = "u1", nickname = null, currentTermsVersion = TERMS_V1, sessionId = "s1")

    @Test
    fun `첫 진입 흐름에서 가입이 필요하면 로그인 화면 위에 가입 정보를 쌓고 닫으면 꺼내 로그인 화면으로 돌아온다`() = runTest {
        AuthCoordinator(AuthRoute.LaunchLogin).test(this) {
            containerHost.onSignUpRequired(signUp)
            expectState(AuthCoordinatorState(listOf(AuthRoute.LaunchLogin, signUp)))
            // 이미 가입 정보가 맨 위면 다시 쌓지 않는다.
            containerHost.onSignUpRequired(signUp.copy(userId = "u2"))
            containerHost.onSignUpClosed()
            expectState(AuthCoordinatorState(listOf(AuthRoute.LaunchLogin)))
            // 이미 로그인 화면이면 그대로 둔다(뒤로 두 번).
            containerHost.onSignUpClosed()
        }
    }

    @Test
    fun `시스템 뒤로는 쌓인 화면을 하나씩 꺼내고 첫 화면은 남긴다`() = runTest {
        AuthCoordinator(AuthRoute.LaunchLogin).test(this) {
            containerHost.onSignUpRequired(signUp)
            expectState(AuthCoordinatorState(listOf(AuthRoute.LaunchLogin, signUp)))
            containerHost.goBack()
            expectState(AuthCoordinatorState(listOf(AuthRoute.LaunchLogin)))
            // 첫 화면에서 뒤로는 스택을 비우지 않는다(앱 종료는 시스템이 한다).
            containerHost.goBack()
        }
    }

    @Test
    fun `재확인으로 뜬 가입 정보를 닫으면 로그인 화면으로 가지 않고 가입 취소를 알린다`() = runTest {
        AuthCoordinator(signUp).test(this) {
            containerHost.onSignUpClosed()
            expectSideEffect(AuthCoordinatorSideEffect.Finished(LaunchLoginResult.SignUpCancelled))
        }
    }

    @Test
    fun `결과는 부모에 그대로 알린다`() = runTest {
        AuthCoordinator(AuthRoute.LaunchLogin).test(this) {
            containerHost.onFinished(LaunchLoginResult.Skipped)
            expectSideEffect(AuthCoordinatorSideEffect.Finished(LaunchLoginResult.Skipped))
        }
    }

    @Test
    fun `약관 상세는 가입 정보 위에 한 번만 쌓고 뒤로 가면 가입 정보로 돌아온다`() = runTest {
        val page = WebPage(url = "https://festicore.app/terms", title = "서비스 이용약관")
        AuthCoordinator(AuthRoute.LaunchLogin).test(this) {
            containerHost.onSignUpRequired(signUp)
            expectState(AuthCoordinatorState(listOf(AuthRoute.LaunchLogin, signUp)))
            containerHost.onLegalDocumentRequested(page)
            expectState(AuthCoordinatorState(listOf(AuthRoute.LaunchLogin, signUp, AuthRoute.LegalDocument(page))))
            // 이미 열려 있으면 다른 약관도 다시 쌓지 않는다(iOS 중복 push 막음).
            containerHost.onLegalDocumentRequested(page.copy(title = "개인정보 수집 · 이용"))
            containerHost.goBack()
            expectState(AuthCoordinatorState(listOf(AuthRoute.LaunchLogin, signUp)))
        }
    }
}
