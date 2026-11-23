package io.festicore.service.apiendpoint.usernotification

import io.festicore.core.network.ErrorBody
import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.NetworkError
import io.festicore.core.network.festiCoreExchange
import io.festicore.core.network.festiCoreRequest
import io.festicore.service.api.UserNotificationCountDto
import io.festicore.service.api.UserNotificationListDto
import io.festicore.service.api.base.AuthSessionError
import io.festicore.service.api.base.bearerAuthorization
import io.festicore.service.api.usernotification.UserNotificationApi
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * 내 알림 요청. 모두 Bearer 대상이고 [expectedSessionId] 세션일 때만 보낸다(iOS `sessionBound`). 재시도하지 않는다.
 * 실패는 [NetworkError] 또는 [AuthSessionError]. iOS `UserNotificationRequest`.
 */
class UserNotificationRequest(private val client: HttpClient) {
    /** `GET api/v1/me/notifications?before=`. [before]가 null이면 첫 쪽(쿼리 없음). */
    suspend fun list(before: String?, expectedSessionId: String): UserNotificationListDto = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Get
            url(UserNotificationApi.LIST)
            before?.let { parameter("before", it) }
            bearerAuthorization(expectedSessionId)
        }
    }

    /** `GET api/v1/me/notifications/unread-count`. */
    suspend fun unreadCount(expectedSessionId: String): UserNotificationCountDto = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Get
            url(UserNotificationApi.UNREAD_COUNT)
            bearerAuthorization(expectedSessionId)
        }
    }

    /** `POST api/v1/me/notifications/read`. 2xx면 본문(204 빈 본문 포함)을 보지 않는다(iOS `FestiCoreEmptyResponse`). */
    suspend fun read(body: UserNotificationIdsBody, expectedSessionId: String) = sendIds(UserNotificationApi.READ, body, expectedSessionId)

    /** `POST api/v1/me/notifications/delete`. 응답 처리는 [read]와 같다. */
    suspend fun delete(body: UserNotificationIdsBody, expectedSessionId: String) =
        sendIds(UserNotificationApi.DELETE, body, expectedSessionId)

    private suspend fun sendIds(
        path: String,
        body: UserNotificationIdsBody,
        expectedSessionId: String,
    ) = unwrapAuth {
        client.festiCoreExchange(
            block = {
                method = HttpMethod.Post
                url(path)
                bearerAuthorization(expectedSessionId)
                jsonBody(body)
            },
        ) { response ->
            if (!response.status.isSuccess()) {
                val error = response.bodyAsText().errorBodyOrNull()
                throw NetworkError.Http(response.status.value, error?.code, error?.message)
            }
        }
    }

    private fun HttpRequestBuilder.jsonBody(body: UserNotificationIdsBody) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private fun String.errorBodyOrNull(): ErrorBody? = try {
        FestiCoreJson.decodeFromString(ErrorBody.serializer(), this)
    } catch (_: IllegalArgumentException) { // SerializationException 포함
        null
    }

    // 인증 플러그인이 던진 AuthSessionError는 요청 함수가 Unknown으로 감싸므로 풀어서 던진다(AuthApi와 같음).
    private suspend inline fun <T> unwrapAuth(block: () -> T): T = try {
        block()
    } catch (e: NetworkError.Unknown) {
        throw e.cause as? AuthSessionError ?: e
    }
}
