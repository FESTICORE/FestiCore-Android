package io.festicore.service.api

import io.festicore.core.network.BaseResponse
import io.festicore.core.network.ErrorBody
import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.NetworkError
import io.festicore.core.network.festiCoreExchange
import io.festicore.core.network.festiCoreRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException

/**
 * 축제 API 요청. 실패는 [NetworkError], Bearer 요청은 [AuthSessionError]도 던진다. 재시도하지 않는다.
 * iOS `FestivalListRequest` · `FestivalDetailRequest` · `FestivalLiveRequest` · `FestivalFlagsRequest` ·
 * `FestivalOfficialNewsRequest` · `ScheduleGetRequest` · `SchedulePutRequest` · `FestivalPackRequest.version`. [mySchedule] · [replaceMySchedule] · 로그인 중 [live]만 Bearer 대상이다.
 */
class FestivalApi(private val client: HttpClient) {
    /** `GET api/v1/festivals?after=&limit=`. [after]가 null이면 첫 쪽. 커서는 받은 그대로 보낸다(URL 인코딩은 Ktor). */
    suspend fun list(after: String?, limit: Int): FestivalListDto = client.festiCoreRequest {
        method = HttpMethod.Get
        url(FestivalApiPath.FESTIVALS)
        after?.let { parameter("after", it) }
        parameter("limit", limit)
    }

    /** `GET api/v1/festivals/{id}`. 인증 없음. */
    suspend fun detail(festivalId: String): FestivalPackDocumentDto = client.festiCoreRequest {
        method = HttpMethod.Get
        url(FestivalApiPath.detail(festivalId))
    }

    /**
     * `GET api/v1/festivals/{id}/live`. `X-Device-Id`는 필수. [expectedSessionId]가 있으면 그 세션의 Bearer를 붙이고,
     * [etag]가 있으면 `If-None-Match`로 보낸다. 본문 `code == "FESTIVAL_NOT_FOUND"`는 상태 코드보다 먼저 본다.
     * 200 · 304 · 429 · 미공개 밖의 응답은 [NetworkError.Http].
     */
    suspend fun live(
        festivalId: String,
        deviceId: String,
        etag: String?,
        expectedSessionId: String?,
    ): FestivalLiveResponse = unwrapAuth {
        client.festiCoreExchange(
            block = {
                method = HttpMethod.Get
                url(FestivalApiPath.live(festivalId))
                header(DEVICE_ID_HEADER, deviceId)
                etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                if (expectedSessionId != null) bearerAuthorization(expectedSessionId)
            },
            handle = { response -> response.toLiveResponse() },
        )
    }

    /**
     * `GET api/v1/festivals/{id}/pack/version`. 인증 없음. 응답에 `message`가 없어(stage 확인) 공통 봉투 대신 `code` · `data`만 읽는다
     * (iOS `FestivalPackVersionResponseDTO`). `code != "SUCCESS"`면 [NetworkError.InvalidResponse], 2xx가 아니면 [NetworkError.Http].
     */
    suspend fun packVersion(festivalId: String): FestivalPackVersionDto = client.festiCoreExchange(
        block = {
            method = HttpMethod.Get
            url(FestivalApiPath.packVersion(festivalId))
        },
        handle = { response ->
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                val error = text.errorBodyOrNull()
                throw NetworkError.Http(response.status.value, error?.code, error?.message)
            }
            val body = FestiCoreJson.decodeFromString(FestivalPackVersionResponseDto.serializer(), text)
            if (body.code != SUCCESS) throw NetworkError.InvalidResponse(body.code, body.message.orEmpty())
            body.data ?: throw NetworkError.Decoding(SerializationException("data가 없습니다"))
        },
    )

    /** `GET api/v1/festivals/{id}/flags`. 인증 없음. */
    suspend fun flags(festivalId: String): FestivalFlagsDto = client.festiCoreRequest {
        method = HttpMethod.Get
        url(FestivalApiPath.flags(festivalId))
    }

    /** `GET api/v1/festivals/{id}/instagram`. 인증 없음. */
    suspend fun instagram(festivalId: String): FestivalOfficialNewsDto = client.festiCoreRequest {
        method = HttpMethod.Get
        url(FestivalApiPath.instagram(festivalId))
    }

    /** `GET api/v1/me/schedule/{id}`. Bearer 대상이고, [expectedSessionId]를 넘기면 그 세션일 때만 보낸다. */
    suspend fun mySchedule(festivalId: String, expectedSessionId: String? = null): FestivalScheduleDto = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Get
            url(FestivalApiPath.mySchedule(festivalId))
            bearerAuthorization(expectedSessionId)
        }
    }

    /**
     * `PUT api/v1/me/schedule/{id}`. 내 일정 전체를 [body]로 바꾸고 저장된 일정을 돌려준다. Bearer 대상이고 [expectedSessionId] 세션일 때만 보낸다.
     * 409 `STATUS_STALE`(더 최신 일정이 서버에 있음)도 [NetworkError.Http]로 그대로 던진다. iOS `SchedulePutRequest`.
     */
    suspend fun replaceMySchedule(
        festivalId: String,
        body: FestivalScheduleReplaceBody,
        expectedSessionId: String? = null,
    ): FestivalScheduleDto = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Put
            url(FestivalApiPath.mySchedule(festivalId))
            bearerAuthorization(expectedSessionId)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    private suspend fun HttpResponse.toLiveResponse(): FestivalLiveResponse {
        val text = bodyAsText()
        val error = text.errorBodyOrNull()
        if (error?.code == FESTIVAL_NOT_FOUND) return FestivalLiveResponse.FestivalNotFound
        return when (status.value) {
            200 -> {
                val body = FestiCoreJson.decodeFromString(BaseResponse.serializer(FestivalLiveBoardDto.serializer()), text)
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
    private suspend inline fun <T> unwrapAuth(block: () -> T): T = try {
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
sealed interface FestivalLiveResponse {
    /** 200. [etag]는 응답의 `ETag` 헤더. */
    data class Loaded(
        val board: FestivalLiveBoardDto,
        val etag: String?,
    ) : FestivalLiveResponse

    /** 304. 이전 응답을 다시 쓴다. */
    data object NotModified : FestivalLiveResponse

    /** 본문 code `FESTIVAL_NOT_FOUND`. 라이브 보드가 아직 공개되지 않았다. */
    data object FestivalNotFound : FestivalLiveResponse

    /** 429. [retryAfter]는 `Retry-After` 헤더 원문(없으면 null). */
    data class RateLimited(val retryAfter: String?) : FestivalLiveResponse
}
