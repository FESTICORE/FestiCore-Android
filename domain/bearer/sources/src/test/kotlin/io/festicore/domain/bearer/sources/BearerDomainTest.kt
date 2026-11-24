package io.festicore.domain.bearer.sources

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.error.NetworkError
import io.festicore.domain.bearer.entity.BearerActivity
import io.festicore.domain.bearer.entity.BearerCoBearer
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerStatusChange
import io.festicore.domain.bearer.entity.BearerStatusRequest
import io.festicore.domain.bearer.sources.model.mapper.bearerError
import io.festicore.domain.bearer.sources.model.mapper.toBearerError
import io.festicore.domain.bearer.sources.model.mapper.toBody
import io.festicore.service.apiendpoint.flag.FlagStatusBody
import io.ktor.http.HttpMethod
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `BearerDomainTests` 기수 API 도메인 9건 + 사진 업로드 1건(같은 이름). 응답은 iOS 테스트와 같은 가짜 값이다. */
class BearerDomainTest {
    @Test
    fun fetchBoard() = runTest {
        val client = RecordingClient(json = BOARD_JSON)

        val board = makeRepository(client).fetchBoard("flg_1")

        val sent = client.last
        assertEquals("api/v1/flags/flg_1/bearer", sent.path)
        assertEquals(HttpMethod.Get, sent.method)
        assertTrue(sent.isSessionBound)
        assertEquals("호락호락", board.profile.name)
        assertEquals("https://festicore.test/api/v1/flags/flg_1/thumbnail", board.profile.thumbnailUrl)
        assertEquals(listOf(BearerFlagTag.Slam, BearerFlagTag.Newbie), board.profile.tags)
        assertEquals(BearerFlagLifecycle.Resting, board.status.lifecycle)
        assertNull(board.status.activity)
        assertEquals("zone_a", board.status.zoneId)
        assertEquals(BearerCoBearer("usr_2", "민지"), board.status.updatedBy)
        assertEquals(1284, board.followerCount)
        assertEquals(listOf(BearerCoBearer("usr_2", "민지")), board.coBearers)
        assertTrue(board.restartUsedToday)
    }

    @Test
    fun statusBodies() {
        val clientAt = Instant.ofEpochSecond(1_792_540_800)
        fun body(change: BearerStatusChange): JsonObject = FestiCoreJson
            .encodeToJsonElement(FlagStatusBody.serializer(), BearerStatusRequest(change, "k", clientAt).toBody())
            .jsonObject

        val start = body(BearerStatusChange.Start("zone_a", BearerActivity.Sing))
        assertEquals(listOf("activity", "clientAt", "zoneId"), start.keys.sorted())
        assertEquals("2026-10-21T00:00:00.000Z", start.string("clientAt"))
        assertEquals(listOf("clientAt", "zoneId"), body(BearerStatusChange.Move("zone_b")).keys.sorted())
        assertEquals("train", body(BearerStatusChange.Activity(BearerActivity.Train)).string("activity"))
        val rest = body(BearerStatusChange.Rest)
        assertEquals("resting", rest.string("status"))
        assertNull(rest["activity"])
        val end = body(BearerStatusChange.End)
        assertEquals(listOf("clientAt", "status"), end.keys.sorted())
        assertEquals("ended", end.string("status"))
        val restart = body(BearerStatusChange.Restart("zone_a", BearerActivity.Calm))
        assertTrue(restart.getValue("restart").jsonPrimitive.boolean)
        assertEquals("zone_a", restart.string("zoneId"))
        assertEquals("calm", restart.string("activity"))
        assertNull(restart["status"])
    }

    @Test
    fun sendStatus() = runTest {
        val client = RecordingClient(
            json = """
                {"code":"SUCCESS","message":"OK",
                "data":{"flagId":"flg_1","status":"active","zoneId":"zone_b","activity":"sing","updatedAt":"2026-10-21T00:00:00.000Z"}}
            """.trimIndent(),
        )
        val request = BearerStatusRequest(BearerStatusChange.Move("zone_b"), "key-1", Instant.now())

        val status = makeRepository(client).sendStatus("flg_1", request)

        val sent = client.last
        assertEquals("api/v1/flags/flg_1/status", sent.path)
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals("key-1", sent.headers["Idempotency-Key"])
        assertEquals(BearerFlagLifecycle.Active, status.lifecycle)
        assertEquals(BearerActivity.Sing, status.activity)
        assertNull(status.updatedBy)
    }

