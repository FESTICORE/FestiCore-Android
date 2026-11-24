package io.festicore.domain.bearer.sources

import io.festicore.core.network.client.createFestiCoreHttpClient
import io.festicore.domain.bearer.sources.model.mapper.BearerImageUrl
import io.festicore.domain.bearer.sources.repository.BearerRepositoryImpl
import io.festicore.service.api.base.BearerAuthorization
import io.festicore.service.api.base.BearerAuthorizationKey
import io.festicore.service.apiendpoint.flag.FlagRequest
import io.festicore.service.pack.CachedFestivalPack
import io.festicore.service.pack.PackDownloadEvent
import io.festicore.service.pack.PackSyncResult
import io.festicore.service.pack.PackSyncService
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 보낸 요청을 기록하고 정한 응답을 주는 Ktor `MockEngine` 클라이언트. iOS `BearerTestDoubles.swift` `RecordingClient`.
 * [routes]가 있으면 경로마다 다른 응답을 주고, 등록하지 않은 경로는 500이다. [offline]이면 연결 실패를 던진다.
 */
internal class RecordingClient(
    private val status: Int = 200,
    private val json: String = "",
    private val routes: Map<String, Pair<Int, String>> = emptyMap(),
    private val offline: Boolean = false,
) {
    val sent = mutableListOf<HttpRequestData>()
    val last: HttpRequestData get() = sent.last()

    /** multipart 본문은 응답 뒤에 다시 읽을 수 없어 보낼 때 바이트로 남긴다. */
    val multipartBodies = mutableListOf<ByteArray>()

    val client = createFestiCoreHttpClient(
        baseUrl = BASE_URL,
        engine = MockEngine { request ->
            sent += request
            if (request.body.contentType?.match(ContentType.MultiPart.FormData) == true) {
                multipartBodies += (request.body as OutgoingContent).toByteArray()
            }
            if (offline) throw IOException("연결 없음")
            val (code, body) = routes[request.url.encodedPath.removePrefix("/")] ?: if (routes.isEmpty()) status to json else 500 to ""
            respond(body, HttpStatusCode.fromValue(code), headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    companion object {
        const val BASE_URL = "https://festicore.test/"
    }
}

internal const val SESSION_ID = "ses_1"
internal const val DEVICE_ID = "8a5c2f8e-1d2b-4c3d-9e4f-0a1b2c3d4e5f"

internal fun makeRepository(client: RecordingClient, loggedIn: Boolean = true) = BearerRepositoryImpl(
    api = FlagRequest(client.client),
    currentSessionId = { if (loggedIn) SESSION_ID else null },
    installationDeviceId = { DEVICE_ID },
    imageUrl = BearerImageUrl(RecordingClient.BASE_URL),
)

internal val HttpRequestData.path: String get() = url.encodedPath.removePrefix("/")

internal val HttpRequestData.isSessionBound: Boolean
    get() = attributes.getOrNull(BearerAuthorizationKey) == BearerAuthorization(SESSION_ID)

internal val HttpRequestData.jsonBody: JsonObject get() = Json.parseToJsonElement((body as TextContent).text).jsonObject

/** multipart 파트 하나의 헤더 원문과 본문 */
internal class MultipartPart(
    val headers: String,
    val body: ByteArray,
)

/** 마지막으로 보낸 multipart 본문을 경계로 나눠 파트별 헤더 · 본문을 돌려준다. */
internal fun RecordingClient.lastMultipartParts(): List<MultipartPart> {
    val boundary = last.body.contentType?.parameter("boundary") ?: error("multipart가 아니다")
    val text = String(multipartBodies.last(), Charsets.ISO_8859_1)
    return text.split("--$boundary").drop(1).dropLast(1).map { raw ->
        val part = raw.removePrefix("\r\n").removeSuffix("\r\n")
        val (headers, content) = part.split("\r\n\r\n", limit = 2)
        MultipartPart(headers, content.toByteArray(Charsets.ISO_8859_1))
    }
}

internal class StubPackSyncService(private val pack: CachedFestivalPack?) : PackSyncService {
    override suspend fun cachedPack(festivalId: String) = pack

    override suspend fun cachedPacks() = listOfNotNull(pack)

    override suspend fun sync(festivalId: String): PackSyncResult = throw UnsupportedOperationException()

    override fun download(festivalId: String): Flow<PackDownloadEvent> = emptyFlow()

    override suspend fun deletePack(festivalId: String) = Unit

    override fun updates(festivalId: String): Flow<PackSyncResult> = emptyFlow()
}

/** 실패한 예외를 돌려준다. 성공하면 null. */
internal suspend fun failure(block: suspend () -> Unit): Throwable? = try {
    block()
    null
} catch (e: Exception) {
    e
}
