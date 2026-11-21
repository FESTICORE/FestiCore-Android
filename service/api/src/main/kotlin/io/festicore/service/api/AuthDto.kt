package io.festicore.service.api

import kotlinx.serialization.Serializable

/** `POST api/v1/auth/login` 요청. null 필드는 보내지 않는다(서버가 `null`도 값으로 검증). iOS `AuthLoginBody`. */
@Serializable
data class AuthLoginBody(
    val provider: String,
    val token: String,
    val authorizationCode: String? = null,
    val name: String? = null,
)

/** `POST api/v1/auth/login` 응답 `data`. iOS `AuthLoginDataDTO`. */
@Serializable
data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val newUser: Boolean,
    val user: AuthUserDto,
)

/** `POST api/v1/auth/refresh` 요청. */
@Serializable
data class RefreshBody(val refreshToken: String)

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

/** `PATCH api/v1/me` 가입 요청. iOS `AuthProfileUpdateBody`. */
@Serializable
data class SignUpBody(
    val nickname: String,
    val acceptTerms: AcceptTerms,
    val pushOptIn: Boolean,
) {
    @Serializable
    data class AcceptTerms(val version: String)
}
