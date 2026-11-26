package io.festicore.domain.bearer.sources

import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestivalTransferRequest
import io.festicore.domain.bearer.sources.bearer.BearerUseCaseImpl
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `POST flags/{id}/festival-transfer`(백엔드 #409). 응답은 서버 OpenAPI 예시와 같은 가짜 값이다. */
class BearerFestivalTransferTest {
    @Test
    fun transferFestival() = runTest {
        val client = RecordingClient(200, TRANSFER_JSON)

        val result = makeRepository(client).transferFestival(REQUEST)

        val sent = client.last
        assertEquals("api/v1/flags/flg_123/festival-transfer", sent.path)
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals("transfer-1", sent.headers["Idempotency-Key"])
        assertTrue(sent.isSessionBound)
        val body = sent.jsonBody
        assertEquals(listOf("fromFestivalId", "toFestivalId"), body.keys.sorted())
        assertEquals("fst_old", body.getValue("fromFestivalId").jsonPrimitive.content)
        assertEquals("fst_new", body.getValue("toFestivalId").jsonPrimitive.content)
        assertEquals("flg_123", result.flagId)
        assertEquals("fst_old", result.previousFestivalId)
        assertEquals("fst_new", result.festivalId)
        assertEquals("푸른 깃발", result.profile.name)
        assertEquals(12, result.profile.followerCount)
        assertEquals(emptyList<String>(), result.profile.plan)
        assertEquals(null, result.profile.homeZoneId)
    }

    @Test
    fun transferFailures() = runTest {
        val cases = listOf(
            Triple(400, "VALIDATION", BearerError.InvalidRequest()),
            Triple(401, "UNAUTHORIZED", BearerError.NotAuthenticated()),
            Triple(403, "FLAG_NOT_BEARER", BearerError.NotBearer()),
            Triple(403, "FLAG_NOT_OWNER", BearerError.NotOwner()),
            Triple(403, "TERMS_REQUIRED", BearerError.TermsRequired()),
            Triple(404, "NOT_FOUND", BearerError.NotFound()),
            Triple(409, "FESTIVAL_MISMATCH", BearerError.FestivalMismatch()),
            Triple(409, "FLAG_HAS_CO_BEARERS", BearerError.HasCoBearers()),
            Triple(409, "BEARER_LIMIT", BearerError.BearerLimit()),
            Triple(409, "FLAG_NAME_TAKEN", BearerError.NameTaken()),
            Triple(409, "BEARER_MODE_OFF", BearerError.BearerModeOff()),
            Triple(409, "IDEMPOTENCY_CONFLICT", BearerError.Conflict()),
        )
        cases.forEach { (status, code, expected) ->
            val client = RecordingClient(status, """{"code":"$code","data":null,"message":"x"}""")
            assertEquals("$status $code", expected, failure { makeRepository(client).transferFestival(REQUEST) })
        }
        assertEquals(BearerError.Offline(), failure { makeRepository(RecordingClient(offline = true)).transferFestival(REQUEST) })
    }

    @Test
    fun transferNeedsSession() = runTest {
        val client = RecordingClient(json = TRANSFER_JSON)

        assertEquals(BearerError.NotAuthenticated(), failure { makeRepository(client, loggedIn = false).transferFestival(REQUEST) })
        assertTrue(client.sent.isEmpty())
    }

    @Test
    fun transferRequestKey() {
        var next = 0
        val useCase =
            BearerUseCaseImpl(ClaimRecordingRepository(), StubPackSyncService(null), newUuid = {
                "UUID-${next++}"
            }, outbox = UnusedOutbox())

        val first = useCase.makeFestivalTransferRequest("flg_123", "fst_old", "fst_new")
        val second = useCase.makeFestivalTransferRequest("flg_123", "fst_old", "fst_new")

        assertEquals("transfer-uuid-0", first.idempotencyKey)
        assertTrue(first.idempotencyKey.length <= 128)
        assertNotEquals(first.idempotencyKey, second.idempotencyKey)
        assertEquals(BearerFestivalTransferRequest("flg_123", "fst_old", "fst_new", "transfer-uuid-0"), first)
    }

    private companion object {
        val REQUEST = BearerFestivalTransferRequest(
            flagId = "flg_123",
            fromFestivalId = "fst_old",
            toFestivalId = "fst_new",
            idempotencyKey = "transfer-1",
        )

        const val TRANSFER_JSON =
            """{"code":"SUCCESS","message":"OK","data":{"flagId":"flg_123","previousFestivalId":"fst_old","festivalId":"fst_new","flag":{"id":"flg_123","name":"푸른 깃발","thumbUrl":null,"photoUrl":null,"tags":["sing"],"bio":"함께 노래해요","followerCount":12,"planVisible":true,"plan":[],"homeZoneId":null}}}"""
    }
}
