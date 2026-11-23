package io.festicore.domain.auth.entity

/** `GET api/v1/me` 결과. iOS `AuthProfile`. */
data class AuthProfile(
    val user: AuthUser,
    val currentTermsVersion: String,
) {
    /** 가입을 마쳐 메인 탭에 들어갈 수 있는지. 아니면 회원가입 화면으로 간다. */
    val isEligible: Boolean
        get() = currentTermsVersion.isNotEmpty() &&
            user.nicknameConfirmed &&
            user.termsAcceptedAt != null &&
            user.termsVersion == currentTermsVersion
}
