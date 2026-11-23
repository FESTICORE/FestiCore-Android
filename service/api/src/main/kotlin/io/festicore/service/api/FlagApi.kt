package io.festicore.service.api

import io.festicore.core.network.NetworkError
import io.festicore.core.network.festiCoreRequest
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
class FlagApi(private val client: HttpClient) {
    /** `GET api/v1/flags/{id}`. 익명 허용. [expectedSessionId]가 있으면 그 세션의 Bearer를 붙인다(iOS `.automatic`). */
    suspend fun detail(flagId: String, expectedSessionId: String? = null): FlagDetailDto = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Get
            url(FlagApiPath.detail(flagId))
            if (expectedSessionId != null) bearerAuthorization(expectedSessionId)
        }
    }

    /** `PUT api/v1/flags/{id}/follow` + `Idempotency-Key`. Bearer 대상. */
    suspend fun follow(
        flagId: String,
        expectedSessionId: String? = null,
        idempotencyKey: String = newIdempotencyKey(),
    ): FlagFollowDto = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Put
            url(FlagApiPath.follow(flagId))
            bearerAuthorization(expectedSessionId)
            idempotencyKey(idempotencyKey)
        }
    }

    /** `DELETE api/v1/flags/{id}/follow`. Bearer 대상, 멱등 키 없음. */
    suspend fun unfollow(flagId: String, expectedSessionId: String? = null): FlagFollowDto = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Delete
            url(FlagApiPath.follow(flagId))
            bearerAuthorization(expectedSessionId)
        }
    }

    /** `POST api/v1/flags/{id}/report` `{reason}`. Bearer 대상. */
    suspend fun report(
        flagId: String,
        body: FlagReportBody,
        expectedSessionId: String? = null,
    ): FlagReportDto = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Post
            url(FlagApiPath.report(flagId))
            bearerAuthorization(expectedSessionId)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** `PUT api/v1/me/pinned-flag` `{flagId}`. 해제는 `flagId: null`을 명시한다. Bearer 대상. */
    suspend fun setPinnedFlag(body: PinnedFlagBody, expectedSessionId: String? = null): PinnedFlagDto = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Put
            url(FlagApiPath.PINNED_FLAG)
            bearerAuthorization(expectedSessionId)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    // 인증 플러그인이 던진 AuthSessionError는 festiCoreRequest가 Unknown으로 감싸므로 풀어서 던진다(FestivalApi와 같음).
    private suspend inline fun <T> unwrapAuth(block: () -> T): T = try {
        block()
    } catch (e: NetworkError.Unknown) {
        throw e.cause as? AuthSessionError ?: e
    }
}
