package io.festicore.service.festivalcatalog

import io.festicore.core.network.NetworkError
import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.domain.festival.sources.service.FestivalLiveBoardServiceError
import io.festicore.domain.festival.sources.service.FestivalScheduleServiceError
import io.festicore.service.api.base.BearerAuthorization
import io.festicore.service.api.base.BearerAuthorizationKey
import io.festicore.service.apiendpoint.festival.FestivalRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 내 일정 저장(PUT) · 409 다시 읽기. iOS `Service/FestiCoreFestivalCatalog/Tests/FestivalScheduleTests.swift`. 응답 본문은 iOS DTO 형태의 가짜 값이다. */
class FestivalScheduleServiceTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var put: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"code":"SUCCESS","message":"OK","data":{"setIds":["set_1"]}}"""
    private var get: Pair<HttpStatusCode, String> =
        HttpStatusCode.OK to """{"code":"SUCCESS","message":"OK","data":{"setIds":["set_2"],"updatedAt":null}}"""
    private var sessionId: String? = "session-1"

    private val service =
        NetworkFestivalScheduleService(
            api = FestivalRequest(
                createFestiCoreHttpClient(
                    baseUrl = "https://example.test/",
                    engine = MockEngine { request ->
                        requests += request
                        val (status, body) = if (request.method == HttpMethod.Put) put else get
                        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ),
            ),
            currentSessionId = { sessionId },
        )

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun replaceSendsWholeScheduleWithMillisecondClientAt() = runTest {
        // 2026-08-07 10:00:00.250 KST
        val clientAt = Instant.ofEpochMilli(1_786_064_400_250)

        val schedule = service.replace("fst_a", listOf("set_1"), clientAt)

        assertEquals(listOf("set_1"), schedule.setIds)
        val request = requests.single()
        assertEquals("PUT /api/v1/me/schedule/fst_a", "${request.method.value} ${request.url.encodedPath}")
        assertEquals(BearerAuthorization("session-1"), request.attributes[BearerAuthorizationKey])
        assertEquals("""{"setIds":["set_1"],"clientAt":"2026-08-07T10:00:00.250+09:00"}""", (request.body as TextContent).text)
    }

    @Test
    fun `clientAt은 소수 3자리를 항상 쓰고 서울 오프셋으로 적는다`() {
        assertEquals("2026-08-07T10:00:00.000+09:00", NetworkFestivalScheduleService.clientAtString(Instant.parse("2026-08-07T01:00:00Z")))
        assertEquals(
            "2026-11-23T00:01:26.123+09:00",
            NetworkFestivalScheduleService.clientAtString(Instant.parse("2026-11-22T15:01:26.123456Z")),
        )
    }

    @Test
    fun staleReplaceReturnsCurrentServerSchedule() = runTest {
        put = HttpStatusCode.Conflict to """{"code":"STATUS_STALE","data":null,"message":"더 최신 일정이 있습니다."}"""

        val error = failure { service.replace("fst_a", listOf("set_1"), Instant.now()) }

        assertEquals(listOf("set_2"), (error as FestivalScheduleServiceError.Stale).schedule.setIds)
        assertEquals(listOf("PUT", "GET"), requests.map { it.method.value })
        assertTrue(requests.all { it.url.encodedPath == "/api/v1/me/schedule/fst_a" })
        // 다시 읽기도 PUT과 같은 세션에 묶는다.
        assertEquals(BearerAuthorization("session-1"), requests[1].attributes[BearerAuthorizationKey])
    }

    @Test
    fun `STATUS_STALE이 아닌 409와 다시 읽기 실패는 그 오류를 그대로 던진다`() = runTest {
        put = HttpStatusCode.Conflict to """{"code":"CONFLICT","data":null,"message":"x"}"""
        assertEquals(409, (failure { service.replace("fst_a", emptyList(), Instant.now()) } as NetworkError.Http).status)
        assertEquals(1, requests.size)

        requests.clear()
        put = HttpStatusCode.Conflict to """{"code":"STATUS_STALE","data":null,"message":"x"}"""
        get = HttpStatusCode.ServiceUnavailable to """{"code":"UNAVAILABLE","data":null,"message":"x"}"""
        assertEquals(503, (failure { service.replace("fst_a", emptyList(), Instant.now()) } as NetworkError.Http).status)
        assertEquals(2, requests.size)
    }

    @Test
    fun invalidFestivalIdIsRejectedWithoutRequest() = runTest {
        assertTrue(
            failure {
                service.replace("../admin", listOf("set_1"), Instant.now())
            } is FestivalLiveBoardServiceError.InvalidFestivalId,
        )
        assertTrue(failure { service.replace("", listOf("set_1"), Instant.now()) } is FestivalLiveBoardServiceError.InvalidFestivalId)
        assertTrue(requests.isEmpty())
    }
}
