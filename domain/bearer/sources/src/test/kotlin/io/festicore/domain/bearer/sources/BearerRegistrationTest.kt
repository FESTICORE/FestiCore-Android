package io.festicore.domain.bearer.sources

import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerPinnedFlag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerRegisterRequest
import io.festicore.domain.bearer.sources.bearer.BearerUseCaseImpl
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `BearerRegistrationTests` 9건(같은 이름). 응답은 iOS 테스트와 같은 가짜 값이다. */
class BearerRegistrationTest {
    @Test
    fun register() = runTest {
        val client = RecordingClient(201, REGISTER_JSON)
        val request = BearerRegisterRequest(
            festivalId = "fst_1",
            change = BearerProfileChange(
                name = "  호락호락 ",
                bio = "  ",
                tags = listOf(BearerFlagTag.Slam),
                planVisible = true,
                homeZoneId = null,
            ),
            idempotencyKey = "register-1",
        )

        val claim = makeRepository(client).register(request)

        val sent = client.last
        assertEquals("api/v1/bearer/register", sent.path)
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals("register-1", sent.headers["Idempotency-Key"])
        assertTrue(sent.isSessionBound)
        val body = sent.jsonBody
        assertEquals(listOf("festivalId", "name", "planVisible", "tags"), body.keys.sorted())
        assertEquals("호락호락", body.getValue("name").jsonPrimitive.content)
        assertEquals("flg_9", claim.flagId)
        assertTrue(claim.isNew)
        assertEquals("호락호락", claim.profile.name)
        assertEquals(emptyList<String>(), claim.coBearerIds)
    }

    @Test
    fun registerFailures() = runTest {
        val cases = listOf(
            Triple(409, "BEARER_LIMIT", BearerError.BearerLimit()),
            Triple(409, "FLAG_NAME_TAKEN", BearerError.NameTaken()),
            Triple(409, "IDEMPOTENCY_CONFLICT", BearerError.Conflict()),
            Triple(403, "TERMS_REQUIRED", BearerError.TermsRequired()),
            Triple(404, "ZONE_NOT_FOUND", BearerError.ZoneNotFound()),
            Triple(404, "NOT_FOUND", BearerError.NotFound()),
            Triple(400, "VALIDATION", BearerError.InvalidRequest()),
        )
        cases.forEach { (status, code, expected) ->
            val client = RecordingClient(status, """{"code":"$code","data":null,"message":"x"}""")
            assertEquals("$status $code", expected, failure { makeRepository(client).register(REQUEST) })
        }
        assertEquals(BearerError.Offline(), failure { makeRepository(RecordingClient(offline = true)).register(REQUEST) })
    }

    @Test
    fun registerNeedsSession() = runTest {
        val client = RecordingClient(json = REGISTER_JSON)

        assertEquals(BearerError.NotAuthenticated(), failure { makeRepository(client, loggedIn = false).register(REQUEST) })
        assertTrue(client.sent.isEmpty())
    }

    @Test
    fun registerRequestKey() {
        var next = 0
        val useCase =
            BearerUseCaseImpl(ClaimRecordingRepository(), StubPackSyncService(null), newUuid = {
                "uuid-${next++}"
            }, outbox = UnusedOutbox())

        val first = useCase.makeRegisterRequest("fst_1", REQUEST.change)
        val second = useCase.makeRegisterRequest("fst_1", REQUEST.change)

        assertTrue(first.idempotencyKey.startsWith("register-"))
        assertNotEquals(first.idempotencyKey, second.idempotencyKey)
        assertEquals("fst_1", first.festivalId)
    }

    @Test
    fun bearerFlags() = runTest {
        val client = RecordingClient(json = BEARER_FLAGS_JSON)

        val flags = makeRepository(client).fetchBearerFlags("fst_1")

        val sent = client.last
        assertEquals("api/v1/me/bearer-flags", sent.path)
        assertEquals("fst_1", sent.url.parameters["festivalId"])
        assertEquals(HttpMethod.Get, sent.method)
        assertTrue(sent.isSessionBound)
        assertEquals(2, flags.size)
        val first = flags.first()
        assertEquals("flg_1", first.id)
        assertEquals("fst_1", first.festivalId)
        assertTrue(first.isOwner)
        assertEquals(BearerFlagLifecycle.Scheduled, first.lifecycle)
        assertNotNull(first.joinedAt)
        assertEquals(128, first.profile.followerCount)
        assertEquals("https://festicore.test/api/v1/flags/flg_1/thumbnail", first.profile.thumbnailUrl)
        val second = flags.last()
        assertFalse(second.isOwner)
        assertTrue(second.isHidden)
        assertNull(second.lifecycle)
        assertNull(second.joinedAt)
        assertEquals(0, second.profile.followerCount)
    }

    @Test
    fun pinnedFlag() = runTest {
        val empty = RecordingClient(json = """{"code":"SUCCESS","message":"OK","data":{"pinnedFlagId":null,"flag":null}}""")
        assertNull(makeRepository(empty).fetchPinnedFlag())
        assertEquals("api/v1/me/pinned-flag", empty.last.path)
        assertEquals(HttpMethod.Get, empty.last.method)

        val pinned = RecordingClient(
            json = """
                {"code":"SUCCESS","message":"OK",
                "data":{"pinnedFlagId":"flg_1",
                "flag":{"id":"flg_1","name":"호락호락","thumbUrl":null,"festivalId":"fst_1","isPinned":true}}}
            """.trimIndent(),
        )
        assertEquals(BearerPinnedFlag("flg_1", "호락호락", "fst_1", null), makeRepository(pinned).fetchPinnedFlag())
    }

