package io.festicore.feature.auth

import io.festicore.domain.auth.AuthDomainError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

class LaunchLoginViewModelTest {
    private fun viewModel(
        login: suspend () -> io.festicore.domain.auth.AuthLoginResult = { loginResult() },
        repository: FakeAuthRepository = FakeAuthRepository(),
    ) = LaunchLoginViewModel(FakeGoogleLoginUseCase(login), repository)

    private val signingIn = LaunchLoginState(isSigningIn = true, attemptId = 1)

    @Test
    fun `로그인과 계정 확인이 끝나고 가입을 마친 계정이면 덮개를 닫는다`() = runTest {
        viewModel().test(this) {
            containerHost.onSignInTapped()
            expectState(signingIn)
            expectSideEffect(LaunchLoginSideEffect.Finished(LaunchLoginResult.Completed))
        }
    }

    @Test
    fun `가입 조건을 채우지 못한 계정이면 로그인 세션으로 가입 정보에 간다`() = runTest {
        val repository = FakeAuthRepository(profile = { newUserProfile })
        viewModel(login = { loginResult(sessionId = "s9") }, repository = repository).test(this) {
            containerHost.onSignInTapped()
            expectState(signingIn)
            expectSideEffect(
                LaunchLoginSideEffect.SignUpRequired(
                    AuthRoute.SignUp(userId = "u1", nickname = "페스티", currentTermsVersion = TERMS_V1, sessionId = "s9"),
                ),
            )
        }
    }

    @Test
    fun `계정 확인이 실패하면 iOS와 같이 덮개를 닫는다`() = runTest {
        val repository = FakeAuthRepository(profile = { throw AuthDomainError.Offline() })
        viewModel(repository = repository).test(this) {
            containerHost.onSignInTapped()
            expectState(signingIn)
            expectSideEffect(LaunchLoginSideEffect.Finished(LaunchLoginResult.Completed))
        }
    }

    @Test
    fun `사용자가 취소하면 문구 없이 다시 누를 수 있다`() = runTest {
        viewModel(login = { throw AuthDomainError.UserCancelled() }).test(this) {
            containerHost.onSignInTapped()
            expectState(signingIn)
            expectState(LaunchLoginState(attemptId = 1))
        }
    }

    @Test
    fun `중복 요청이면 문구 없이 다시 누를 수 있다`() = runTest {
        viewModel(login = { throw AuthDomainError.RequestInProgress() }).test(this) {
            containerHost.onSignInTapped()
            expectState(signingIn)
            expectState(LaunchLoginState(attemptId = 1))
        }
    }

    @Test
    fun `토큰 저장에 실패하면 저장 실패 문구를 보인다`() = runTest {
        viewModel(login = { throw AuthDomainError.CredentialSaveFailed() }).test(this) {
            containerHost.onSignInTapped()
            expectState(signingIn)
            expectState(LaunchLoginState(errorMessage = "로그인 정보를 저장하지 못했어요. 다시 시도해 주세요.", attemptId = 1))
        }
    }

    @Test
    fun `그 외 로그인 실패는 실패 문구를 보이고 다시 누르면 문구를 지운다`() = runTest {
        var fail = true
        val login = suspend { if (fail) throw AuthDomainError.Offline() else loginResult() }
        viewModel(login = login).test(this) {
            containerHost.onSignInTapped()
            expectState(signingIn)
            expectState(LaunchLoginState(errorMessage = "로그인에 실패했습니다. 다시 시도해 주세요.", attemptId = 1))

            fail = false
            containerHost.onSignInTapped()
            expectState(LaunchLoginState(isSigningIn = true, attemptId = 2))
            expectSideEffect(LaunchLoginSideEffect.Finished(LaunchLoginResult.Completed))
        }
    }

    @Test
    fun `건너뛰기는 건너뛰기 결과를 알린다`() = runTest {
        viewModel().test(this) {
            containerHost.onSkipTapped()
            expectSideEffect(LaunchLoginSideEffect.Finished(LaunchLoginResult.Skipped))
        }
    }

    @Test
    fun `로그인 중에는 다시 누르기와 건너뛰기를 무시하고 진행 중 시도의 결과만 반영한다`() = runTest {
        val pending = CompletableDeferred<io.festicore.domain.auth.AuthLoginResult>()
        val useCase = FakeGoogleLoginUseCase { pending.await() }
        LaunchLoginViewModel(useCase, FakeAuthRepository()).test(this) {
            containerHost.onSignInTapped()
            expectState(signingIn)

            containerHost.onSignInTapped()
            containerHost.onSkipTapped()
            pending.complete(loginResult())
            expectSideEffect(LaunchLoginSideEffect.Finished(LaunchLoginResult.Completed))
        }
        assertEquals(1, useCase.callCount)
    }
}
