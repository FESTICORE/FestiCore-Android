package io.festicore.service.api

import kotlinx.serialization.Serializable

/** `POST api/v1/auth/login` 응답 `data`. iOS `AuthLoginDataDTO`. */
@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val newUser: Boolean,
    val user: AuthUserDto,
)

/** `POST api/v1/auth/refresh` 응답 `data`. 서버는 refresh 토큰을 회전한다. */
@Serializable
data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
)

/** 로그인 · 가입 응답의 사용자. iOS `AuthUserDTO`. */
@Serializable
data class AuthUserDto(
    val id: String,
    val provider: String,
    val email: String? = null,
    val nickname: String? = null,
    val nicknameConfirmed: Boolean,
    val termsAcceptedAt: String? = null,
    val termsVersion: String? = null,
    val pushOptIn: Boolean,
    val profileImageUrl: String? = null,
)

/** `GET api/v1/me` 응답 `data`. iOS `AuthProfileDataDTO`. */
@Serializable
data class MeResponse(
    val id: String,
    val provider: String,
    val email: String? = null,
    val nickname: String? = null,
    val nicknameConfirmed: Boolean,
    val termsAcceptedAt: String? = null,
    val termsVersion: String? = null,
    val pushOptIn: Boolean,
    val profileImageUrl: String? = null,
    val currentTermsVersion: String,
)
