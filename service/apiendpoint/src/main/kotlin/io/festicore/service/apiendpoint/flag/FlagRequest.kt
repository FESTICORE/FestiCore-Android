package io.festicore.service.apiendpoint.flag

import io.festicore.core.network.endpoint.festiCoreRequest
import io.festicore.core.network.error.NetworkError
import io.festicore.service.api.base.AuthSessionError
import io.festicore.service.api.base.bearerAuthorization
import io.festicore.service.api.flag.FlagApi
import io.festicore.service.apiendpoint.common.idempotencyKey
import io.festicore.service.apiendpoint.common.newIdempotencyKey
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * 깃발 상세 · 팔로우 · 신고 · 대표 깃발 요청. 실패는 [NetworkError], Bearer 요청은 [AuthSessionError]도 던진다. 재시도하지 않는다.
 * iOS `PublicFlagRequest` · `FestivalFlagFollowRequest` · `FlagUserRequest`.
 */
class FlagRequest(@PublishedApi internal val client: HttpClient) {
    /** `GET api/v1/flags/{id}`. 익명 허용. [expectedSessionId]가 있으면 그 세션의 Bearer를 붙인다(iOS `.automatic`). */
    suspend inline fun <reified T> detail(flagId: String, expectedSessionId: String? = null): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Get
            url(FlagApi.detail(flagId))
            if (expectedSessionId != null) bearerAuthorization(expectedSessionId)
        }
    }

    /** `PUT api/v1/flags/{id}/follow` + `Idempotency-Key`. Bearer 대상. */
    suspend inline fun <reified T> follow(
        flagId: String,
        expectedSessionId: String? = null,
        idempotencyKey: String = newIdempotencyKey(),
    ): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Put
            url(FlagApi.follow(flagId))
            bearerAuthorization(expectedSessionId)
            idempotencyKey(idempotencyKey)
        }
    }

    /** `DELETE api/v1/flags/{id}/follow`. Bearer 대상, 멱등 키 없음. */
    suspend inline fun <reified T> unfollow(flagId: String, expectedSessionId: String? = null): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Delete
            url(FlagApi.follow(flagId))
            bearerAuthorization(expectedSessionId)
        }
    }

    /** `POST api/v1/flags/{id}/report` `{reason}`. Bearer 대상. */
    suspend inline fun <reified T> report(
        flagId: String,
        body: FlagReportBody,
        expectedSessionId: String? = null,
    ): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Post
            url(FlagApi.report(flagId))
            bearerAuthorization(expectedSessionId)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** `PUT api/v1/me/pinned-flag` `{flagId}`. 해제는 `flagId: null`을 명시한다. Bearer 대상. */
    suspend inline fun <reified T> setPinnedFlag(body: PinnedFlagBody, expectedSessionId: String? = null): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Put
            url(FlagApi.PINNED_FLAG)
            bearerAuthorization(expectedSessionId)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    // 인증 플러그인이 던진 AuthSessionError는 festiCoreRequest가 Unknown으로 감싸므로 풀어서 던진다(FestivalApi와 같음).
    @PublishedApi internal suspend inline fun <T> unwrapAuth(block: () -> T): T = try {
        block()
    } catch (e: NetworkError.Unknown) {
        throw e.cause as? AuthSessionError ?: e
    }
}
