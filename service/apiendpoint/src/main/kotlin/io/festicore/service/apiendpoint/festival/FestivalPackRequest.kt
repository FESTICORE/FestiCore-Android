package io.festicore.service.apiendpoint.festival

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.endpoint.festiCoreExchange
import io.festicore.core.network.error.NetworkError
import io.festicore.core.network.response.ErrorBody
import io.festicore.service.api.festival.FestivalApi
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * 축제 팩 요청. 실패는 [NetworkError]. 재시도하지 않는다. iOS `FestivalPackRequest`.
 * 목록 · 상세 · 라이브 · 깃발 · 공식 소식 · 내 일정 요청은 `service/festivalcatalog`의 `endpoint/`에 있다(iOS `FestiCoreFestivalCatalog/Sources/Endpoint`).
 */
class FestivalPackRequest(@PublishedApi internal val client: HttpClient) {
    /**
     * `GET api/v1/festivals/{id}/pack/version`. 인증 없음. 응답에 `message`가 없어(stage 확인) 공통 봉투 대신 `code` · `data`만 읽는다
     * (iOS `FestivalPackVersionResponseDTO`). `code != "SUCCESS"`면 [NetworkError.InvalidResponse], 2xx가 아니면 [NetworkError.Http].
     */
    suspend fun <T> packVersion(festivalId: String, dataSerializer: KSerializer<T>): T = client.festiCoreExchange(
        block = {
            method = HttpMethod.Get
            url(FestivalApi.packVersion(festivalId))
        },
        handle = { response -> response.packData(dataSerializer) },
    )

    /**
     * `GET api/v1/festivals/{id}/pack/changes?from=`. 인증 없음. 봉투는 [packVersion]과 같게 `code` · `data`만 읽는다
     * (iOS `FestivalPackRequest.changes` · `FestivalPackChangesResponseDTO`).
     */
    suspend fun <T> packChanges(
        festivalId: String,
        fromVersion: Int,
        dataSerializer: KSerializer<T>,
    ): T = client.festiCoreExchange(
        block = {
            method = HttpMethod.Get
            url(FestivalApi.packChanges(festivalId))
            parameter("from", fromVersion)
        },
        handle = { response -> response.packData(dataSerializer) },
    )

    /** `code` · `data` 봉투. `code != "SUCCESS"`면 [NetworkError.InvalidResponse], 2xx가 아니면 [NetworkError.Http]. */
    private suspend fun <T> HttpResponse.packData(dataSerializer: KSerializer<T>): T {
        val text = bodyAsText()
        if (!status.isSuccess()) {
            val error = text.errorBodyOrNull()
            throw NetworkError.Http(status.value, error?.code, error?.message)
        }
        val body = FestiCoreJson.decodeFromString(PackVersionResponse.serializer(dataSerializer), text)
        if (body.code != SUCCESS) throw NetworkError.InvalidResponse(body.code, body.message.orEmpty())
        return body.data ?: throw NetworkError.Decoding(SerializationException("data가 없습니다"))
    }

    private fun String.errorBodyOrNull(): ErrorBody? = try {
        FestiCoreJson.decodeFromString(ErrorBody.serializer(), this)
    } catch (_: IllegalArgumentException) { // SerializationException 포함
        null
    }

    private companion object {
        const val SUCCESS = "SUCCESS"
    }
}

/** `GET api/v1/festivals/{id}/pack/version` · `pack/changes` 응답 봉투. `message`가 없을 수 있다. iOS `FestivalPackVersionResponseDTO`. */
@Serializable
private class PackVersionResponse<T>(
    val code: String,
    val message: String? = null,
    val data: T? = null,
)
