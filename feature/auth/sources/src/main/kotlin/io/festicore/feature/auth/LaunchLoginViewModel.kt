package io.festicore.feature.auth

import androidx.lifecycle.ViewModel
import io.festicore.domain.auth.AuthDomainError
import io.festicore.domain.auth.AuthLoginResult
import io.festicore.domain.auth.AuthProfile
import io.festicore.domain.auth.AuthRepository
import io.festicore.domain.auth.GoogleLoginUseCase
import kotlin.coroutines.cancellation.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * iOS `LoginFeature`(Google만) + 로그인 직후 계정 확인(`AuthCoordinator.acceptLogin` → `SessionCoordinator.handleProfileLoaded`).
 * 토큰은 서버 응답 직후 저장된다. 계정을 확인하는 동안에도 덮개를 유지해 홈이 잠깐 비치지 않게 한다.
 */
class LaunchLoginViewModel(
    private val googleLoginUseCase: GoogleLoginUseCase,
    private val authRepository: AuthRepository,
) : ViewModel(),
    OrbitContainerHost<LaunchLoginState, LaunchLoginState, LaunchLoginSideEffect> {
    override val container = orbitContainer<LaunchLoginState, LaunchLoginSideEffect>(LaunchLoginState())

    fun onSignInTapped() = intent {
        if (state.isSigningIn) return@intent
        val attemptId = state.attemptId + 1
        reduce { state.copy(isSigningIn = true, errorMessage = null, attemptId = attemptId) }
        signIn(attemptId)
    }

    fun onSkipTapped() = intent {
        if (state.isSigningIn) return@intent
        postSideEffect(LaunchLoginSideEffect.Finished(LaunchLoginResult.Skipped))
    }

    private suspend fun signIn(attemptId: Int) = subIntent {
        val login =
            try {
                googleLoginUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onSignInFailed(attemptId, e)
                return@subIntent
            }
        fetchProfile(attemptId, login)
    }

    private suspend fun fetchProfile(attemptId: Int, login: AuthLoginResult) = subIntent {
        val profile =
            try {
                authRepository.fetchProfile()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // iOS와 같이 계정 확인이 실패하면(오프라인 · 서버 오류 · 401) 덮개를 닫는다. 로그인 상태는 세션 저장소가 정한다.
                null
            }
        onProfileLoaded(attemptId, login, profile)
    }

    private suspend fun onProfileLoaded(
        attemptId: Int,
        login: AuthLoginResult,
        profile: AuthProfile?,
    ) = subIntent {
        if (!(state.isSigningIn && state.attemptId == attemptId)) return@subIntent
        if (profile == null || profile.isEligible) {
            postSideEffect(LaunchLoginSideEffect.Finished(LaunchLoginResult.Completed))
            return@subIntent
        }
        // 가입 정보에서 뒤로 오면 이 화면을 새로 그리므로 진행 표시는 그대로 둔다.
        val route =
            AuthRoute.SignUp(
                userId = profile.user.id,
                nickname = profile.user.nickname,
                currentTermsVersion = profile.currentTermsVersion,
                sessionId = login.sessionId,
            )
        postSideEffect(LaunchLoginSideEffect.SignUpRequired(route))
    }

    private suspend fun onSignInFailed(attemptId: Int, error: Exception) = subIntent {
        if (!(state.isSigningIn && state.attemptId == attemptId)) return@subIntent
        reduce { state.copy(isSigningIn = false, errorMessage = error.signInMessage()) }
    }

    companion object {
        const val SIGN_IN_FAILED = "로그인에 실패했습니다. 다시 시도해 주세요."
        const val LOGIN_SAVE_FAILED = "로그인 정보를 저장하지 못했어요. 다시 시도해 주세요."
    }
}

/** 사용자 취소 · 중복 요청은 문구 없음. iOS `LoginFeature.signInFailed` · `AuthFlowState.Message.loginSaveFailed`. */
private fun Exception.signInMessage(): String? = when (this) {
    is AuthDomainError.UserCancelled, is AuthDomainError.RequestInProgress -> null
    is AuthDomainError.CredentialSaveFailed -> LaunchLoginViewModel.LOGIN_SAVE_FAILED
    else -> LaunchLoginViewModel.SIGN_IN_FAILED
}
