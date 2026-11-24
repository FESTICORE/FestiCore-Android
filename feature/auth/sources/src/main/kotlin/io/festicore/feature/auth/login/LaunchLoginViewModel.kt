package io.festicore.feature.auth.login

import androidx.lifecycle.ViewModel
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.core.logger.LogCategory
import io.festicore.domain.auth.auth.AuthRepository
import io.festicore.domain.auth.entity.AuthDomainError
import io.festicore.domain.auth.entity.AuthLoginResult
import io.festicore.domain.auth.entity.AuthProfile
import io.festicore.domain.auth.google.GoogleLoginUseCase
import io.festicore.feature.auth.AuthCoordinator
import io.festicore.feature.auth.AuthRoute
import io.festicore.feature.auth.LaunchLoginResult
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
                authRepository.fetchProfile(expectedSessionId = login.sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: AuthDomainError.NotAuthenticated) {
                onSessionLost(attemptId, login.sessionId)
                return@subIntent
            } catch (_: Exception) {
                // iOS와 같이 오프라인 · 서버 오류면 로그인을 유지하고 덮개를 닫는다.
                null
            }
        onProfileLoaded(attemptId, login, profile)
    }

    /**
     * 계정 확인이 401이면 이 로그인 세션을 잃었다. iOS `SessionCoordinator.handleSessionExpired` · `handleProfileLoaded(.failure(.notAuthenticated))`.
     * 이 세션의 토큰 갱신이 401로 끝났으면(만료 이벤트) 저장소는 이미 비었고 상위가 문구 없는 새 launchLogin을 띄운다.
     * 만료 이벤트가 없으면 이 세션일 때만 지운다(그 사이 저장된 다른 세션은 남긴다). 덮개를 닫을지는 상위가 현재 세션과 비교해 정한다.
     */
    private suspend fun onSessionLost(attemptId: Int, sessionId: String) = subIntent {
        if (!(state.isSigningIn && state.attemptId == attemptId)) return@subIntent
        val expired = authRepository.hasSessionExpired(sessionId)
        if (!expired) {
            try {
                authRepository.clearSessionIf(sessionId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FestiCoreLogger.error("로그인 확인 실패 후 세션을 지울 수 없음: ${e::class.simpleName}", LogCategory.AUTH)
            }
        }
        // iOS `LoginFeature`는 로그인 성공 때 문구를 지운다. 401은 실패 문구를 보이지 않는다.
        reduce { state.copy(isSigningIn = false, errorMessage = null) }
        postSideEffect(LaunchLoginSideEffect.Finished(LaunchLoginResult.SessionLost(sessionId = sessionId, expired = expired)))
    }

    private suspend fun onProfileLoaded(
        attemptId: Int,
        login: AuthLoginResult,
        profile: AuthProfile?,
    ) = subIntent {
        if (!(state.isSigningIn && state.attemptId == attemptId)) return@subIntent
        if (profile == null || profile.isEligible) {
            val userId = profile?.user?.id ?: login.user.id
            postSideEffect(
                LaunchLoginSideEffect.Finished(
                    LaunchLoginResult.Completed(userId = userId, sessionId = login.sessionId, profile = profile),
                ),
            )
            return@subIntent
        }
        // 가입 정보는 이 화면 위에 쌓여 뒤로 오면 이 화면이 그대로 드러난다. iOS `LoginFeature`처럼 진행 표시를 먼저 끈다.
        reduce { state.copy(isSigningIn = false, errorMessage = null) }
        val route =
            AuthRoute.SignUp(
                userId = profile.user.id,
                nickname = profile.user.nickname,
                currentTermsVersion = profile.currentTermsVersion,
                sessionId = login.sessionId,
                isReconsent = profile.user.termsAcceptedAt != null,
            )
        postSideEffect(LaunchLoginSideEffect.SignUpRequired(route))
    }

    private suspend fun onSignInFailed(attemptId: Int, error: Exception) = subIntent {
        if (!(state.isSigningIn && state.attemptId == attemptId)) return@subIntent
        val message = error.signInMessage()
        reduce { state.copy(isSigningIn = false, errorMessage = message) }
        postSideEffect(LaunchLoginSideEffect.SignInFailed(showsMessage = message != null))
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
