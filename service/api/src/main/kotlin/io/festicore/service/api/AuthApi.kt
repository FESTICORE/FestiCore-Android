package io.festicore.service.api

import io.festicore.core.network.NetworkError
import io.festicore.core.network.festiCoreRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * 인증 API 요청. 실패는 [NetworkError] 또는 [AuthSessionError]. 인증 요청은 재시도하지 않는다. iOS `AuthRequest`.
 * [me] · [patchMe]는 Bearer 대상이고, [expectedSessionId]를 넘기면 그 세션일 때만 보낸다.
 */
class AuthApi(private val client: HttpClient) {
    suspend fun login(body: AuthLoginBody): AuthTokenResponse = send {
        method = HttpMethod.Post
        url(AuthApiPath.LOGIN)
        jsonBody(body)
    }

    /** 인증 플러그인을 거치지 않는다(Bearer 표시 없음). */
    suspend fun refresh(refreshToken: String): RefreshResponse = send {
        method = HttpMethod.Post
        url(AuthApiPath.REFRESH)
        jsonBody(RefreshBody(refreshToken))
    }

    suspend fun me(expectedSessionId: String? = null): MeResponse = send {
        method = HttpMethod.Get
        url(AuthApiPath.ME)
        bearerAuthorization(expectedSessionId)
    }

    suspend fun patchMe(body: SignUpBody, expectedSessionId: String? = null): AuthUserDto = send {
        method = HttpMethod.Patch
        url(AuthApiPath.ME)
        bearerAuthorization(expectedSessionId)
        jsonBody(body)
    }

    // 인증 플러그인이 던진 AuthSessionError는 festiCoreRequest가 Unknown으로 감싸므로 풀어서 던진다.
    private suspend inline fun <reified T> send(noinline block: HttpRequestBuilder.() -> Unit): T = try {
        client.festiCoreRequest<T>(block)
    } catch (e: NetworkError.Unknown) {
        throw e.cause as? AuthSessionError ?: e
    }

    private inline fun <reified B> HttpRequestBuilder.jsonBody(body: B) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}