    @Test
    fun claim() = runTest {
        val client = RecordingClient(
            json = """
                {"code":"SUCCESS","message":"OK",
                "data":{"flagId":"flg_1",
                "flag":{"id":"flg_1","name":"새 깃발 a1b2c3","thumbUrl":null,"photoUrl":null,
                "tags":["newbie"],"bio":null,"followerCount":0,"planVisible":true,"plan":[],"homeZoneId":null},
                "isNew":true,"coBearers":["usr_1"]}}
            """.trimIndent(),
        )

        val claim = makeRepository(client).claim("fst_1", "ABC234")

        val sent = client.last
        assertEquals("api/v1/bearer/claim", sent.path)
        assertEquals(DEVICE_ID, sent.headers["X-Device-Id"])
        assertTrue(sent.isSessionBound)
        assertEquals(mapOf("festivalId" to "fst_1", "code" to "ABC234"), sent.jsonBody.mapValues { it.value.jsonPrimitive.content })
        assertEquals("flg_1", claim.flagId)
        assertTrue(claim.isNew)
        assertEquals(listOf(BearerFlagTag.Newbie), claim.profile.tags)
    }

    @Test
    fun claimFailures() = runTest {
        // 422 · 423 실패 본문의 `data`를 읽는다. `data: null`이면 남은 횟수를 모른다.
        val invalid = RecordingClient(422, """{"code":"BEARER_CODE_INVALID","data":{"remainingAttempts":2},"message":"x"}""")
        assertEquals(BearerError.InvalidCode(2), failure { makeRepository(invalid).claim("fst_1", "ABC234") })
        val unknown = RecordingClient(422, """{"code":"BEARER_CODE_INVALID","data":null,"message":"x"}""")
        assertEquals(BearerError.InvalidCode(null), failure { makeRepository(unknown).claim("fst_1", "ABC234") })
        val locked = RecordingClient(423, """{"code":"BEARER_CODE_LOCKED","data":{"retryAfter":582},"message":"x"}""")
        assertEquals(BearerError.CodeLocked(582), failure { makeRepository(locked).claim("fst_1", "ABC234") })
    }

    @Test
    fun uploadPhoto() = runTest {
        val client = RecordingClient(
            json = """
                {"code":"SUCCESS","message":"OK","data":{"photoUrl":"/api/v1/flags/flg_1/photo","thumbUrl":"/api/v1/flags/flg_1/thumbnail"}}
            """.trimIndent(),
        )
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

        val photo = makeRepository(client).uploadPhoto("flg_1", jpeg) {}

        val sent = client.last
        assertEquals("api/v1/flags/flg_1/photo", sent.path)
        assertEquals(HttpMethod.Post, sent.method)
        assertTrue(sent.isSessionBound)
        val parts = client.lastMultipartParts()
        assertEquals(1, parts.size)
        assertTrue(parts[0].headers, parts[0].headers.contains("Content-Disposition: form-data; name=\"photo\"; filename=\"photo.jpg\""))
        assertTrue(parts[0].headers, parts[0].headers.contains("Content-Type: image/jpeg"))
        assertArrayEquals(jpeg, parts[0].body)
        assertEquals("https://festicore.test/api/v1/flags/flg_1/photo", photo.photoUrl)
        assertEquals("https://festicore.test/api/v1/flags/flg_1/thumbnail", photo.thumbnailUrl)
    }

    @Test
    fun uploadPhotoFailures() = runTest {
        val tooLarge = RecordingClient(413, """{"code":"PAYLOAD_TOO_LARGE","message":"x"}""")
        assertEquals(BearerError.PhotoTooLarge(), failure { makeRepository(tooLarge).uploadPhoto("flg_1", byteArrayOf(1)) {} })

        val offline = RecordingClient(offline = true)
        assertEquals(BearerError.Offline(), failure { makeRepository(offline).uploadPhoto("flg_1", byteArrayOf(1)) {} })

        val loggedOut = RecordingClient()
        assertEquals(
            BearerError.NotAuthenticated(),
            failure {
                makeRepository(loggedOut, loggedIn = false).uploadPhoto("flg_1", byteArrayOf(1)) {}
            },
        )
        assertTrue(loggedOut.sent.isEmpty())
    }

