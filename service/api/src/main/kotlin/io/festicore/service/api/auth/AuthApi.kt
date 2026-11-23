package io.festicore.service.api.auth

/** 인증 API 경로. `BASE_URL`이 `/`로 끝나므로 앞 `/`를 붙이지 않는다. iOS `FestiCoreDomain` · `AuthAPI`. */
object AuthApi {
    const val LOGIN = "api/v1/auth/login"
    const val REFRESH = "api/v1/auth/refresh"
    const val LOGOUT = "api/v1/auth/logout"
    const val ME = "api/v1/me"
}
