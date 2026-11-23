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
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 크루 API 경로 · method · 본문 · 인증 속성 · 멱등 키. iOS `Service/APIEndpoint/Tests/CrewRequestTests.swift`.
 * 초대 미리보기 404는 stage GET 응답, 성공 응답은 iOS DTO 형태의 가짜 값이다.
 */
class CrewApiTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    private fun api(body: String = ok("null"), status: HttpStatusCode = HttpStatusCode.OK) = CrewApi(
        createFestiCoreHttpClient(
            baseUrl = "https://example.test/",
            engine = MockEngine { request ->
                requests += request
                respond(body, status, jsonHeaders)
            },
        ),
    )

    private fun ok(data: String) = """{"code":"SUCCESS","message":"OK","data":$data}"""

    private val crewOk = ok(
        """{"id":"crw_a","festivalId":"fst_a","name":"우리","ownerId":"usr_a",
        "members":[{"userId":"usr_a","nickname":"로이","joinedAt":"2026-11-23T01:02:03.456Z"}],
        "inviteCode":"ABCD23","inviteExpiresAt":"2026-11-25T01:02:03Z","archivedAt":null}""",
    )

    private val HttpRequestData.textBody: String get() = (body as TextContent).text

    private val HttpRequestData.bearer: BearerAuthorization? get() = attributes.getOrNull(BearerAuthorizationKey)

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `경로를 선언한다`() {
        assertEquals("api/v1/crews", CrewApiPath.CREWS)
        assertEquals("api/v1/crews/join", CrewApiPath.JOIN)
        assertEquals("api/v1/crews/crw_a", CrewApiPath.crew("crw_a"))
        assertEquals("api/v1/crews/crw_a/status", CrewApiPath.status("crw_a"))
        assertEquals("api/v1/crews/crw_a/invite/rotate", CrewApiPath.rotateInvite("crw_a"))
        assertEquals("api/v1/crews/crw_a/transfer", CrewApiPath.transfer("crw_a"))
        assertEquals("api/v1/crews/crw_a/members/usr_b", CrewApiPath.member("crw_a", "usr_b"))
        assertEquals("api/v1/crews/invite/ABCD23", CrewApiPath.invite("ABCD23"))
        assertEquals("api/v1/crews/crw_a/gather", CrewApiPath.gather("crw_a"))
        assertEquals("api/v1/crews/crw_a/gather/gth_a/response", CrewApiPath.gatherResponse("crw_a", "gth_a"))
    }

    @Test
    fun `생성은 Bearer POST이고 nickname을 보내지 않는다`() = runTest {
        val crew = api(crewOk).create(CrewCreateBody(festivalId = "fst_a", name = "우리"), expectedSessionId = "s1")

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/crews", request.url.encodedPath)
        assertEquals(BearerAuthorization("s1"), request.bearer)
        assertEquals("""{"festivalId":"fst_a","name":"우리"}""", request.textBody)
        assertNull(request.headers[IDEMPOTENCY_KEY_HEADER])
        assertEquals("crw_a", crew.id)
        assertEquals("2026-11-23T01:02:03.456Z", crew.members.single().joinedAt)
        assertNull(crew.live)
    }

    @Test
    fun `상세 · 이름 · 해산은 같은 경로의 GET · PATCH · DELETE다`() = runTest {
        api(crewOk).detail("crw_a", "s1")
        api(crewOk).rename("crw_a", CrewRenameBody("새 이름"), "s1")
        api().dissolve("crw_a", "s1")

        assertEquals(listOf(HttpMethod.Get, HttpMethod.Patch, HttpMethod.Delete), requests.map { it.method })
        assertTrue(requests.all { it.url.encodedPath == "/api/v1/crews/crw_a" })
        assertTrue(requests.all { it.bearer == BearerAuthorization("s1") })
        assertEquals("""{"name":"새 이름"}""", requests[1].textBody)
        assertTrue(requests.none { it.headers[IDEMPOTENCY_KEY_HEADER] != null })
    }

    @Test
    fun `상태는 Bearer POST이고 UUID 멱등 키를 요청마다 새로 붙인다`() = runTest {
        val api = api(ok("""{"userId":"usr_a","state":"here"}"""))
        val body = CrewStatusBody(zoneId = "zon_a", state = "here", clientAt = crewClientAt(Instant.parse("2026-11-23T01:02:03.456Z")))

        val status = api.status("crw_a", body, "s1")
        api.status("crw_a", body, "s1")

        val first = requests[0]
        assertEquals(HttpMethod.Post, first.method)
        assertEquals("/api/v1/crews/crw_a/status", first.url.encodedPath)
        assertEquals(BearerAuthorization("s1"), first.bearer)
        assertEquals("""{"zoneId":"zon_a","state":"here","clientAt":"2026-11-23T01:02:03.456Z"}""", first.textBody)
        val keys = requests.map { checkNotNull(it.headers[IDEMPOTENCY_KEY_HEADER]) }
        assertTrue(keys.all { uuid.matches(it) })
        assertNotEquals(keys[0], keys[1])
        assertEquals("here", status.state)
    }

    @Test
    fun `존 없는 상태는 zoneId를 빼고 보낸다`() = runTest {
        api(
            ok("""{"userId":"usr_a","state":"leaving"}"""),
        ).status("crw_a", CrewStatusBody(state = "leaving", clientAt = "2026-11-23T01:02:03.000Z"), "s1")

        assertEquals("""{"state":"leaving","clientAt":"2026-11-23T01:02:03.000Z"}""", requests.single().textBody)
    }

    @Test
    fun `clientAt은 밀리초가 있는 ISO UTC다`() {
        assertEquals("2026-11-23T01:02:03.000Z", crewClientAt(Instant.parse("2026-11-23T10:02:03+09:00")))
        assertEquals("2026-11-23T01:02:03.456Z", crewClientAt(Instant.parse("2026-11-23T01:02:03.456789Z")))
    }

    @Test
    fun `초대 재생성 · 이전 · 내보내기 · 참여 · 모여 · 모여 응답은 Bearer 요청이다`() = runTest {
        api(ok("""{"inviteCode":"ABCD23","inviteExpiresAt":"2026-11-25T01:02:03Z"}""")).rotateInvite("crw_a", "s1")
        api(crewOk).transfer("crw_a", CrewTransferBody("usr_b"), "s1")
        api().removeMember("crw_a", "usr_b", "s1")
        api(crewOk).join(CrewJoinBody("ABCD23"), "s1")
        api(ok("""{"id":"gth_a","zoneId":"zon_a"}""")).gather("crw_a", CrewGatherBody("zon_a"), "s1")
        api(
            ok("""{"gatherId":"gth_a","userId":"usr_a","nickname":"로이","response":"going","respondedAt":"2026-11-23T01:02:03Z"}"""),
        ).respondToGather("crw_a", "gth_a", CrewGatherResponseBody("going"), "s1")

        assertEquals(
            listOf(
                HttpMethod.Post to "/api/v1/crews/crw_a/invite/rotate",
                HttpMethod.Post to "/api/v1/crews/crw_a/transfer",
                HttpMethod.Delete to "/api/v1/crews/crw_a/members/usr_b",
                HttpMethod.Post to "/api/v1/crews/join",
                HttpMethod.Post to "/api/v1/crews/crw_a/gather",
                HttpMethod.Post to "/api/v1/crews/crw_a/gather/gth_a/response",
            ),
            requests.map { it.method to it.url.encodedPath },
        )
        assertTrue(requests.all { it.bearer == BearerAuthorization("s1") })
        assertTrue(requests.none { it.headers[IDEMPOTENCY_KEY_HEADER] != null })
        assertEquals("""{"toUserId":"usr_b"}""", requests[1].textBody)
        assertEquals("""{"inviteCode":"ABCD23"}""", requests[3].textBody)
        assertEquals("""{"zoneId":"zon_a"}""", requests[4].textBody)
        assertEquals("""{"response":"going"}""", requests[5].textBody)
    }

    @Test
    fun `초대 미리보기는 인증 없는 GET이다`() = runTest {
        val invite = api(
            ok(
                """{"crewName":"우리","festivalId":"fst_a","festivalName":null,"ownerNickname":"로이","memberCount":2,
                "expiresAt":"2026-11-25T01:02:03Z"}""",
            ),
        ).invite("ABCD23")

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/crews/invite/ABCD23", request.url.encodedPath)
        assertNull(request.bearer)
        assertEquals(2, invite.memberCount)
        assertNull(invite.memberPreview)
    }

    @Test
    fun `stage 초대 404는 상태 · 코드를 담은 Http 오류다`() = runTest {
        val stage = checkNotNull(javaClass.getResource("/crew-invite-not-found-stage.json")).readText()

        val error = failure { api(stage, HttpStatusCode.NotFound).invite("ZZZZZZ") }

        assertTrue(error is NetworkError.Http)
        error as NetworkError.Http
        assertEquals(404, error.status)
        assertEquals("NOT_FOUND", error.code)
    }

    @Test
    fun `Bearer 요청의 409 · 429는 Http 오류 그대로 던지고 재시도하지 않는다`() = runTest {
        val conflict =
            failure {
                api("""{"code":"GATHER_EXPIRED","message":"x"}""", HttpStatusCode.Conflict).gather("crw_a", CrewGatherBody("zon_a"), "s1")
            }
        val cooldown =
            failure {
                api("""{"code":"COOLDOWN","message":"x"}""", HttpStatusCode.TooManyRequests).gather("crw_a", CrewGatherBody("zon_a"), "s1")
            }

        assertEquals(409, (conflict as NetworkError.Http).status)
        assertEquals(429, (cooldown as NetworkError.Http).status)
        assertEquals(2, requests.size)
    }
}
