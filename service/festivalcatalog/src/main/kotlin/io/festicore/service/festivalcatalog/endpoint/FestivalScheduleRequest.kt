package io.festicore.service.festivalcatalog.endpoint

import io.festicore.core.network.endpoint.festiCoreRequest
import io.festicore.core.network.error.NetworkError
import io.festicore.service.api.base.AuthSessionError
import io.festicore.service.api.base.bearerAuthorization
import io.festicore.service.api.festival.FestivalApi
import io.festicore.service.apiendpoint.festival.FestivalScheduleReplaceBody
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * 내 일정 요청. 실패는 [NetworkError] · [AuthSessionError]. 재시도하지 않는다. Bearer 대상이다.
 * iOS `ScheduleGetRequest` · `SchedulePutRequest`(iOS는 `NetworkFestivalScheduleService` 파일 안에 둔다).
 */
class FestivalScheduleRequest(@PublishedApi internal val client: HttpClient) {
    /** `GET api/v1/me/schedule/{id}`. Bearer 대상이고, [expectedSessionId]를 넘기면 그 세션일 때만 보낸다. */
    suspend inline fun <reified T> mySchedule(festivalId: String, expectedSessionId: String? = null): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Get
            url(FestivalApi.mySchedule(festivalId))
            bearerAuthorization(expectedSessionId)
        }
    }

    /**
     * `PUT api/v1/me/schedule/{id}`. 내 일정 전체를 [body]로 바꾸고 저장된 일정을 돌려준다. Bearer 대상이고 [expectedSessionId] 세션일 때만 보낸다.
     * 409 `STATUS_STALE`(더 최신 일정이 서버에 있음)도 [NetworkError.Http]로 그대로 던진다. iOS `SchedulePutRequest`.
     */
    suspend inline fun <reified T> replaceMySchedule(
        festivalId: String,
        body: FestivalScheduleReplaceBody,
        expectedSessionId: String? = null,
    ): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Put
            url(FestivalApi.mySchedule(festivalId))
            bearerAuthorization(expectedSessionId)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    // 인증 플러그인이 던진 AuthSessionError는 festiCoreRequest가 Unknown으로 감싸므로 풀어서 던진다(AuthApi와 같음).
    @PublishedApi internal suspend inline fun <T> unwrapAuth(block: () -> T): T = try {
        block()
    } catch (e: NetworkError.Unknown) {
        throw e.cause as? AuthSessionError ?: e
    }
}
