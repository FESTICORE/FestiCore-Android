package io.festicore.domain.festival.sources

import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.domain.festival.FestivalCrewError
import io.festicore.domain.festival.FestivalCrewGatherResponse
import io.festicore.domain.festival.FestivalCrewState
import io.festicore.service.api.base.BearerAuthorization
import io.festicore.service.api.base.BearerAuthorizationKey
import io.festicore.service.apiendpoint.common.IDEMPOTENCY_KEY_HEADER
import io.festicore.service.apiendpoint.crew.CrewRequest
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 크루 UseCase → Repository → 서비스 → API를 Ktor `MockEngine`으로 잇는다. 응답은 iOS DTO 형태의 가짜 값이다.
 * iOS `Domain/FestivalDomain/Tests/Sources/FestivalCrewRepositoryTests.swift` · `FestivalCrewRepositoryTestDefaults.swift`.
 */
class FestivalCrewRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var response: Pair<HttpStatusCode, String> = ok(crewJson())
    private var sessionId: String? = "s1"
    private var userId: String? = "usr_b"

    private val client = createFestiCoreHttpClient(
        baseUrl = "https://example.test/",
        engine = MockEngine { request ->
            requests += request
            respond(response.second, response.first, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )
    private val useCase = FestivalCrewUseCaseImpl(
        FestivalCrewRepositoryImpl(
            CrewRequest(client),
            currentSessionId = { sessionId },
            now = { Instant.parse("2026-11-23T01:02:03.456Z") },
            currentUserId = { userId },
        ),
    )

    private fun ok(data: String) = HttpStatusCode.OK to """{"code":"SUCCESS","message":"OK","data":$data}"""

    private fun fail(status: HttpStatusCode, code: String) = status to """{"code":"$code","message":"실패"}"""

    private fun crewJson(
        id: String = "crw_a",
        ownerId: String = "usr_a",
        live: String = "",
        joinedAt: String = "2026-11-23T01:02:03.456Z",
    ) = """
        {"id":"$id","festivalId":"fst_a","name":"우리","ownerId":"$ownerId",
        "members":[{"userId":"usr_a","nickname":"로이","joinedAt":"$joinedAt"}],
        "inviteCode":"ABCD23","inviteExpiresAt":"2026-11-25T01:02:03Z","archivedAt":null$live}
    """.trimIndent()

    private val HttpRequestData.textBody: String get() = (body as TextContent).text

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `상세는 세션 Bearer로 받아 크루원과 live를 엔티티로 바꾼다`() = runTest {
        val member = """{"userId":"usr_a","nickname":"로이","state":"here","zoneId":"zon_a"}"""
        response = ok(crewJson(live = ""","live":{"id":"crw_a","name":"우리","members":[$member]}"""))

        val crew = useCase.detail("crw_a")

        assertEquals(BearerAuthorization("s1"), requests.single().attributes[BearerAuthorizationKey])
        assertEquals(Instant.parse("2026-11-23T01:02:03.456Z"), crew.members.single().joinedAt)
        assertEquals(Instant.parse("2026-11-25T01:02:03Z"), crew.inviteExpiresAt)
        assertEquals("zon_a", crew.live?.members?.single()?.zoneId)
        assertNull(crew.archivedAt)
    }

    @Test
    fun `요청과 다른 크루 · 다른 live · 틀린 날짜 · SUCCESS 아닌 코드는 InvalidResponse다`() = runTest {
        response = ok(crewJson(id = "crw_b"))
        assertTrue(failure { useCase.detail("crw_a") } is FestivalCrewError.InvalidResponse)
        response = ok(crewJson(live = ""","live":{"id":"crw_b","name":"우리","members":[]}"""))
        assertTrue(failure { useCase.detail("crw_a") } is FestivalCrewError.InvalidResponse)
        response = ok(crewJson(joinedAt = "어제"))
        assertTrue(failure { useCase.detail("crw_a") } is FestivalCrewError.InvalidResponse)
        response = HttpStatusCode.OK to """{"code":"FAIL","message":"x","data":null}"""
        assertTrue(failure { useCase.detail("crw_a") } is FestivalCrewError.InvalidResponse)
        response = ok(crewJson(ownerId = "usr_a"))
        assertTrue(failure { useCase.transfer("crw_a", "usr_b") } is FestivalCrewError.InvalidResponse)
    }

    @Test
    fun `세션이 없으면 요청하지 않고 Unauthenticated다`() = runTest {
        sessionId = null

        assertTrue(failure { useCase.detail("crw_a") } is FestivalCrewError.Unauthenticated)
        assertTrue(failure { useCase.create("fst_a", "우리") } is FestivalCrewError.Unauthenticated)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `공통 오류를 상태 · 코드로 고른다`() = runTest {
        val cases = listOf(
            fail(HttpStatusCode.BadRequest, "BAD") to FestivalCrewError.InvalidInput::class,
            fail(HttpStatusCode.Unauthorized, "UNAUTHORIZED") to FestivalCrewError.Unauthenticated::class,
            fail(HttpStatusCode.Forbidden, "TERMS_REQUIRED") to FestivalCrewError.TermsRequired::class,
            fail(HttpStatusCode.Forbidden, "NOT_OWNER") to FestivalCrewError.Forbidden::class,
            fail(HttpStatusCode.NotFound, "NOT_FOUND") to FestivalCrewError.NotFound::class,
            fail(HttpStatusCode.Conflict, "CREW_FULL") to FestivalCrewError.Full::class,
            fail(HttpStatusCode.Conflict, "CREW_ALREADY_IN") to FestivalCrewError.AlreadyInCrew::class,
            fail(HttpStatusCode.Gone, "FESTIVAL_ENDED") to FestivalCrewError.FestivalEnded::class,
            fail(HttpStatusCode.TooManyRequests, "RATE_LIMITED") to FestivalCrewError.Cooldown::class,
            fail(HttpStatusCode.InternalServerError, "ERROR") to FestivalCrewError.Unavailable::class,
        )
        cases.forEach { (given, expected) ->
            response = given
            val error = failure { useCase.join("ABCD23") }
            assertEquals(given.first.toString(), expected, error?.let { it::class })
        }
    }

    @Test
    fun `stage 초대 미리보기 404는 NotFound다`() = runTest {
        response = HttpStatusCode.NotFound to """{"code":"NOT_FOUND","data":null,"message":"초대를 찾을 수 없습니다."}"""

        assertTrue(failure { useCase.invite("zzzzzz") } is FestivalCrewError.NotFound)
        assertEquals("/api/v1/crews/invite/ZZZZZZ", requests.single().url.encodedPath)
        assertNull(requests.single().attributes.getOrNull(BearerAuthorizationKey))
    }

    @Test
    fun `초대 미리보기는 로그인 없이 받고 이름 빈 미리보기 줄을 버린다`() = runTest {
        sessionId = null
        response = ok(
            """{"crewName":"우리","festivalId":"fst_a","festivalName":"축제","ownerNickname":"로이","memberCount":2,
            "expiresAt":"2026-11-25T01:02:03Z","memberPreview":[{"nickname":"로이","avatarUrl":null},{"nickname":""}]}""",
        )

        val invite = useCase.invite("abcd23")

        assertEquals("ABCD23", invite.code)
        assertEquals("로이", invite.inviterNickname)
        assertEquals(listOf("로이"), invite.memberPreview.map { it.nickname })
    }

    @Test
    fun `생성은 409 CREW_ALREADY_IN만 AlreadyInCrew로 보고 다른 409는 Unavailable이다`() = runTest {
        response = fail(HttpStatusCode.Conflict, "CREW_ALREADY_IN")
        assertTrue(failure { useCase.create("fst_a", "우리") } is FestivalCrewError.AlreadyInCrew)
        response = fail(HttpStatusCode.Conflict, "IDEMPOTENCY")
        assertTrue(failure { useCase.create("fst_a", "우리") } is FestivalCrewError.Unavailable)
        response = fail(HttpStatusCode.Forbidden, "TERMS_REQUIRED")
        assertTrue(failure { useCase.create("fst_a", "우리") } is FestivalCrewError.TermsRequired)
        assertEquals("""{"festivalId":"fst_a","name":"우리"}""", requests.first().textBody)
    }

    @Test
    fun `상태는 clientAt과 멱등 키를 붙이고 서버가 확정한 상태를 확인한다`() = runTest {
        response = ok("""{"userId":"usr_b","state":"here"}""")

        useCase.updateStatus("crw_a", FestivalCrewState.HERE, "zon_a")

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("""{"zoneId":"zon_a","state":"here","clientAt":"2026-11-23T01:02:03.456Z"}""", request.textBody)
        assertTrue(request.headers[IDEMPOTENCY_KEY_HEADER]?.isNotEmpty() == true)
        response = ok("""{"userId":"usr_b","state":"eating"}""")
        assertTrue(failure { useCase.updateStatus("crw_a", FestivalCrewState.HERE, null) } is FestivalCrewError.InvalidResponse)
    }

    @Test
    fun `모여는 429를 Cooldown으로, 다른 존 응답을 InvalidResponse로 본다`() = runTest {
        response = ok("""{"id":"gth_a","zoneId":"zon_a"}""")
        useCase.gather("crw_a", "zon_a")
        response = ok("""{"id":"gth_a","zoneId":"zon_b"}""")
        assertTrue(failure { useCase.gather("crw_a", "zon_a") } is FestivalCrewError.InvalidResponse)
        response = fail(HttpStatusCode.TooManyRequests, "GATHER_COOLDOWN")
        assertTrue(failure { useCase.gather("crw_a", "zon_a") } is FestivalCrewError.Cooldown)
        response = fail(HttpStatusCode.Forbidden, "TERMS_REQUIRED")
        assertTrue(failure { useCase.gather("crw_a", "zon_a") } is FestivalCrewError.TermsRequired)
        assertEquals("""{"zoneId":"zon_a"}""", requests.first().textBody)
    }

    @Test
    fun `모여 응답은 409 GATHER_EXPIRED를 GatherExpired로 본다`() = runTest {
        response = ok("""{"gatherId":"gth_a","userId":"usr_b","nickname":"비","response":"ack","respondedAt":"2026-11-23T01:02:03Z"}""")
        val reply = useCase.respondToGather("crw_a", "gth_a", FestivalCrewGatherResponse.ACK)
        assertEquals(FestivalCrewGatherResponse.ACK, reply.response)
        assertEquals("""{"response":"ack"}""", requests.single().textBody)

        response = fail(HttpStatusCode.Conflict, "GATHER_EXPIRED")
        assertTrue(failure { useCase.respondToGather("crw_a", "gth_a", FestivalCrewGatherResponse.ACK) } is FestivalCrewError.GatherExpired)
        response = fail(HttpStatusCode.Forbidden, "GATHER_SENDER")
        assertTrue(failure { useCase.respondToGather("crw_a", "gth_a", FestivalCrewGatherResponse.ACK) } is FestivalCrewError.Forbidden)
    }

    @Test
    fun `나가기는 내 ID로 내보내기 요청을 보내고 크루장 409는 Forbidden이다`() = runTest {
        response = ok("null")
        useCase.leave("crw_a")
        assertEquals(HttpMethod.Delete, requests.single().method)
        assertEquals("/api/v1/crews/crw_a/members/usr_b", requests.single().url.encodedPath)

        response = fail(HttpStatusCode.Conflict, "OWNER_CANNOT_LEAVE")
        assertTrue(failure { useCase.leave("crw_a") } is FestivalCrewError.Forbidden)

        userId = null
        requests.clear()
        assertTrue(failure { useCase.leave("crw_a") } is FestivalCrewError.Unauthenticated)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `해산 · 내보내기 · 초대 재생성은 같은 공통 매핑을 쓴다`() = runTest {
        response = ok("null")
        useCase.dissolve("crw_a")
        useCase.removeMember("crw_a", "usr_c")
        response = ok("""{"inviteCode":"WXYZ23","inviteExpiresAt":"2026-11-25T01:02:03Z"}""")
        assertEquals("WXYZ23", useCase.rotateInvite("crw_a").code)
        response = fail(HttpStatusCode.Forbidden, "NOT_OWNER")
        assertTrue(failure { useCase.dissolve("crw_a") } is FestivalCrewError.Forbidden)
        assertEquals(
            listOf(HttpMethod.Delete, HttpMethod.Delete, HttpMethod.Post, HttpMethod.Delete),
            requests.map { it.method },
        )
    }
}
