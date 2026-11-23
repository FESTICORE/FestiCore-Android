package io.festicore.service.apiendpoint.auth

import kotlinx.serialization.Serializable

/** `POST api/v1/auth/login` 요청. null 필드는 보내지 않는다(서버가 `null`도 값으로 검증). iOS `AuthLoginBody`. */
@Serializable
data class AuthLoginBody(
    val provider: String,
    val token: String,
    val authorizationCode: String? = null,
    val name: String? = null,
)

/** `POST api/v1/auth/refresh` 요청. */
@Serializable
data class RefreshBody(val refreshToken: String)

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

/** `PATCH api/v1/me` 닉네임 변경 요청. iOS `AuthNicknameUpdateBody`. */
@Serializable
data class NicknameBody(val nickname: String)
