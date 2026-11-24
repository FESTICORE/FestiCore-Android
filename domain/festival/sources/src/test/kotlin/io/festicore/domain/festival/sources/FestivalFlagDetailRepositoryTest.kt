package io.festicore.domain.festival.sources
import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.client.createFestiCoreHttpClient
import io.festicore.domain.festival.entity.FestivalFlagDetail
import io.festicore.domain.festival.entity.FestivalFlagDetailError
import io.festicore.domain.festival.entity.FestivalFlagPinError
import io.festicore.domain.festival.entity.FestivalFlagReportError
import io.festicore.domain.festival.entity.FestivalFlagReportReason
import io.festicore.domain.festival.sources.flag.FestivalFlagDetailUseCaseImpl
import io.festicore.domain.festival.sources.repository.FestivalFlagDetailRepositoryImpl
import io.festicore.service.api.base.BearerAuthorization
import io.festicore.service.api.base.BearerAuthorizationKey
import io.festicore.service.apiendpoint.flag.FlagRequest
import io.festicore.service.festivalcatalog.NetworkFestivalCatalogService
import io.festicore.service.festivalcatalog.endpoint.FestivalDetailRequest
import io.festicore.service.festivalcatalog.endpoint.FestivalListRequest
import io.festicore.service.festivalcatalog.storage.FestivalCatalogCache
import io.festicore.service.festivalcatalog.url.FestivalImageUrlResolver
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 깃발 상세 · 신고 · 대표 깃발 UseCase → Repository → 서비스 → API를 Ktor `MockEngine`으로 잇는다.
 * iOS `Domain/FestivalDomain/Tests/Sources/FestivalFlagDetailRepositoryTests.swift`. 응답은 iOS DTO 형태의 가짜 값이다.
 */
class FestivalFlagDetailRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var response: Pair<HttpStatusCode, String> = ok(detailJson())
    private var sessionId: String? = null

    private val client = createFestiCoreHttpClient(
        baseUrl = "https://example.test/",
        engine = MockEngine { request ->
            requests += request
            respond(response.second, response.first, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )
    private val useCase = FestivalFlagDetailUseCaseImpl(
        FestivalFlagDetailRepositoryImpl(
            FlagRequest(client),
            NetworkFestivalCatalogService(
                FestivalListRequest(client),
                FestivalDetailRequest(client),
                FestivalCatalogCache(File("unused.json"), FestiCoreJson),
                FestivalImageUrlResolver("https://example.test/"),
            ),
            currentSessionId = { sessionId },
        ),
    )

    private fun ok(data: String) = HttpStatusCode.OK to """{"code":"SUCCESS","message":"OK","data":$data}"""

    private fun fail(status: HttpStatusCode, code: String) = status to """{"code":"$code","message":"실패"}"""

    private fun detailJson(
        id: String = "flg_a",
        status: String = "active",
        viewer: String = "",
        tags: String = """["slam",null]""",
    ) = """
        {"id":"$id","name":"호락호락","thumbUrl":"/api/v1/festivals/images/a.webp","photoUrl":null,"tags":$tags,"bio":null,
        "followerCount":-1,"planVisible":true,"plan":["set_1",null],"homeZoneId":"zon_a",
        "status":{"flagId":"flg_a","status":"$status","zoneId":"zon_a","activity":"dance","updatedAt":"2026-11-08T10:41:39.481Z"},
        "hidden":false$viewer}
    """.trimIndent()

    private val HttpRequestData.textBody: String get() = (body as TextContent).text

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `로그인 응답을 도메인 값으로 바꾸고 공개 상세 요청을 보낸다`() = runTest {
        sessionId = "session-1"
        response = ok(detailJson(viewer = ""","isFollowing":true,"isBearer":false,"isPinned":true"""))

        val detail = useCase.detail("flg_a")

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/flags/flg_a", request.url.encodedPath)
        assertEquals(BearerAuthorization("session-1"), request.attributes[BearerAuthorizationKey])
        assertEquals(true, detail.isFollowing)
        assertEquals(false, detail.isBearer)
        assertEquals(true, detail.isPinned)
        assertEquals("https://example.test/api/v1/festivals/images/a.webp", detail.thumbnailUrl)
        assertEquals(FestivalFlagDetail.Status.Active, detail.status)
        assertEquals(Instant.parse("2026-11-08T10:41:39.481Z"), detail.statusUpdatedAt)
    }

    @Test
    fun `익명 응답은 팔로우 · 기수 여부를 비우고 빈 값과 모르는 원소를 견딘다`() = runTest {
        val detail = useCase.detail("flg_a")

        assertNull(requests.single().attributes.getOrNull(BearerAuthorizationKey))
        assertNull(detail.isFollowing)
        assertNull(detail.isBearer)
        assertNull(detail.isPinned)
        assertEquals(listOf("slam"), detail.tags)
        assertEquals(listOf("set_1"), detail.planSetIds)
        assertEquals(0, detail.followerCount)
        assertNull(detail.activity)
        assertNull(detail.photoUrl)
    }

    @Test
    fun `모르는 상태, 다른 깃발 ID, 실패 코드, 필수 값 누락은 응답 오류로 막는다`() = runTest {
        val cases = listOf(
            ok(detailJson(status = "paused")),
            ok(detailJson(id = "flg_b")),
            HttpStatusCode.OK to """{"code":"FAIL","message":"실패","data":null}""",
            ok("""{"id":"flg_a"}"""),
        )
        for (case in cases) {
            response = case
            assertTrue(failure { useCase.detail("flg_a") } is FestivalFlagDetailError.InvalidResponse)
        }
    }

    @Test
    fun `네트워크 오류를 상세 오류로 바꾼다`() = runTest {
        response = fail(HttpStatusCode.NotFound, "FLAG_NOT_FOUND")
        assertTrue(failure { useCase.detail("flg_a") } is FestivalFlagDetailError.NotFound)

        response = fail(HttpStatusCode.TooManyRequests, "RATE_LIMITED")
        assertTrue(failure { useCase.detail("flg_a") } is FestivalFlagDetailError.Unavailable)
    }

    @Test
    fun `경로에 넣을 수 없는 ID는 요청하지 않는다`() = runTest {
        assertTrue(failure { useCase.detail("flg/../a") } is FestivalFlagDetailError.NotFound)
        assertTrue(failure { useCase.report("", FestivalFlagReportReason.Fake) } is FestivalFlagReportError.InvalidRequest)
        assertTrue(failure { useCase.setPinnedFlag("a".repeat(65)) } is FestivalFlagPinError.InvalidRequest)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `신고는 사유를 담아 로그인 토큰으로 보낸다`() = runTest {
        sessionId = "session-1"
        response = ok("""{"reportId":"rpt_1"}""")

        useCase.report("flg_a", FestivalFlagReportReason.Inappropriate)

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/flags/flg_a/report", request.url.encodedPath)
        assertEquals("""{"reason":"inappropriate"}""", request.textBody)
        assertEquals(BearerAuthorization("session-1"), request.attributes[BearerAuthorizationKey])
    }

    @Test
    fun `신고 실패를 로그인 · 약관 · 중복 · 없는 깃발 · 일시 오류로 나눈다`() = runTest {
        val cases = listOf(
            fail(HttpStatusCode.Unauthorized, "UNAUTHORIZED") to FestivalFlagReportError.LoginRequired::class,
            fail(HttpStatusCode.Forbidden, "TERMS_REQUIRED") to FestivalFlagReportError.TermsRequired::class,
            fail(HttpStatusCode.Conflict, "ALREADY_REPORTED") to FestivalFlagReportError.AlreadyReported::class,
            fail(HttpStatusCode.NotFound, "FLAG_NOT_FOUND") to FestivalFlagReportError.NotFound::class,
            fail(HttpStatusCode.TooManyRequests, "RATE_LIMITED") to FestivalFlagReportError.Unavailable::class,
            ok("""{"reportId":""}""") to FestivalFlagReportError.InvalidResponse::class,
        )
        for ((case, expected) in cases) {
            response = case
            assertEquals(expected, failure { useCase.report("flg_a", FestivalFlagReportReason.Other) }?.let { it::class })
        }
    }

    @Test
    fun `대표 깃발 지정 · 해제는 PUT으로 보내고 서버가 저장한 값을 돌려준다`() = runTest {
        response = ok("""{"pinnedFlagId":"flg_a"}""")
        assertEquals("flg_a", useCase.setPinnedFlag("flg_a"))

        response = ok("""{"pinnedFlagId":null}""")
        assertNull(useCase.setPinnedFlag(null))

        assertEquals(listOf(HttpMethod.Put, HttpMethod.Put), requests.map { it.method })
        assertEquals("/api/v1/me/pinned-flag", requests[1].url.encodedPath)
        assertEquals("""{"flagId":"flg_a"}""", requests[0].textBody)
        assertEquals("""{"flagId":null}""", requests[1].textBody)
    }

    @Test
    fun `대표 깃발 응답이 요청과 다르거나 값이 빠지면 형식 오류, 팔로우하지 않은 깃발은 따로 알린다`() = runTest {
        val cases = listOf(
            ok("""{"pinnedFlagId":"flg_b"}""") to FestivalFlagPinError.InvalidResponse::class,
            ok("{}") to FestivalFlagPinError.InvalidResponse::class,
            fail(HttpStatusCode.Conflict, "PIN_NOT_FOLLOWING") to FestivalFlagPinError.NotFollowing::class,
            fail(HttpStatusCode.Unauthorized, "UNAUTHORIZED") to FestivalFlagPinError.LoginRequired::class,
            fail(HttpStatusCode.Forbidden, "TERMS_REQUIRED") to FestivalFlagPinError.TermsRequired::class,
            fail(HttpStatusCode.TooManyRequests, "RATE_LIMITED") to FestivalFlagPinError.Unavailable::class,
        )
        for ((case, expected) in cases) {
            response = case
            assertEquals(expected, failure { useCase.setPinnedFlag("flg_a") }?.let { it::class })
        }
    }
}