    @Test
    fun followedFlags() = runTest {
        val client = RecordingClient(
            routes = mapOf(
                "api/v1/me/follows" to (
                    200 to
                        """{"code":"SUCCESS","message":"OK","data":{"items":[{"flagId":"flg_2","festivalId":"fst_1","followedAt":"2026-10-21T00:00:00.000Z"},{"flagId":"flg_gone","festivalId":"fst_1","followedAt":null},{"flagId":"flg_1","festivalId":"fst_2","followedAt":"bad"}]}}"""
                    ),
                "api/v1/flags/flg_2" to (
                    200 to
                        """{"code":"SUCCESS","message":"OK","data":{"id":"flg_2","name":"둘째","thumbUrl":"/api/v1/flags/flg_2/thumbnail","hidden":false}}"""
                    ),
                "api/v1/flags/flg_gone" to (404 to """{"code":"FLAG_NOT_FOUND","message":"없음","data":null}"""),
                "api/v1/flags/flg_1" to (200 to """{"code":"SUCCESS","message":"OK","data":{"id":"flg_1","name":"첫째","thumbUrl":null}}"""),
            ),
        )

        val flags = makeRepository(client).fetchFollowedFlags(null)

        val follows = client.sent.first()
        assertEquals("api/v1/me/follows", follows.path)
        assertEquals(HttpMethod.Get, follows.method)
        assertEquals(0L, (follows.body as OutgoingContent).contentLength ?: 0L)
        assertTrue(follows.url.parameters.isEmpty())
        assertTrue(follows.isSessionBound)
        assertEquals(listOf("flg_2", "flg_1"), flags.map { it.flagId })
        assertEquals("둘째", flags.first().name)
        assertEquals("fst_1", flags.first().festivalId)
        assertNotNull(flags.first().followedAt)
        assertEquals("https://festicore.test/api/v1/flags/flg_2/thumbnail", flags.first().thumbnailUrl)
        assertNull(flags.last().thumbnailUrl)
        assertNull(flags.last().followedAt)
    }

    @Test
    fun emptyFollowedFlags() = runTest {
        val client = RecordingClient(json = """{"code":"SUCCESS","message":"OK","data":{"items":[]}}""")

        val flags = makeRepository(client).fetchFollowedFlags("fst_1")

        assertTrue(flags.isEmpty())
        assertEquals(1, client.sent.size)
    }

    @Test
    fun followedFlagDetailFailurePropagates() = runTest {
        val client = RecordingClient(
            routes = mapOf(
                "api/v1/me/follows" to
                    (
                        200 to
                            """{"code":"SUCCESS","message":"OK","data":{"items":[{"flagId":"flg_1","festivalId":"fst_1","followedAt":null}]}}"""
                        ),
                "api/v1/flags/flg_1" to (500 to """{"code":"INTERNAL","message":"오류","data":null}"""),
            ),
        )

        assertTrue(failure { makeRepository(client).fetchFollowedFlags(null) } is BearerError)
    }

    private companion object {
        val REQUEST = BearerRegisterRequest(
            festivalId = "fst_1",
            change = BearerProfileChange(
                name = "호락호락",
                bio = null,
                tags = listOf(BearerFlagTag.Slam),
                planVisible = true,
                homeZoneId = null,
            ),
            idempotencyKey = "register-1",
        )

        const val REGISTER_JSON =
            """{"code":"SUCCESS","message":"OK","data":{"flagId":"flg_9","flag":{"id":"flg_9","name":"호락호락","thumbUrl":null,"photoUrl":null,"tags":["slam"],"bio":null,"followerCount":0,"planVisible":true,"plan":[],"homeZoneId":null},"isNew":true,"coBearers":[]}}"""

        val BEARER_FLAGS_JSON = """
            {"code":"SUCCESS","message":"OK","data":{"items":[
            {"flagId":"flg_1","festivalId":"fst_1","isOwner":true,"joinedAt":"2026-10-21T00:00:00.000Z","flag":{"id":"flg_1","name":"호락호락","thumbUrl":"/api/v1/flags/flg_1/thumbnail","photoUrl":null,"tags":["sing"],"bio":null,"followerCount":128,"planVisible":true,"plan":[],"homeZoneId":null},"status":{"flagId":"flg_1","status":"scheduled","zoneId":null,"activity":null,"updatedAt":"2026-10-21T00:00:00.000Z"},"hidden":false},
            {"flagId":"flg_2","festivalId":"fst_2","isOwner":false,"joinedAt":null,"flag":{"id":"flg_2","name":"푸른 깃발","thumbUrl":null,"photoUrl":null,"tags":null,"bio":null,"followerCount":null,"planVisible":null,"plan":null,"homeZoneId":null},"status":{"flagId":"flg_2","status":"unknown","zoneId":null,"activity":null,"updatedAt":"2026-10-21T00:00:00.000Z"},"hidden":true}
            ]}}
        """.trimIndent()
    }
}
