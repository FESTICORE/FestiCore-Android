package io.festicore.service.api

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.util.AttributeKey

/**
 * Bearer 토큰이 필요한 요청 표시. 이 속성이 있는 요청에만 인증 플러그인(service/auth)이 토큰을 붙인다.
 * [expectedSessionId]가 있으면 현재 세션이 그 세션일 때만 보낸다. iOS `FestiCoreAuthorization.sessionBound`.
 */
data class BearerAuthorization(val expectedSessionId: String? = null)

val BearerAuthorizationKey: AttributeKey<BearerAuthorization> = AttributeKey("FestiCoreBearerAuthorization")

fun HttpRequestBuilder.bearerAuthorization(expectedSessionId: String? = null) {
    attributes.put(BearerAuthorizationKey, BearerAuthorization(expectedSessionId))
}

/** 도메인 매핑 전 인증 세션 오류. */
sealed class AuthSessionError : Exception() {
    /** 세션이 없거나 요청의 세션과 현재 세션이 다르다. 요청을 보내거나 재전송하지 않았다. */
    class NotAuthenticated : AuthSessionError()
}
