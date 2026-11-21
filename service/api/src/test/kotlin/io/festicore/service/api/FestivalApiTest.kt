package io.festicore.service.api

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.NetworkError
import io.festicore.core.network.createFestiCoreHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalApiTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(body: String, status: HttpStatusCode = HttpStatusCode.OK) = FestivalApi(
        createFestiCoreHttpClient(
            baseUrl = "https://example.test/",
            engine = MockEngine { request ->
                requests += request
                respond(body, status, jsonHeaders)
            },
        ),
    )

    private fun stagePage(): String = checkNotNull(javaClass.getResource("/festival-list-stage-page.json")).readText()

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    private fun item(fields: String) = FestiCoreJson.decodeFromString(
        FestivalItemDto.serializer(),
        """{"id":"fst_a","name":"가짜 축제","packVersion":1$fields}""",
    )

    @Test
    fun `stage 응답 한 쪽을 읽고 모르는 필드는 무시한다`() = runTest {
        val page = api(stagePage()).list(after = null, limit = 2)

        assertEquals(2, page.items.size)
        assertEquals("2026-10-23~v2~2026-11-22~1~2026-11-29~fst_eacde26f05a55f27", page.nextCursor)
        val first = page.items.first()
        assertEquals("fst_00e0f4f1d8065a93", first.id)
        assertEquals("/api/v1/festivals/images/c3b583dc-d7a3-4bf5-a905-daaba4267336.webp", first.thumbUrl)
        assertEquals(56, first.packVersion)
        assertEquals("live", first.status)
        assertEquals(2, first.ticketLinks?.size)
        assertEquals("2026-11-20T12:00:00+09:00", first.artists?.first()?.performances?.first()?.startsAt)
    }

    @Test
    fun `첫 쪽은 after 없이 limit만 보내고 인증 헤더가 없다`() = runTest {
        api(stagePage()).list(after = null, limit = 10)

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/festivals", request.url.encodedPath)
        assertEquals("10", request.url.parameters["limit"])
        assertNull(request.url.parameters["after"])
        assertFalse(request.headers.contains(HttpHeaders.Authorization))
    }

    @Test
    fun `커서는 물결표를 포함해 그대로 after로 보낸다`() = runTest {
        val cursor = "2026-10-22~v2~2026-11-21~1~2026-12-18~fst_b"

        api(stagePage()).list(after = cursor, limit = 50)

        assertEquals(cursor, requests.single().url.parameters["after"])
        assertEquals("50", requests.single().url.parameters["limit"])
    }

    @Test
    fun `마지막 쪽은 nextCursor가 null이다`() = runTest {
        val page = api("""{"code":"SUCCESS","message":"ok","data":{"items":[],"nextCursor":null}}""").list(null, 10)

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun `잘못된 커서 400은 Http 오류다`() = runTest {
        val error = failure {
            api("""{"code":"VALIDATION","data":null,"message":"after는 YYYY-MM-DD여야 합니다."}""", HttpStatusCode.BadRequest)
                .list("bogus", 10)
        } as NetworkError.Http

        assertEquals(400, error.status)
        assertEquals("VALIDATION", error.code)
    }

    @Test
    fun `손상된 예매처 · 아티스트 · 공연 원소는 건너뛴다`() {
        val dto = item(
            ""","ticketLinks":[{"label":"가","url":"https://a.test"},{"label":1},null,"x"],""" +
                """"artists":[{"name":"A","performances":[{"startsAt":"s","date":"d"},{"date":"d"}]},{"performances":[]}]""",
        )

        assertEquals(listOf(FestivalTicketLinkDto("가", "https://a.test")), dto.ticketLinks)
        assertEquals(1, dto.artists?.size)
        assertEquals(listOf(FestivalCatalogPerformanceDto("s", "d")), dto.artists?.first()?.performances)
    }

    @Test
    fun `예매처가 배열이 아니면 빈 목록이고 공연이 없으면 빈 목록이다`() {
        val dto = item(""","ticketLinks":{"label":"가"},"artists":[{"name":"A"}]""")

        assertEquals(emptyList<FestivalTicketLinkDto>(), dto.ticketLinks)
        assertEquals(emptyList<FestivalCatalogPerformanceDto>(), dto.artists?.first()?.performances)
    }

    @Test
    fun `선택 필드가 없으면 null이고 아티스트 없음과 빈 배열을 구분한다`() {
        val missing = item("")
        val empty = item(""","artists":[]""")

        assertNull(missing.venue)
        assertNull(missing.thumbUrl)
        assertNull(missing.artists)
        assertEquals(emptyList<FestivalCatalogArtistDto>(), empty.artists)
    }

    @Test
    fun `packVersion이 없으면 해석 실패다`() = runTest {
        val error = failure {
            api("""{"code":"SUCCESS","message":"ok","data":{"items":[{"id":"fst_a","name":"가짜"}]}}""").list(null, 10)
        }

        assertTrue(error is NetworkError.Decoding)
    }

    @Test
    fun `저장 형태로 쓰고 다시 읽으면 같다`() {
        val dto =
            item(""","venue":"가짜 장소","ticketLinks":[{"label":"가","url":"https://a.test"}],"artists":[{"name":"A","performances":[]}]""")

        val json = FestiCoreJson.encodeToString(FestivalItemDto.serializer(), dto)

        assertEquals(dto, FestiCoreJson.decodeFromString(FestivalItemDto.serializer(), json))
    }
}
