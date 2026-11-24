package io.festicore.service.festivalcatalog

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.client.createFestiCoreHttpClient
import io.festicore.domain.festival.sources.service.FestivalLiveBoardServiceError
import io.festicore.service.festivalcatalog.endpoint.FestivalDetailRequest
import io.festicore.service.festivalcatalog.endpoint.FestivalListRequest
import io.festicore.service.festivalcatalog.endpoint.FestivalLiveBoardRequest
import io.festicore.service.festivalcatalog.endpoint.FestivalOfficialNewsRequest
import io.festicore.service.festivalcatalog.endpoint.FestivalScheduleRequest
import io.festicore.service.festivalcatalog.storage.FestivalCatalogCache
import io.festicore.service.festivalcatalog.url.FestivalImageUrlResolver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ETag 304 재사용 · 429 대기 · 404 미공개 · 세션별 캐시. iOS `NetworkFestivalLiveBoardService` 동작. */
class FestivalLiveBoardServiceTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var now = Instant.parse("2026-11-22T00:00:00Z")
    private var sessionId: String? = "session-1"

    /** `/live` 응답. 기본은 200 + ETag. */
    private var live: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { liveOk("\"e1\"", "2026-11-22T00:00:00Z") }

    /** `/flags` 응답 시 할 일(세션 바뀜 흉내). */
    private var onFlags: () -> Unit = {}

    private val client = createFestiCoreHttpClient(
        baseUrl = "https://example.test/",
        engine = MockEngine { request ->
            requests += request
            when {
                request.url.encodedPath.endsWith("/live") -> live(request)

                request.url.encodedPath.endsWith("/flags") -> {
                    onFlags()
                    json("""{"code":"SUCCESS","message":"ok","data":{"items":[{"id":"flg_a","name":"가짜 깃발"}]}}""")
                }

                request.url.encodedPath.endsWith("/instagram") ->
                    json("""{"code":"SUCCESS","message":"ok","data":{"festivalId":"fst_other","available":false,"posts":[]}}""")

                else -> json("""{"code":"SUCCESS","message":"ok","data":{"id":"fst_other"}}""")
            }
        },
    )
    private val api = FestivalLiveBoardRequest(client)

    private val service = NetworkFestivalLiveBoardService(
        api = api,
        deviceId = { "00000000-0000-4000-8000-000000000001" },
        currentSessionId = { sessionId },
        now = { now },
    )

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        vararg extra: Pair<String, String>,
    ) = respond(
        body,
        status,
        headersOf(
            *(listOf(HttpHeaders.ContentType to "application/json") + extra).map {
                it.first to listOf(it.second)
            }.toTypedArray(),
        ),
    )

    private fun MockRequestHandleScope.liveOk(
        etag: String?,
        serverAt: String,
        festivalId: String = "fst_a",
    ) = json(
        """{"code":"SUCCESS","message":"ok","data":{"festivalId":"$festivalId","activeFlags":[],"me":null,"serverAt":"$serverAt"}}""",
        HttpStatusCode.OK,
        *listOfNotNull(etag?.let { HttpHeaders.ETag to it }).toTypedArray(),
    )

    private val liveRequests get() = requests.filter { it.url.encodedPath.endsWith("/live") }

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `로그인 중이면 받은 ETag를 다음 요청에 보내고 304면 저장한 응답을 쓴다`() = runTest {
        val first = service.refresh("fst_a")
        live = { json("", HttpStatusCode.NotModified) }
        now = now.plusSeconds(35)

        val second = service.refresh("fst_a")

        assertNull(liveRequests[0].headers[HttpHeaders.IfNoneMatch])
        assertEquals("\"e1\"", liveRequests[1].headers[HttpHeaders.IfNoneMatch])
        assertEquals("00000000-0000-4000-8000-000000000001", liveRequests[1].headers["X-Device-Id"])
        assertEquals(first.board, second.board)
        assertEquals(listOf("flg_a"), second.flags.map { it.id })
        assertEquals(now, second.receivedAt)
    }

    @Test
    fun `304 뒤에도 처음 ETag를 계속 보낸다`() = runTest {
        service.refresh("fst_a")
        live = { json("", HttpStatusCode.NotModified) }
        service.refresh("fst_a")
        service.refresh("fst_a")

        assertEquals("\"e1\"", liveRequests[2].headers[HttpHeaders.IfNoneMatch])
    }

    @Test
    fun `비로그인이면 ETag를 보내지 않고 304는 잘못된 응답이다`() = runTest {
        sessionId = null
        service.refresh("fst_a")
        service.refresh("fst_a")
        live = { json("", HttpStatusCode.NotModified) }

        val error = failure { service.refresh("fst_a") }

        assertTrue(liveRequests.all { it.headers[HttpHeaders.IfNoneMatch] == null })
        assertTrue(error is FestivalLiveBoardServiceError.InvalidResponse)
    }

    @Test
    fun `429면 Retry-After 동안 요청하지 않고 남은 시간을 알린다`() = runTest {
        live =
            {
                json(
                    """{"code":"RATE_LIMITED","data":null,"message":"x"}""",
                    HttpStatusCode.TooManyRequests,
                    HttpHeaders.RetryAfter to "20",
                )
            }

        val first = failure { service.refresh("fst_a") } as FestivalLiveBoardServiceError.RateLimited
        now = now.plusSeconds(5)
        val second = failure { service.refresh("fst_a") } as FestivalLiveBoardServiceError.RateLimited

        assertEquals(20.seconds, first.retryAfter)
        assertEquals(15.seconds, second.retryAfter)
        assertEquals(1, liveRequests.size)

        now = now.plusSeconds(15)
        live = { liveOk("\"e2\"", "2026-11-22T00:00:20Z") }
        service.refresh("fst_a")
        assertEquals(2, liveRequests.size)
    }

    @Test
    fun `Retry-After가 없거나 숫자가 아니면 35초, 1초보다 작으면 1초다`() {
        assertEquals(35.seconds, NetworkFestivalLiveBoardService.retryAfter(null))
        assertEquals(35.seconds, NetworkFestivalLiveBoardService.retryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(1.seconds, NetworkFestivalLiveBoardService.retryAfter("0"))
        assertEquals(12.seconds, NetworkFestivalLiveBoardService.retryAfter("12"))
    }

    @Test
    fun `FESTIVAL_NOT_FOUND면 미공개이고 그 축제 캐시를 지운다`() = runTest {
        service.refresh("fst_a")
        live = { json("""{"code":"FESTIVAL_NOT_FOUND","data":null,"message":"x"}""", HttpStatusCode.NotFound) }

        val error = failure { service.refresh("fst_a") }
        live = { liveOk("\"e3\"", "2026-11-22T00:01:00Z") }
        service.refresh("fst_a")

        assertTrue(error is FestivalLiveBoardServiceError.FestivalNotFound)
        assertNull(liveRequests[2].headers[HttpHeaders.IfNoneMatch])
    }

    @Test
    fun `세션이 바뀌면 ETag 캐시를 비운다`() = runTest {
        service.refresh("fst_a")
        sessionId = "session-2"

        service.refresh("fst_a")

        assertNull(liveRequests[1].headers[HttpHeaders.IfNoneMatch])
    }

    @Test
    fun `세션이 바뀌면 429 대기도 비운다`() = runTest {
        live = { json("{}", HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "60") }
        failure { service.refresh("fst_a") }
        sessionId = "session-2"
        live = { liveOk("\"e1\"", "2026-11-22T00:00:00Z") }

        service.refresh("fst_a")

        assertEquals(2, liveRequests.size)
    }

    @Test
    fun `응답을 받는 사이 세션이 바뀌면 잘못된 응답이고 캐시하지 않는다`() = runTest {
        onFlags = { sessionId = "session-2" }

        val error = failure { service.refresh("fst_a") }
        onFlags = {}
        service.refresh("fst_a")

        assertTrue(error is FestivalLiveBoardServiceError.InvalidResponse)
        assertNull(liveRequests[1].headers[HttpHeaders.IfNoneMatch])
    }

    @Test
    fun `다른 축제의 응답은 잘못된 응답이다`() = runTest {
        live = { liveOk("\"e1\"", "2026-11-22T00:00:00Z", festivalId = "fst_other") }

        assertTrue(failure { service.refresh("fst_a") } is FestivalLiveBoardServiceError.InvalidResponse)
    }

    @Test
    fun `잘못된 축제 ID는 요청하지 않는다`() = runTest {
        assertTrue(failure { service.refresh("fst/a") } is FestivalLiveBoardServiceError.InvalidFestivalId)
        assertTrue(failure { service.refresh("") } is FestivalLiveBoardServiceError.InvalidFestivalId)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `공식 소식이 다른 축제 것이면 잘못된 응답이다`() = runTest {
        val error = failure { NetworkFestivalOfficialNewsService(FestivalOfficialNewsRequest(client)).officialNews("fst_a") }

        assertTrue(error is FestivalLiveBoardServiceError.InvalidResponse)
    }

    @Test
    fun `상세 응답 ID가 다르거나 경로 문자가 있는 ID면 잘못된 응답이다`() = runTest {
        val catalog =
            NetworkFestivalCatalogService(
                FestivalListRequest(client),
                FestivalDetailRequest(client),
                FestivalCatalogCache(File("unused.json"), FestiCoreJson),
                FestivalImageUrlResolver(null),
            )

        assertTrue(failure { catalog.festivalDetail("fst_a") } is FestivalLiveBoardServiceError.InvalidResponse)
        assertTrue(failure { catalog.festivalDetail("fst?a") } is FestivalLiveBoardServiceError.InvalidResponse)
        assertEquals(1, requests.size)
        assertEquals("fst_other", catalog.festivalDetail("fst_other").id)
    }

    @Test
    fun `내 일정의 잘못된 축제 ID는 요청하지 않는다`() = runTest {
        assertTrue(
            failure {
                NetworkFestivalScheduleService(FestivalScheduleRequest(client)).mySchedule("fst a")
            } is FestivalLiveBoardServiceError.InvalidFestivalId,
        )
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `마지막 조회 결과를 그대로 돌려주고 받은 적이 없으면 null이다`() = runTest {
        assertNull(service.cachedFetch("fst_a"))

        val fetched = service.refresh("fst_a")
        live = { json("", HttpStatusCode.NotModified) }
        now = now.plusSeconds(35)
        val reused = service.refresh("fst_a")
        val requestCount = requests.size

        assertEquals(reused, service.cachedFetch("fst_a"))
        assertEquals(fetched.board, service.cachedFetch("fst_a")?.board)
        assertEquals(listOf("flg_a"), service.cachedFetch("fst_a")?.flags?.map { it.id })
        assertEquals(now, service.cachedFetch("fst_a")?.receivedAt)
        assertNull(service.cachedFetch("fst_other"))
        assertEquals(requestCount, requests.size)
    }

    @Test
    fun `비로그인 조회 결과도 돌려준다`() = runTest {
        sessionId = null
        val fetched = service.refresh("fst_a")

        assertEquals(fetched, service.cachedFetch("fst_a"))
    }

    @Test
    fun `세션이 바뀌면 이전 세션의 조회 결과를 돌려주지 않는다`() = runTest {
        service.refresh("fst_a")
        sessionId = "session-2"

        assertNull(service.cachedFetch("fst_a"))
        sessionId = "session-1"
        assertTrue(service.cachedFetch("fst_a") != null)

        sessionId = "session-2"
        service.refresh("fst_a")
        sessionId = "session-1"
        assertNull(service.cachedFetch("fst_a"))
    }

    @Test
    fun `미공개 응답을 받으면 그 축제의 조회 결과를 지운다`() = runTest {
        service.refresh("fst_a")
        live = { json("""{"code":"FESTIVAL_NOT_FOUND","data":null,"message":"x"}""", HttpStatusCode.NotFound) }

        failure { service.refresh("fst_a") }

        assertNull(service.cachedFetch("fst_a"))
    }
}
