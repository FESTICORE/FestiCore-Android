package io.festicore.service.apiendpoint.auth

import io.festicore.core.network.ErrorBody
import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.NetworkError
import io.festicore.core.network.festiCoreExchange
import io.festicore.core.network.festiCoreRequest
import io.festicore.service.api.AuthTokenResponse
import io.festicore.service.api.AuthUserDto
import io.festicore.service.api.LogoutResponse
import io.festicore.service.api.MeResponse
import io.festicore.service.api.RefreshResponse
import io.festicore.service.api.auth.AuthApi
import io.festicore.service.api.base.AuthSessionError
import io.festicore.service.api.base.bearerAuthorization
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * 인증 API 요청. 실패는 [NetworkError] 또는 [AuthSessionError]. 인증 요청은 재시도하지 않는다. iOS `AuthRequest`.
 * [logout] · [me] · [patchMe] · [patchNickname] · [withdraw]는 Bearer 대상이고, [expectedSessionId]를 넘기면 그 세션일 때만 보낸다.
 */
class AuthRequest(private val client: HttpClient) {
    suspend fun login(body: AuthLoginBody): AuthTokenResponse = send {
        method = HttpMethod.Post
        url(AuthApi.LOGIN)
        jsonBody(body)
    }

    /** 인증 플러그인을 거치지 않는다(Bearer 표시 없음). */
    suspend fun refresh(refreshToken: String): RefreshResponse = send {
        method = HttpMethod.Post
        url(AuthApi.REFRESH)
        jsonBody(RefreshBody(refreshToken))
    }

    suspend fun me(expectedSessionId: String? = null): MeResponse = send {
        method = HttpMethod.Get
        url(AuthApi.ME)
        bearerAuthorization(expectedSessionId)
    }

    suspend fun patchMe(body: SignUpBody, expectedSessionId: String? = null): AuthUserDto = send {
        method = HttpMethod.Patch
        url(AuthApi.ME)
        bearerAuthorization(expectedSessionId)
        jsonBody(body)
    }

    /** `POST api/v1/auth/logout`. 본문 없음. 서버는 계정의 refresh 토큰을 모두 폐기한다. */
    suspend fun logout(expectedSessionId: String? = null): LogoutResponse = send {
        method = HttpMethod.Post
        url(AuthApi.LOGOUT)
        bearerAuthorization(expectedSessionId)
    }

    /** `PATCH api/v1/me {nickname}`. 닉네임만 바꾼다. iOS `AuthRequest.updateNickname`. */
    suspend fun patchNickname(body: NicknameBody, expectedSessionId: String? = null): AuthUserDto = send {
        method = HttpMethod.Patch
        url(AuthApi.ME)
        bearerAuthorization(expectedSessionId)
        jsonBody(body)
    }

    /** `DELETE api/v1/me`. 본문 없음. 2xx면 본문(204 빈 본문)을 보지 않는다(iOS `FestiCoreEmptyResponse`). */
    suspend fun withdraw(expectedSessionId: String? = null) {
        unwrapAuth {
            client.festiCoreExchange(
                block = {
                    method = HttpMethod.Delete
                    url(AuthApi.ME)
                    bearerAuthorization(expectedSessionId)
                },
            ) { response ->
                if (!response.status.isSuccess()) {
                    val error = response.bodyAsText().errorBodyOrNull()
                    throw NetworkError.Http(response.status.value, error?.code, error?.message)
                }
            }
        }
    }

    private suspend inline fun <reified T> send(noinline block: HttpRequestBuilder.() -> Unit): T = unwrapAuth {
        client.festiCoreRequest<T>(block)
    }

    // 인증 플러그인이 던진 AuthSessionError는 요청 함수가 Unknown으로 감싸므로 풀어서 던진다.
    private inline fun <T> unwrapAuth(block: () -> T): T = try {
        block()
    } catch (e: NetworkError.Unknown) {
        throw e.cause as? AuthSessionError ?: e
    }

    private fun String.errorBodyOrNull(): ErrorBody? = try {
        FestiCoreJson.decodeFromString(ErrorBody.serializer(), this)
    } catch (_: IllegalArgumentException) { // SerializationException 포함
        null
    }

    private inline fun <reified B> HttpRequestBuilder.jsonBody(body: B) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}
