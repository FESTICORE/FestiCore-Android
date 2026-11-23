package io.festicore.domain.auth.entity

/** 서버 사용자. iOS `AuthUser`. */
data class AuthUser(
    val id: String,
    val provider: String,
    val email: String?,
    val nickname: String?,
    val nicknameConfirmed: Boolean,
    val termsAcceptedAt: String?,
    val termsVersion: String?,
    val pushOptIn: Boolean,
    val profileImageUrl: String?,
)
