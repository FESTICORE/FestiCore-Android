package io.festicore.domain.auth

/** 서버 로그인 결과. [sessionId]는 저장한 세션으로, 세션에 묶인 요청(가입)에 넘긴다. iOS `AuthLoginResult`. */
data class AuthLoginResult(
    val newUser: Boolean,
    val user: AuthUser,
    val sessionId: String,
)