    @Test
    fun errorMapping() {
        // iOS 인자 16종
        val cases = listOf(
            Triple(400, "VALIDATION", BearerError.InvalidRequest()),
            Triple(401, "UNAUTHORIZED", BearerError.NotAuthenticated()),
            Triple(403, "TERMS_REQUIRED", BearerError.TermsRequired()),
            Triple(403, "FLAG_NOT_BEARER", BearerError.NotBearer()),
            Triple(403, "FLAG_NOT_OWNER", BearerError.NotOwner()),
            Triple(404, "ZONE_NOT_FOUND", BearerError.ZoneNotFound()),
            Triple(404, "NOT_FOUND", BearerError.NotFound()),
            Triple(409, "FLAG_NAME_TAKEN", BearerError.NameTaken()),
            Triple(409, "STATUS_STALE", BearerError.Stale()),
            Triple(409, "FLAG_RESTART_REQUIRED", BearerError.TransitionRejected()),
            Triple(409, "CO_BEARER_FULL", BearerError.CoBearerFull()),
            Triple(409, "CONFLICT", BearerError.Conflict()),
            Triple(413, "PAYLOAD_TOO_LARGE", BearerError.PhotoTooLarge()),
            Triple(415, "UNSUPPORTED_MEDIA_TYPE", BearerError.UnsupportedPhoto()),
            Triple(429, "RATE_LIMITED", BearerError.RateLimited()),
            Triple(500, "INTERNAL_SERVER_ERROR", BearerError.Unavailable()),
        )
        assertEquals(16, cases.size)
        cases.forEach { (status, code, expected) ->
            assertEquals("$status $code", expected, bearerError(status, code))
            assertEquals("$status $code", expected, NetworkError.Http(status, code, "x").toBearerError())
        }
    }

    @Test
    fun transportMapping() = runTest {
        val offline = RecordingClient(offline = true)
        val error = failure {
            makeRepository(offline).sendStatus("flg_1", BearerStatusRequest(BearerStatusChange.Rest, "k", Instant.now()))
        }
        assertEquals(BearerError.Offline(), error)
        val cancelled = CancellationException("취소")
        assertSame(cancelled, cancelled.toBearerError())
    }

    @Test
    fun notAuthenticated() = runTest {
        val client = RecordingClient(json = BOARD_JSON)

        val error = failure { makeRepository(client, loggedIn = false).fetchBoard("flg_1") }

        assertEquals(BearerError.NotAuthenticated(), error)
        assertTrue(client.sent.isEmpty())
    }

    @Test
    fun profileAndCoBearer() = runTest {
        val client = RecordingClient(
            json = """
                {"code":"SUCCESS","message":"OK",
                "data":{"id":"flg_1","name":"호락호락","thumbUrl":null,"photoUrl":null,
                "tags":["sing"],"bio":"함께 노래해요","followerCount":3,"planVisible":false,"plan":[],"homeZoneId":"zone_a"}}
            """.trimIndent(),
        )

        val profile = makeRepository(client).updateProfile(
            "flg_1",
            BearerProfileChange(name = " 호락호락 ", bio = "  ", tags = listOf(BearerFlagTag.Sing), planVisible = false, homeZoneId = "zone_a"),
        )

        val sent = client.last
        assertEquals(HttpMethod.Patch, sent.method)
        assertEquals("api/v1/flags/flg_1", sent.path)
        val body = sent.jsonBody
        assertEquals("호락호락", body.string("name"))
        assertEquals(JsonNull, body["bio"])
        assertFalse("plan을 고르지 않으면 키를 보내지 않는다", body.containsKey("plan"))
        assertEquals("zone_a", profile.homeZoneId)
        assertFalse(profile.planVisible)

        val removal = RecordingClient(204, "")
        makeRepository(removal).removeCoBearer("flg_1", "usr_2")
        assertEquals(HttpMethod.Delete, removal.last.method)
        assertEquals("api/v1/flags/flg_1/co-bearers/usr_2", removal.last.path)
    }

    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.content

    private companion object {
        val BOARD_JSON = """
            {"code":"SUCCESS","message":"OK","data":{
            "flag":{"id":"flg_1","name":"호락호락","thumbUrl":"/api/v1/flags/flg_1/thumbnail","photoUrl":"/api/v1/flags/flg_1/photo","tags":["slam","newbie","unknown"],"bio":null,"followerCount":1284,"planVisible":true,"plan":[],"homeZoneId":"zone_a"},
            "status":{"flagId":"flg_1","status":"resting","zoneId":"zone_a","activity":"resting","updatedAt":"2026-10-21T00:00:00.000Z","updatedBy":{"userId":"usr_2","nickname":"민지"}},
            "planVisible":true,"followerCount":1284,"coBearers":[{"userId":"usr_2","nickname":"민지"}],
            "hidden":false,"reportCount":0,"restartUsedToday":true}}
        """.trimIndent()
    }
}
