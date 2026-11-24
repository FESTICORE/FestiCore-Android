package io.festicore.service.apiendpoint.flag

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.endpoint.FestiCoreUploadPart
import io.festicore.core.network.endpoint.festiCoreExchange
import io.festicore.core.network.endpoint.festiCoreRequest
import io.festicore.core.network.endpoint.festiCoreUpload
import io.festicore.core.network.error.NetworkError
import io.festicore.core.network.response.ErrorBody
import io.festicore.service.api.base.AuthSessionError
import io.festicore.service.api.base.bearerAuthorization
import io.festicore.service.api.flag.FlagApi
import io.festicore.service.apiendpoint.common.idempotencyKey
import io.festicore.service.apiendpoint.common.newIdempotencyKey
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
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 깃발 상세 · 팔로우 · 신고 · 대표 깃발 · 기수 요청. 실패는 [NetworkError], Bearer 요청은 [AuthSessionError]도 던진다. 재시도하지 않는다.
 * iOS `PublicFlagRequest` · `FestivalFlagFollowRequest` · `FlagUserRequest` · `FlagRequest`(기수).
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

    /** `GET api/v1/me/pinned-flag`. 지정하지 않았으면 `flag`가 null이다. Bearer 대상. */
    suspend inline fun <reified T> pinnedFlag(expectedSessionId: String): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Get
            url(FlagApi.PINNED_FLAG)
            bearerAuthorization(expectedSessionId)
        }
    }

    /** `GET api/v1/flags/{id}/bearer`. 기수 보드. Bearer 대상. [onServerDate]는 응답 `Date` 헤더 시각이다(오류 응답 포함). */
    suspend inline fun <reified T> bearer(
        flagId: String,
        expectedSessionId: String,
        noinline onServerDate: (Instant) -> Unit = {},
    ): T = unwrapAuth {
        client.festiCoreRequest(onResponse = { it.serverDate()?.let(onServerDate) }) {
            method = HttpMethod.Get
            url(FlagApi.bearer(flagId))
            bearerAuthorization(expectedSessionId)
        }
    }

    /** `PATCH api/v1/flags/{id}` 프로필 수정. Bearer 대상. */
    suspend inline fun <reified T> updateProfile(
        flagId: String,
        body: FlagProfileBody,
        expectedSessionId: String,
    ): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Patch
            url(FlagApi.detail(flagId))
            bearerAuthorization(expectedSessionId)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /**
     * `POST api/v1/flags/{id}/status` + `Idempotency-Key`. 같은 변경을 다시 보낼 때는 같은 키를 쓴다. Bearer 대상.
     * [onServerDate]는 응답 `Date` 헤더 시각이다(오류 응답 포함). 기수 대기열이 서버 시각 추정에 쓴다.
     */
    suspend inline fun <reified T> status(
        flagId: String,
        body: FlagStatusBody,
        idempotencyKey: String,
        expectedSessionId: String,
        noinline onServerDate: (Instant) -> Unit = {},
    ): T = unwrapAuth {
        client.festiCoreRequest(onResponse = { it.serverDate()?.let(onServerDate) }) {
            method = HttpMethod.Post
            url(FlagApi.status(flagId))
            bearerAuthorization(expectedSessionId)
            idempotencyKey(idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** `POST api/v1/flags/{id}/co-bearers/invite`. 본문 없음. Bearer 대상. */
    suspend inline fun <reified T> inviteCoBearer(flagId: String, expectedSessionId: String): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Post
            url(FlagApi.coBearerInvite(flagId))
            bearerAuthorization(expectedSessionId)
        }
    }

    /** `DELETE api/v1/flags/{id}/co-bearers/{userId}`. 2xx면 본문(204 빈 본문)을 보지 않는다(iOS `FestiCoreEmptyResponse`). */
    suspend fun removeCoBearer(
        flagId: String,
        userId: String,
        expectedSessionId: String,
    ) {
        unwrapAuth {
            client.festiCoreExchange(
                block = {
                    method = HttpMethod.Delete
                    url(FlagApi.coBearer(flagId, userId))
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

    /**
     * `POST api/v1/bearer/claim` `{festivalId, code}` + `X-Device-Id`. Bearer 대상.
     * 실패 본문의 `data.retryAfter` · `data.remainingAttempts`를 호출하는 쪽이 읽도록 2xx가 아니어도 상태와 원문을 돌려준다(iOS `sendResponse`).
     */
    suspend fun claim(
        body: BearerClaimBody,
        deviceId: String,
        expectedSessionId: String,
    ): FlagRawResponse = unwrapAuth {
        client.festiCoreExchange(
            block = {
                method = HttpMethod.Post
                url(FlagApi.BEARER_CLAIM)
                bearerAuthorization(expectedSessionId)
                header(DEVICE_ID_HEADER, deviceId)
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        ) { response -> FlagRawResponse(response.status.value, response.bodyAsText()) }
    }

    /** `POST api/v1/bearer/register` + `Idempotency-Key`. 다시 보낼 때도 같은 키와 같은 본문을 보낸다. Bearer 대상. */
    suspend inline fun <reified T> register(
        body: BearerRegisterBody,
        idempotencyKey: String,
        expectedSessionId: String,
    ): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Post
            url(FlagApi.BEARER_REGISTER)
            bearerAuthorization(expectedSessionId)
            idempotencyKey(idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** `GET api/v1/me/bearer-flags?festivalId=`. [festivalId]가 null이면 쿼리를 붙이지 않는다. Bearer 대상. */
    suspend inline fun <reified T> bearerFlags(festivalId: String?, expectedSessionId: String): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Get
            url(FlagApi.BEARER_FLAGS)
            festivalId?.let { parameter(FESTIVAL_ID_QUERY, it) }
            bearerAuthorization(expectedSessionId)
        }
    }

    /** `GET api/v1/me/follows?festivalId=`(최근 팔로우순). [festivalId]가 null이면 쿼리를 붙이지 않는다. Bearer 대상. */
    suspend inline fun <reified T> follows(festivalId: String?, expectedSessionId: String): T = unwrapAuth {
        client.festiCoreRequest {
            method = HttpMethod.Get
            url(FlagApi.FOLLOWS)
            festivalId?.let { parameter(FESTIVAL_ID_QUERY, it) }
            bearerAuthorization(expectedSessionId)
        }
    }

    /**
     * `POST api/v1/flags/{id}/photo` multipart 파트 하나 `photo`(`photo.jpg`, `image/jpeg`). Bearer 대상, 재시도 0, 제한 시간 60초.
     * [onProgress]는 보낸 비율(0~1). iOS `FlagPhotoUploadRequest`.
     */
    suspend inline fun <reified T> uploadPhoto(
        flagId: String,
        jpeg: ByteArray,
        expectedSessionId: String,
        noinline onProgress: (Double) -> Unit,
    ): T = unwrapAuth {
        client.festiCoreUpload(
            path = FlagApi.photo(flagId),
            part = FestiCoreUploadPart(PHOTO_PART, PHOTO_FILE_NAME, PHOTO_CONTENT_TYPE, jpeg),
            onProgress = onProgress,
        ) { bearerAuthorization(expectedSessionId) }
    }

    private fun String.errorBodyOrNull(): ErrorBody? = try {
        FestiCoreJson.decodeFromString(ErrorBody.serializer(), this)
    } catch (_: IllegalArgumentException) { // SerializationException 포함
        null
    }

    /** 응답 `Date` 헤더(RFC 1123). 없거나 읽지 못하면 null. */
    @PublishedApi internal fun HttpResponse.serverDate(): Instant? = try {
        headers[HttpHeaders.Date]?.let { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
    } catch (_: DateTimeParseException) {
        null
    }

    // 인증 플러그인이 던진 AuthSessionError는 festiCoreRequest가 Unknown으로 감싸므로 풀어서 던진다(FestivalApi와 같음).
    @PublishedApi internal suspend inline fun <T> unwrapAuth(block: () -> T): T = try {
        block()
    } catch (e: NetworkError.Unknown) {
        throw e.cause as? AuthSessionError ?: e
    }

    @PublishedApi internal companion object {
        const val DEVICE_ID_HEADER = "X-Device-Id"
        const val FESTIVAL_ID_QUERY = "festivalId"
        const val PHOTO_PART = "photo"
        const val PHOTO_FILE_NAME = "photo.jpg"
        const val PHOTO_CONTENT_TYPE = "image/jpeg"
    }
}

/** 상태 코드와 원문 본문. 실패 본문의 추가 필드를 호출하는 쪽이 읽어야 하는 요청이 쓴다. */
class FlagRawResponse(
    val status: Int,
    val body: String,
)
