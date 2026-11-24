package io.festicore.service.festivalcatalog.endpoint

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.endpoint.festiCoreExchange
import io.festicore.core.network.endpoint.festiCoreRequest
import io.festicore.core.network.error.NetworkError
import io.festicore.core.network.response.BaseResponse
import io.festicore.core.network.response.ErrorBody
import io.festicore.service.api.base.AuthSessionError
import io.festicore.service.api.base.bearerAuthorization
import io.festicore.service.api.festival.FestivalApi
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * 라이브 보드 · 축제 깃발 목록 요청. 실패는 [NetworkError], Bearer 요청은 [AuthSessionError]도 던진다. 재시도하지 않는다.
 * iOS `FestivalLiveBoardRequest.swift`(`FestivalLiveRequest` · `FestivalFlagsRequest`). 로그인 중 [live]만 Bearer 대상이다.
 */
class FestivalLiveBoardRequest(@PublishedApi internal val client: HttpClient) {
    /**
     * `GET api/v1/festivals/{id}/live`. `X-Device-Id`는 필수. [expectedSessionId]가 있으면 그 세션의 Bearer를 붙이고,
     * [etag]가 있으면 `If-None-Match`로 보낸다. 본문 `code == "FESTIVAL_NOT_FOUND"`는 상태 코드보다 먼저 본다.
     * 200 · 304 · 429 · 미공개 밖의 응답은 [NetworkError.Http].
     */
    suspend fun <T> live(
        festivalId: String,
        deviceId: String,
        etag: String?,
        expectedSessionId: String?,
        boardSerializer: KSerializer<T>,
    ): FestivalLiveResponse<T> = unwrapAuth {
        client.festiCoreExchange(
            block = {
                method = HttpMethod.Get
                url(FestivalApi.live(festivalId))
                header(DEVICE_ID_HEADER, deviceId)
                etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                if (expectedSessionId != null) bearerAuthorization(expectedSessionId)
            },
            handle = { response -> response.toLiveResponse(boardSerializer) },
        )
    }

    /** `GET api/v1/festivals/{id}/flags`. 인증 없음. */
    suspend inline fun <reified T> flags(festivalId: String): T = client.festiCoreRequest {
        method = HttpMethod.Get
        url(FestivalApi.flags(festivalId))
    }

    private suspend fun <T> HttpResponse.toLiveResponse(boardSerializer: KSerializer<T>): FestivalLiveResponse<T> {
        val text = bodyAsText()
        val error = text.errorBodyOrNull()
        if (error?.code == FESTIVAL_NOT_FOUND) return FestivalLiveResponse.FestivalNotFound
        return when (status.value) {
            200 -> {
                val body = FestiCoreJson.decodeFromString(BaseResponse.serializer(boardSerializer), text)
                if (body.code != SUCCESS) throw NetworkError.InvalidResponse(body.code, body.message)
                val data = body.data ?: throw NetworkError.Decoding(SerializationException("data가 없습니다"))
                FestivalLiveResponse.Loaded(data, headers[HttpHeaders.ETag])
            }

            304 -> FestivalLiveResponse.NotModified

            429 -> FestivalLiveResponse.RateLimited(headers[HttpHeaders.RetryAfter])

            else -> throw NetworkError.Http(status.value, error?.code, error?.message)
        }
    }

    private fun String.errorBodyOrNull(): ErrorBody? = try {
        FestiCoreJson.decodeFromString(ErrorBody.serializer(), this)
    } catch (_: IllegalArgumentException) { // SerializationException 포함
        null
    }

    // 인증 플러그인이 던진 AuthSessionError는 festiCoreRequest가 Unknown으로 감싸므로 풀어서 던진다(AuthApi와 같음).
    @PublishedApi internal suspend inline fun <T> unwrapAuth(block: () -> T): T = try {
        block()
    } catch (e: NetworkError.Unknown) {
        throw e.cause as? AuthSessionError ?: e
    }

    private companion object {
        const val DEVICE_ID_HEADER = "X-Device-Id"
        const val FESTIVAL_NOT_FOUND = "FESTIVAL_NOT_FOUND"
        const val SUCCESS = "SUCCESS"
    }
}

/** `/live` 응답을 상태 코드 · 헤더로 나눈 결과. iOS `NetworkFestivalLiveBoardService.decodeBoardData`. */
sealed interface FestivalLiveResponse<out T> {
    /** 200. [etag]는 응답의 `ETag` 헤더. */
    data class Loaded<T>(
        val board: T,
        val etag: String?,
    ) : FestivalLiveResponse<T>

    /** 304. 이전 응답을 다시 쓴다. */
    data object NotModified : FestivalLiveResponse<Nothing>

    /** 본문 code `FESTIVAL_NOT_FOUND`. 라이브 보드가 아직 공개되지 않았다. */
    data object FestivalNotFound : FestivalLiveResponse<Nothing>

    /** 429. [retryAfter]는 `Retry-After` 헤더 원문(없으면 null). */
    data class RateLimited(val retryAfter: String?) : FestivalLiveResponse<Nothing>
}
