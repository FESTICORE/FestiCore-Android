package io.festicore.service.api

import io.festicore.core.network.NetworkError
import io.festicore.core.network.createFestiCoreHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 깃발 API 경로 · method · 본문 · 인증 속성. iOS `Service/API/Tests/FlagAPITests.swift` · `APIEndpoint/Tests/FlagRequestTests.swift`.
 * 상세는 stage 익명 GET 응답, 나머지 로그인 응답은 iOS DTO 형태의 가짜 값이다.
 */
class FlagApiTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    private fun api(body: String, status: HttpStatusCode = HttpStatusCode.OK) = FlagApi(
        createFestiCoreHttpClient(
            baseUrl = "https://example.test/",
            engine = MockEngine { request ->
                requests += request
                respond(body, status, jsonHeaders)
            },
        ),
    )

    private fun ok(data: String) = """{"code":"SUCCESS","message":"OK","data":$data}"""

    private val followOk = ok("""{"flagId":"flg_a","following":true,"followerCount":3}""")

    private fun stageDetail(): String = checkNotNull(javaClass.getResource("/flag-detail-stage.json")).readText()

    private val HttpRequestData.textBody: String get() = (body as TextContent).text

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `경로를 선언한다`() {
        assertEquals("api/v1/flags/flg_a", FlagApiPath.detail("flg_a"))
        assertEquals("api/v1/flags/flg_a/follow", FlagApiPath.follow("flg_a"))
        assertEquals("api/v1/flags/flg_a/report", FlagApiPath.report("flg_a"))
        assertEquals("api/v1/me/pinned-flag", FlagApiPath.PINNED_FLAG)
    }

    @Test
    fun `익명 상세는 인증 없는 GET이고 팔로우 · 기수 · 대표 여부를 null로 둔다`() = runTest {
        val detail = api(stageDetail()).detail("flg_3c6c2661ba")

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/flags/flg_3c6c2661ba", request.url.encodedPath)
        assertNull(request.attributes.getOrNull(BearerAuthorizationKey))
        assertEquals("flg_3c6c2661ba", detail.id)
        assertEquals(listOf("slam"), detail.tags)
        assertEquals("active", detail.status.status)
        assertEquals("2026-11-08T10:41:39.481Z", detail.status.updatedAt)
        assertNull(detail.isFollowing)
        assertNull(detail.isBearer)
        assertNull(detail.isPinned)
        assertFalse(detail.hidden)
    }

    @Test
    fun `로그인 상세는 그 세션의 Bearer를 붙인다`() = runTest {
        api(stageDetail()).detail("flg_3c6c2661ba", expectedSessionId = "s1")

        assertEquals(BearerAuthorization("s1"), requests.single().attributes[BearerAuthorizationKey])
    }

    @Test
    fun `팔로우는 Bearer PUT이고 UUID 멱등 키를 요청마다 새로 붙인다`() = runTest {
        val api = api(followOk)

        val result = api.follow("flg_a", expectedSessionId = "s1")
        api.follow("flg_a", expectedSessionId = "s1")

        assertEquals(FlagFollowDto("flg_a", true, 3), result)
        val first = requests[0]
        assertEquals(HttpMethod.Put, first.method)
        assertEquals("/api/v1/flags/flg_a/follow", first.url.encodedPath)
        assertEquals(BearerAuthorization("s1"), first.attributes[BearerAuthorizationKey])
        val firstKey = checkNotNull(first.headers[IDEMPOTENCY_KEY_HEADER])
        assertTrue(uuid.matches(firstKey))
        assertNotEquals(firstKey, requests[1].headers[IDEMPOTENCY_KEY_HEADER])
    }

    @Test
    fun `언팔로우는 Bearer DELETE이고 멱등 키가 없다`() = runTest {
        api(ok("""{"flagId":"flg_a","following":false,"followerCount":2}""")).unfollow("flg_a")

        val request = requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/v1/flags/flg_a/follow", request.url.encodedPath)
        assertEquals(BearerAuthorization(), request.attributes[BearerAuthorizationKey])
        assertFalse(request.headers.contains(IDEMPOTENCY_KEY_HEADER))
    }

    @Test
    fun `신고는 사유만 담은 Bearer POST다`() = runTest {
        val result = api(ok("""{"reportId":"rpt_1"}""")).report("flg_a", FlagReportBody("fake"))

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/flags/flg_a/report", request.url.encodedPath)
        assertEquals("""{"reason":"fake"}""", request.textBody)
        assertEquals(BearerAuthorization(), request.attributes[BearerAuthorizationKey])
        assertEquals("rpt_1", result.reportId)
    }

    @Test
    fun `대표 깃발 지정은 PUT이고 해제할 때 flagId null을 명시한다`() = runTest {
        val set = api(ok("""{"pinnedFlagId":"flg_a"}""")).setPinnedFlag(PinnedFlagBody("flg_a"))
        val cleared = api(ok("""{"pinnedFlagId":null}""")).setPinnedFlag(PinnedFlagBody(null))

        assertEquals(HttpMethod.Put, requests[0].method)
        assertEquals("/api/v1/me/pinned-flag", requests[0].url.encodedPath)
        assertEquals("""{"flagId":"flg_a"}""", requests[0].textBody)
        assertEquals("""{"flagId":null}""", requests[1].textBody)
        assertEquals(BearerAuthorization(), requests[1].attributes[BearerAuthorizationKey])
        assertEquals("flg_a", set.pinnedFlagId)
        assertNull(cleared.pinnedFlagId)
    }

    @Test
    fun `대표 깃발 응답에 pinnedFlagId 키가 없으면 해석 실패다`() = runTest {
        val error = failure { api(ok("{}")).setPinnedFlag(PinnedFlagBody(null)) }

        assertTrue(error is NetworkError.Decoding)
    }

    @Test
    fun `실패 응답은 상태 코드와 code를 담은 Http 오류다`() = runTest {
        val error = failure {
            api("""{"code":"FOLLOW_LIMIT","message":"한도"}""", HttpStatusCode.Conflict).follow("flg_a")
        }

        assertEquals(409, (error as NetworkError.Http).status)
        assertEquals("FOLLOW_LIMIT", error.code)
    }
}
