package io.festicore.feature.auth

import androidx.lifecycle.ViewModel
import io.festicore.domain.auth.AuthDomainError
import io.festicore.domain.auth.AuthNicknameRule
import io.festicore.domain.auth.AuthProfile
import io.festicore.domain.auth.AuthRepository
import io.festicore.domain.auth.AuthUser
import kotlin.coroutines.cancellation.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * iOS `SignUpFeature` + 가입 제출 · 결과 처리(`AuthCoordinator.submitSignUp`, `SessionCoordinator.handleProfileUpdated` · `handleProfileLoaded`).
 * [route]의 프로필로 초기화하고, 가입은 [AuthRoute.SignUp.sessionId] 세션에 묶는다.
 */
class SignUpViewModel(
    private val route: AuthRoute.SignUp,
    private val authRepository: AuthRepository,
) : ViewModel(),
    OrbitContainerHost<SignUpState, SignUpState, SignUpSideEffect> {
    override val container =
        orbitContainer<SignUpState, SignUpSideEffect>(
            // 예전 약관에 동의한 기존 회원은 가입이 아니라 재동의라는 점을 처음부터 알린다(iOS `SessionCoordinator.swift:336-338`).
            SignUpState(currentTermsVersion = route.currentTermsVersion)
                .applying(route.nickname, route.currentTermsVersion)
                .copy(message = if (route.isReconsent) TERMS_CHANGED_RECONSENT else null),
        )

    fun onNicknameChanged(value: String) = intent {
        reduce { state.copy(nickname = AuthNicknameRule.limited(value), nicknameEdited = true) }
    }

    fun onServiceTermsChanged(accepted: Boolean) = intent {
        reduce { state.copy(acceptedServiceTerms = accepted).syncingRequiredTerms() }
    }

    fun onPrivacyTermsChanged(accepted: Boolean) = intent {
        reduce { state.copy(acceptedPrivacyTerms = accepted).syncingRequiredTerms() }
    }

    fun onLocationTermsChanged(accepted: Boolean) = intent {
        reduce { state.copy(acceptedLocationTerms = accepted).syncingRequiredTerms() }
    }

    fun onAllConsentToggled() = intent {
        val accepted = !state.isAllConsented
        reduce {
            state
                .copy(acceptedServiceTerms = accepted, acceptedPrivacyTerms = accepted, acceptedLocationTerms = accepted)
                .syncingRequiredTerms()
        }
    }

    fun onLegalDocumentTapped(document: LegalDocument) = intent {
        postSideEffect(SignUpSideEffect.OpenLegalDocument(document))
    }

    fun onSubmitTapped() = intent {
        if (!state.canSubmit) return@intent
        val nickname = AuthNicknameRule.trimmed(state.nickname)
        if (!AuthNicknameRule.isValid(nickname)) {
            reduce { state.copy(message = INVALID_NICKNAME) }
            return@intent
        }
        val termsVersion = state.currentTermsVersion
        reduce { state.copy(isSubmitting = true, message = null) }
        submit(nickname, termsVersion)
    }

    /** 뒤로(시스템 뒤로 포함). iOS와 같이 확정된 로그인 세션은 유지하고 흐름에 닫기를 알린다(`AuthCoordinator.swift:184-204`). */
    fun onCloseTapped() = intent { postSideEffect(SignUpSideEffect.Closed) }

    private suspend fun submit(nickname: String, termsVersion: String) = subIntent {
        val result =
            catching {
                authRepository.signUp(
                    nickname = nickname,
                    termsVersion = termsVersion,
                    expectedUserId = route.userId,
                    expectedSessionId = route.sessionId,
                )
            }
        onSignUpSubmitted(result, termsVersion)
    }

    private suspend fun reloadProfile() = subIntent {
        onProfileReloaded(catching { authRepository.fetchProfile(expectedSessionId = route.sessionId) })
    }

    private suspend fun onSignUpSubmitted(result: Result<AuthUser>, termsVersion: String) = subIntent {
        val user = result.getOrNull()
        if (user != null) {
            if (AuthProfile(user, termsVersion).isEligible) {
                // 제출 사이 약관 버전이 올랐을 수 있어 현재 프로필로 다시 판단한다. route 세션이 아니면 NotAuthenticated로 버린다.
                onSignUpVerified(catching { authRepository.fetchProfile(expectedSessionId = route.sessionId) })
            } else {
                reduce { state.copy(isSubmitting = false, message = REQUIRED_INFO_MISSING) }
            }
            return@subIntent
        }
        when (result.exceptionOrNull()) {
            is AuthDomainError.TermsVersionOutdated -> onTermsVersionOutdated()
            is AuthDomainError.NotAuthenticated -> postSideEffect(SignUpSideEffect.SessionLost)
            is AuthDomainError.Offline -> reduce { state.copy(isSubmitting = false, message = SAVE_OFFLINE) }
            else -> reduce { state.copy(isSubmitting = false, message = SAVE_FAILED) }
        }
    }

    /**
     * 가입 직후 다시 읽은 프로필로 완료를 정한다. 약관 버전이 올라 조건 미달이면 체크를 지우고 남는다.
     * 401이면 로그인으로 돌아가고, 오프라인 · 서버 오류는 가입이 이미 저장됐으므로 완료로 보고 다음 포그라운드 재확인에 맡긴다.
     */
    private suspend fun onSignUpVerified(result: Result<AuthProfile>) = subIntent {
        val profile = result.getOrNull()
        when {
            profile != null && !profile.isEligible ->
                reduce {
                    state
                        .applying(profile.user.nickname, profile.currentTermsVersion)
                        .clearingRequiredTerms()
                        .copy(isSubmitting = false, message = TERMS_CHANGED)
                }

            result.exceptionOrNull() is AuthDomainError.NotAuthenticated -> postSideEffect(SignUpSideEffect.SessionLost)

            else -> postSideEffect(SignUpSideEffect.Completed)
        }
    }

    /** 체크를 지우고 프로필을 한 번만 다시 읽는다. 다시 바뀌면 제출을 막는다. */
    private suspend fun onTermsVersionOutdated() = subIntent {
        if (state.hasRetriedTermsUpdate) {
            reduce {
                state.clearingRequiredTerms().copy(isSubmitting = false, termsUpdateExhausted = true, message = TERMS_CHANGED_AGAIN)
            }
            return@subIntent
        }
        reduce { state.clearingRequiredTerms().copy(hasRetriedTermsUpdate = true, message = TERMS_CHANGED) }
        reloadProfile()
    }

    private suspend fun onProfileReloaded(result: Result<AuthProfile>) = subIntent {
        val profile = result.getOrNull()
        if (profile != null) {
            if (profile.isEligible) {
                postSideEffect(SignUpSideEffect.Completed)
            } else {
                reduce {
                    state
                        .applying(profile.user.nickname, profile.currentTermsVersion)
                        .copy(isSubmitting = false, message = TERMS_CHANGED_RECONSENT)
                }
            }
            return@subIntent
        }
        when (result.exceptionOrNull()) {
            is AuthDomainError.NotAuthenticated -> postSideEffect(SignUpSideEffect.SessionLost)
            is AuthDomainError.Offline -> reduce { state.copy(isSubmitting = false, message = OFFLINE_PROFILE) }
            else -> reduce { state.copy(isSubmitting = false, message = PROFILE_UNAVAILABLE) }
        }
    }

    private inline fun <T> catching(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** iOS `AuthFlowState.Message`. */
    companion object {
        const val INVALID_NICKNAME = "닉네임은 1~12자로 입력해 주세요."
        const val TERMS_CHANGED = "약관이 변경됐어요. 내용을 확인하고 다시 동의해 주세요."
        const val TERMS_CHANGED_RECONSENT = "변경된 약관을 확인하고 다시 동의해 주세요."
        const val TERMS_CHANGED_AGAIN = "약관이 다시 변경됐어요. 나중에 다시 시도해 주세요."
        const val REQUIRED_INFO_MISSING = "필수 정보가 확인되지 않았어요. 다시 시도해 주세요."
        const val SAVE_OFFLINE = "오프라인에서 저장하지 못했어요. 연결 후 다시 시도해 주세요."
        const val SAVE_FAILED = "저장하지 못했어요. 다시 시도해 주세요."
        const val OFFLINE_PROFILE = "오프라인에서 계정 정보를 확인할 수 없어요. 연결 후 다시 시도해 주세요."
        const val PROFILE_UNAVAILABLE = "회원 정보를 확인할 수 없어요. 다시 시도해 주세요."
    }
}
