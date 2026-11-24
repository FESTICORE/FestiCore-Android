package io.festicore.domain.festival.sources.request

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.client.createFestiCoreHttpClient
import io.festicore.core.network.error.NetworkError
import io.festicore.domain.festival.sources.model.dto.FestivalEventPeriodDto
import io.festicore.domain.festival.sources.model.dto.FestivalFlagsDto
import io.festicore.domain.festival.sources.model.dto.FestivalLiveBoardDto
import io.festicore.domain.festival.sources.model.dto.FestivalOfficialNewsDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackDirectionsDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackDirectionsSectionDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackDocumentDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackSourceDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackZoneDto
import io.festicore.domain.festival.sources.model.dto.FestivalScheduleDto
import io.festicore.service.api.base.BearerAuthorization
import io.festicore.service.api.base.BearerAuthorizationKey
import io.festicore.service.apiendpoint.festival.FestivalLiveResponse
import io.festicore.service.apiendpoint.festival.FestivalRequest
import io.festicore.service.apiendpoint.festival.FestivalScheduleReplaceBody
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 상세 · live · flags · instagram · me/schedule 요청과 stage 응답(축약) 해석. */
class FestivalLiveBoardRequestTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val deviceId = "3f2b8c1e-0d4a-4c6b-9e7f-1a2b3c4d5e6f"

    private fun api(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        vararg headers: Pair<String, String>,
    ) = FestivalRequest(
        createFestiCoreHttpClient(
            baseUrl = "https://example.test/",
            engine = MockEngine { request ->
                requests += request
                val all = listOf(HttpHeaders.ContentType to "application/json") + headers
                respond(body, status, headersOf(*all.map { it.first to listOf(it.second) }.toTypedArray()))
            },
        ),
    )

    private fun resource(name: String): String = checkNotNull(javaClass.getResource("/$name")).readText()

    private fun success(data: String) = """{"code":"SUCCESS","message":"ok","data":$data}"""

    private fun document(fields: String) = FestiCoreJson.decodeFromString(FestivalPackDocumentDto.serializer(), """{"id":"fst_a"$fields}""")

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `stage 상세를 읽고 iOS가 읽지 않는 키는 무시한다`() = runTest {
        val detail = api(resource("festival-detail-stage.json")).detail<FestivalPackDocumentDto>("fst_00e0f4f1d8065a93")

        assertEquals("/api/v1/festivals/fst_00e0f4f1d8065a93", requests.single().url.encodedPath)
        assertFalse(requests.single().headers.contains(HttpHeaders.Authorization))
        assertEquals("fst_00e0f4f1d8065a93", detail.id)
        assertEquals("KINTEX HALL 7/8/9/10", detail.venue)
        assertEquals(FestivalEventPeriodDto("2026-11-20", "2026-11-22"), detail.eventPeriod)
        assertEquals(3, detail.days?.size)
        assertEquals("2026-11-20T12:00:00+09:00", detail.days?.first()?.startsAt)
        assertEquals(listOf("LIVET STAGE", "LIVET STAGE", "WONDER STAGE"), detail.timetable?.map { it.stageName })
        assertEquals("set_dcc99d63b2a2e102", detail.timetable?.first()?.id)
        assertEquals(listOf("LIVET STAGE", "WONDER STAGE"), detail.stages?.map { it.name })
        assertEquals(2, detail.zones?.size)
        assertEquals("2026-11-01T03:08:48Z", detail.source?.verifiedAt)
        assertEquals(true, detail.flagsEnabled)
        assertEquals(2, detail.ticketLinks?.size)
    }

    @Test
    fun `팩 문서의 기간 · 출처 · 메모는 형식이 틀리면 null이고 손상된 존만 버린다`() {
        val dto = document(
            ""","eventPeriod":{"firstDate":1},"source":{"name":3,"url":"https://a.test"},""" +
                """"zones":[{"id":"z1","name":"A"},{"id":"z2"},null],"sets":[{"id":"s1","note":5}]""",
        )

        assertNull(dto.eventPeriod)
        assertEquals(FestivalPackSourceDto(name = null, url = "https://a.test"), dto.source)
        assertEquals(listOf(FestivalPackZoneDto("z1", "A")), dto.zones)
        assertNull(dto.sets?.single()?.note)
    }

    @Test
    fun `팩 문서의 가는 길은 귀가 값만 읽고 형식이 틀리면 null이다`() {
        val dto = document(
            ""","directions":{"lastShuttleAt":"23:10","lastTrainAt":"2026-11-20T23:40:00+09:00",""" +
                """"shuttle":{"stopName":"정문","body":"x"},"transit":{"stationName":"잠실역","walkMinutes":12},"memo":"m"}""",
        )
        val broken = document(""","directions":{"transit":{"walkMinutes":"12분"}}""")

        assertEquals(
            FestivalPackDirectionsDto(
                lastShuttleAt = "23:10",
                lastTrainAt = "2026-11-20T23:40:00+09:00",
                shuttle = FestivalPackDirectionsSectionDto(stopName = "정문"),
                transit = FestivalPackDirectionsSectionDto(stationName = "잠실역", walkMinutes = 12),
            ),
            dto.directions,
        )
        assertNull(broken.directions)
    }

    @Test
    fun `팩 문서의 공연 원소가 틀리면 문서 전체가 해석 실패다`() = runTest {
        val error = failure {
            api(success("""{"id":"fst_a","sets":[{"id":"s1","artist":1}]}""")).detail<FestivalPackDocumentDto>("fst_a")
        }

        assertTrue(error is NetworkError.Decoding)
    }

    @Test
    fun `stage live를 X-Device-Id와 함께 받고 ETag를 돌려준다`() = runTest {
        val etag = "\"cdm1-fake\""
        val response = api(resource("festival-live-stage.json"), HttpStatusCode.OK, HttpHeaders.ETag to etag)
            .live(
                "fst_00e0f4f1d8065a93",
                deviceId,
                etag = null,
                expectedSessionId = null,
                FestivalLiveBoardDto.serializer(),
            ) as FestivalLiveResponse.Loaded

        val request = requests.single()
        assertEquals("/api/v1/festivals/fst_00e0f4f1d8065a93/live", request.url.encodedPath)
        assertEquals(deviceId, request.headers["X-Device-Id"])
        assertNull(request.headers[HttpHeaders.IfNoneMatch])
        assertNull(request.attributes.getOrNull(BearerAuthorizationKey))
        assertEquals(etag, response.etag)
        assertEquals(listOf("flg_4d2c186c24", "flg_31da303181", "flg_365ec2cf38"), response.board.activeFlags.map { it.flagId })
        assertEquals("resting", response.board.activeFlags[2].status)
        assertNull(response.board.me)
        assertEquals("2026-11-22T00:43:06Z", response.board.serverAt)
    }

    @Test
    fun `세션이 있으면 Bearer 표시와 If-None-Match를 붙이고 304는 NotModified다`() = runTest {
        val response = api(
            "",
            HttpStatusCode.NotModified,
        ).live("fst_a", deviceId, etag = "\"e1\"", expectedSessionId = "s1", FestivalLiveBoardDto.serializer())

        assertEquals(FestivalLiveResponse.NotModified, response)
        assertEquals("\"e1\"", requests.single().headers[HttpHeaders.IfNoneMatch])
        assertEquals(BearerAuthorization("s1"), requests.single().attributes[BearerAuthorizationKey])
    }

    @Test
    fun `429는 Retry-After 원문과 함께 RateLimited다`() = runTest {
        val response = api(
            """{"code":"RATE_LIMITED","data":null,"message":"잠시 후"}""",
            HttpStatusCode.TooManyRequests,
            HttpHeaders.RetryAfter to "12",
        ).live("fst_a", deviceId, null, null, FestivalLiveBoardDto.serializer())

        assertEquals(FestivalLiveResponse.RateLimited("12"), response)
    }

    @Test
    fun `본문 FESTIVAL_NOT_FOUND는 상태 코드와 관계없이 미공개다`() = runTest {
        val body = """{"code":"FESTIVAL_NOT_FOUND","data":null,"message":"공개된 페스티벌을 찾을 수 없습니다."}"""

        assertEquals(
            FestivalLiveResponse.FestivalNotFound,
            api(body, HttpStatusCode.NotFound).live("fst_a", deviceId, null, null, FestivalLiveBoardDto.serializer()),
        )
        assertEquals(
            FestivalLiveResponse.FestivalNotFound,
            api(body, HttpStatusCode.OK).live("fst_a", deviceId, null, null, FestivalLiveBoardDto.serializer()),
        )
    }

    @Test
    fun `그 밖의 실패 응답은 Http 오류다`() = runTest {
        val error = failure {
            api("""{"code":"VALIDATION","data":null,"message":"X-Device-Id UUID가 필요합니다."}""", HttpStatusCode.BadRequest)
                .live("fst_a", "", null, null, FestivalLiveBoardDto.serializer())
        } as NetworkError.Http

        assertEquals(400, error.status)
        assertEquals("VALIDATION", error.code)
    }

    @Test
    fun `stage 깃발 프로필을 읽고 photoUrl · bio · planVisible은 무시한다`() = runTest {
        val flags = api(resource("festival-flags-stage.json")).flags<FestivalFlagsDto>("fst_00e0f4f1d8065a93")

        assertEquals("/api/v1/festivals/fst_00e0f4f1d8065a93/flags", requests.single().url.encodedPath)
        assertEquals(3, flags.items.size)
        val first = flags.items.first()
        assertEquals("호락호락", first.name)
        assertEquals(listOf("slam"), first.tags)
        assertEquals(emptyList<String>(), first.plan)
        assertEquals("zon_rivet_front_left", first.homeZoneId)
    }

    @Test
    fun `깃발 태그는 문자열만 남기고 형식이 틀린 계획은 null이다`() {
        val dto = FestiCoreJson.decodeFromString(
            FestivalFlagsDto.serializer(),
            """{"items":[{"id":"a","name":"A","tags":["slam",1,null,"sing"],"plan":"wrong"},{"id":"b","name":"B","plan":["s1",2]}]}""",
        )

        assertEquals(listOf("slam", "sing"), dto.items[0].tags)
        assertNull(dto.items[0].plan)
        assertNull(dto.items[1].plan)
    }

    @Test
    fun `stage 공식 인스타그램을 읽는다`() = runTest {
        val news = api(resource("festival-instagram-stage.json")).instagram<FestivalOfficialNewsDto>("fst_00e0f4f1d8065a93")

        assertEquals("/api/v1/festivals/fst_00e0f4f1d8065a93/instagram", requests.single().url.encodedPath)
        assertFalse(news.available)
        assertEquals("wonderlivet", news.account?.username)
        assertNull(news.fetchedAt)
        assertTrue(news.posts.isEmpty())
    }

    @Test
    fun `내 일정은 Bearer 대상 GET이다`() = runTest {
        val schedule = api(success("""{"setIds":["set_a","set_b"]}""")).mySchedule<FestivalScheduleDto>("fst_a")

        val request = requests.single()
        assertEquals("/api/v1/me/schedule/fst_a", request.url.encodedPath)
        assertEquals(BearerAuthorization(), request.attributes[BearerAuthorizationKey])
        assertEquals(listOf("set_a", "set_b"), schedule.setIds)
    }

    @Test
    fun `내 일정 저장은 세션에 묶인 Bearer PUT이고 본문은 setIds · clientAt이다`() = runTest {
        val body = FestivalScheduleReplaceBody(listOf("set_1"), "2026-08-07T10:00:00.250+09:00")

        val schedule = api(
            success("""{"setIds":["set_1"]}"""),
        ).replaceMySchedule<FestivalScheduleDto>("fst_a", body, expectedSessionId = "session-1")

        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/v1/me/schedule/fst_a", request.url.encodedPath)
        assertEquals(BearerAuthorization("session-1"), request.attributes[BearerAuthorizationKey])
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
        assertEquals("""{"setIds":["set_1"],"clientAt":"2026-08-07T10:00:00.250+09:00"}""", (request.body as TextContent).text)
        assertEquals(listOf("set_1"), schedule.setIds)
    }

    @Test
    fun `내 일정 저장 409는 상태 코드와 본문 code를 그대로 던진다`() = runTest {
        val error =
            failure {
                api("""{"code":"STATUS_STALE","data":null,"message":"stale"}""", HttpStatusCode.Conflict)
                    .replaceMySchedule<FestivalScheduleDto>(
                        "fst_a",
                        FestivalScheduleReplaceBody(emptyList(), "2026-08-07T10:00:00.250+09:00"),
                    )
            } as NetworkError.Http

        assertEquals(409, error.status)
        assertEquals("STATUS_STALE", error.code)
    }
}
