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

    @Test
    fun `팩 버전은 message 없는 stage 응답을 읽고 인증 헤더 없이 보낸다`() = runTest {
        val body =
            """{"code":"SUCCESS","data":{"packVersion":56,"packUpdatedAt":"2026-11-12T15:01:26Z","etag":"\"b5fe\"",""" +
                """"mapVersion":"zm-7fc315a33960"}}"""

        val version = api(body).packVersion("fst_00e0f4f1d8065a93")

        assertEquals(FestivalPackVersionDto(56, "zm-7fc315a33960"), version)
        assertEquals("/api/v1/festivals/fst_00e0f4f1d8065a93/pack/version", requests.single().url.encodedPath)
        assertEquals(HttpMethod.Get, requests.single().method)
        assertNull(requests.single().headers[HttpHeaders.Authorization])
        assertNull(api("""{"code":"SUCCESS","data":{"packVersion":1}}""").packVersion("fst_a").mapVersion)
    }

    @Test
    fun `팩 버전 code가 SUCCESS가 아니면 InvalidResponse, 2xx가 아니면 Http다`() = runTest {
        assertTrue(failure { api("""{"code":"FAIL","data":null}""").packVersion("fst_a") } is NetworkError.InvalidResponse)
        val http = failure { api("""{"code":"NOT_FOUND","message":"x"}""", HttpStatusCode.NotFound).packVersion("fst_a") }
        assertEquals("NOT_FOUND", (http as NetworkError.Http).code)
    }

    @Test
    fun `팩 문서의 지도 값은 형식이 틀리면 null이고 무대 머리글은 손상 원소만 버린다`() {
        val document = FestiCoreJson.decodeFromString(
            FestivalPackDocumentDto.serializer(),
            """{"id":"fst_a","mapImageUrl":3,"mapAspect":"wide","mapVersion":"zm-1",
            "mapStages":[{"stageId":"stg_a","x":0.1,"y":0.02,"w":0.4},{"stageId":"stg_b"},null],
            "zones":[{"id":"zon_a","name":"A","kind":"무대 앞","order":1,"area":{"x":0.1,"y":0.2,"w":0.3,"h":0.4},"landmark":"GATE"},
                     {"id":"zon_b","name":"B","order":"first","area":{"x":"left"}}]}""",
        )

        assertNull(document.mapImageUrl)
        assertNull(document.mapAspect)
        assertEquals("zm-1", document.mapVersion)
        assertEquals(listOf(FestivalPackMapStageDto("stg_a", 0.1, 0.02, 0.4)), document.mapStages)
        assertEquals(FestivalPackZoneAreaDto(0.1, 0.2, 0.3, 0.4), document.zones?.get(0)?.area)
        assertEquals("GATE", document.zones?.get(0)?.landmark)
        // 지도 값이 틀린 존도 이름은 남긴다(보드 존 이름 찾기).
        assertEquals("B", document.zones?.get(1)?.name)
        assertNull(document.zones?.get(1)?.order)
        assertNull(document.zones?.get(1)?.area)
    }
}
