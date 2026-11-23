package io.festicore.feature.auth

import io.festicore.domain.auth.entity.AuthNicknameRule
import io.festicore.domain.auth.entity.AuthProfile
/**
 * iOS `SignUpFeature.State` + 부모 흐름 값(`AuthFlowState`의 제출 중 · 문구 · 약관 재변경).
 *
 * @param currentTermsVersion 서버 현재 약관 버전.
 * @param nicknameEdited 사용자가 닉네임을 직접 고쳤는지. 고친 값은 서버 닉네임으로 덮어쓰지 않는다.
 * @param termsDraftVersion 필수 약관을 처음 체크할 때의 약관 버전. 새 버전이 오면 체크를 지운다.
 * @param hasRetriedTermsUpdate `422 TERMS_VERSION_OUTDATED`로 프로필을 한 번 다시 읽었는지.
 * @param termsUpdateExhausted 약관이 다시 바뀌어 더 제출할 수 없는지.
 */
data class SignUpState(
    val currentTermsVersion: String,
    val nickname: String = "",
    val nicknameEdited: Boolean = false,
    val acceptedServiceTerms: Boolean = false,
    val acceptedPrivacyTerms: Boolean = false,
    val acceptedLocationTerms: Boolean = false,
    val termsDraftVersion: String? = null,
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val hasRetriedTermsUpdate: Boolean = false,
    val termsUpdateExhausted: Boolean = false,
) {
    /** 세 필수 약관이 모두 체크됐을 때만 true다. */
    val acceptedTerms: Boolean get() = acceptedServiceTerms && acceptedPrivacyTerms && acceptedLocationTerms

    val isAllConsented: Boolean get() = acceptedTerms

    /** 필수 약관이 모두 체크됐을 때만 체크한 시점의 약관 버전에 동의한 것으로 본다. */
    val consentedVersion: String? get() = if (acceptedTerms) termsDraftVersion else null

    val canSubmit: Boolean
        get() = !isSubmitting &&
            !termsUpdateExhausted &&
            acceptedTerms &&
            consentedVersion == currentTermsVersion &&
            AuthNicknameRule.trimmed(nickname).isNotEmpty()

    /** 가입이 필요한 프로필을 받았을 때 서버 닉네임을 처음 한 번 채우고, 약관 버전이 바뀌었으면 필수 체크를 지운다. */
    fun applying(serverNickname: String?, termsVersion: String): SignUpState {
        var next = copy(currentTermsVersion = termsVersion)
        if (!nicknameEdited && nickname.isEmpty() && !serverNickname.isNullOrEmpty()) next = next.copy(nickname = serverNickname)
        if (termsDraftVersion != null && termsDraftVersion != termsVersion) next = next.clearingRequiredTerms()
        return next
    }

    fun clearingRequiredTerms(): SignUpState = copy(
        acceptedServiceTerms = false,
        acceptedPrivacyTerms = false,
        acceptedLocationTerms = false,
        termsDraftVersion = null,
    )

    /** 필수 약관을 처음 체크할 때의 약관 버전을 기록하고, 모두 해제하면 지운다. */
    fun syncingRequiredTerms(): SignUpState = copy(
        termsDraftVersion =
            if (acceptedServiceTerms || acceptedPrivacyTerms || acceptedLocationTerms) {
                termsDraftVersion ?: currentTermsVersion
            } else {
                null
            },
    )
}

/** iOS `SignUpDelegate` + 가입 결과(`SessionCoordinator.handleProfileUpdated`). */
sealed interface SignUpSideEffect {
    /** 가입을 마쳐 메인 탭으로 간다. */
    data class Completed(val profile: AuthProfile?) : SignUpSideEffect

    /** 뒤로 나왔다. 세션은 유지한다. */
    data object Closed : SignUpSideEffect

    /** 로그인을 잃었다(401). 첫 진입 흐름이면 첫 진입 로그인으로, 재확인 가입이면 덮개를 닫는다. */
    data object SessionLost : SignUpSideEffect

    data class OpenLegalDocument(val document: LegalDocument) : SignUpSideEffect
}
