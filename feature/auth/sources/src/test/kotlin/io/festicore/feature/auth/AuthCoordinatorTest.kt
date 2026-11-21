package io.festicore.feature.auth

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.orbitmvi.orbit.test.test

class AuthCoordinatorTest {
    private val signUp = AuthRoute.SignUp(userId = "u1", nickname = null, currentTermsVersion = TERMS_V1)

    @Test
    fun `첫 진입 흐름에서 가입이 필요하면 로그인 화면을 가입 정보로 바꾸고 닫으면 로그인 화면으로 돌아온다`() = runTest {
        AuthCoordinator(AuthRoute.LaunchLogin).test(this) {
            containerHost.onSignUpRequired(signUp)
            expectState(AuthCoordinatorState(listOf(signUp)))
            containerHost.onSignUpClosed()
            expectState(AuthCoordinatorState(listOf(AuthRoute.LaunchLogin)))
            // 이미 로그인 화면이면 그대로 둔다(뒤로 두 번).
            containerHost.onSignUpClosed()
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
}
