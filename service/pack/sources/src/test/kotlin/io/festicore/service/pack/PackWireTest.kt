package io.festicore.service.pack

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PackWireTest {
    private val requestId = "11111111-2222-3333-4444-555555555555"

    @Test
    fun `pack_get 요청은 축제 ID와 ETag를 담고 ETag가 없으면 키를 뺀다`() {
        val withEtag = Json.parseToJsonElement(PackWire.request(FESTIVAL_ID, requestId, "\"one\"")).jsonObject
        assertEquals("pack.get", withEtag.getValue("event").jsonPrimitive.content)
        assertEquals(requestId, withEtag.requestId)
        val data = withEtag.getValue("data").jsonObject
        assertEquals(FESTIVAL_ID, data.getValue("festivalId").jsonPrimitive.content)
        assertEquals("\"one\"", data.ifNoneMatch())

        val withoutEtag = Json.parseToJsonElement(PackWire.request(FESTIVAL_ID, requestId, null)).jsonObject
        assertNull(withoutEtag.getValue("data").jsonObject.ifNoneMatch())
    }

    @Test
    fun `1KB를 넘는 요청은 보내지 않는다`() {
        assertThrows<PackSyncError.InvalidMessage> { PackWire.request(FESTIVAL_ID, requestId, "e".repeat(1024)) }
    }

    @Test
    fun `4MB를 넘는 메시지는 해석하지 않는다`() {
        val padding = "x".repeat(PackWire.MAX_MESSAGE_BYTES)
        assertThrows<PackSyncError.InvalidMessage> { PackWire.decode(message("pack.retry", requestId, data("padding" to padding))) }
        // 한글은 UTF-8로 3바이트라 글자 수가 한도 아래여도 바이트로 넘으면 거부한다.
        val korean = "가".repeat(PackWire.MAX_MESSAGE_BYTES / 3 + 1)
        assertThrows<PackSyncError.InvalidMessage> { PackWire.decode(message("pack.retry", requestId, data("padding" to korean))) }
    }

    @Test
    fun `JSON 객체가 아니거나 event가 없으면 잘못된 메시지다`() {
        assertThrows<PackSyncError.InvalidMessage> { PackWire.decode("not json") }
        assertThrows<PackSyncError.InvalidMessage> { PackWire.decode("[]") }
        assertThrows<PackSyncError.InvalidMessage> { PackWire.decode("{\"requestId\":\"a\"}") }
        assertThrows<PackSyncError.InvalidMessage> { PackWire.decode("{\"event\":1}") }
    }

    @Test
    fun `스냅샷은 축제 ID · 버전 · ETag · 팩 본문이 맞아야 한다`() {
        val ok = PackWire.decode(
            message(
                "pack.snapshot",
                requestId,
                data(
                    "festivalId" to FESTIVAL_ID,
                    "packVersion" to 3,
                    "etag" to "\"e\"",
                    "pack" to data("id" to FESTIVAL_ID, "packVersion" to 3),
                ),
            ),
        )
        val pack = PackWire.cachedPack(ok, FESTIVAL_ID)
        assertEquals(3, pack.packVersion)
        assertTrue(PackWire.isValidPack(pack.pack, FESTIVAL_ID, 3))

        val bad = listOf(
            data(
                "festivalId" to "fst_other",
                "packVersion" to 3,
                "etag" to "\"e\"",
                "pack" to data("id" to FESTIVAL_ID, "packVersion" to 3),
            ),
            data(
                "festivalId" to FESTIVAL_ID,
                "packVersion" to 0,
                "etag" to "\"e\"",
                "pack" to data("id" to FESTIVAL_ID, "packVersion" to 0),
            ),
            data(
                "festivalId" to FESTIVAL_ID,
                "packVersion" to "3",
                "etag" to "\"e\"",
                "pack" to data("id" to FESTIVAL_ID, "packVersion" to 3),
            ),
            data("festivalId" to FESTIVAL_ID, "packVersion" to 3, "etag" to "", "pack" to data("id" to FESTIVAL_ID, "packVersion" to 3)),
            data(
                "festivalId" to FESTIVAL_ID,
                "packVersion" to 3,
                "etag" to "e".repeat(129),
                "pack" to data("id" to FESTIVAL_ID, "packVersion" to 3),
            ),
            data(
                "festivalId" to FESTIVAL_ID,
                "packVersion" to 3,
                "etag" to "\"e\"",
                "pack" to data("id" to "fst_other", "packVersion" to 3),
            ),
            data(
                "festivalId" to FESTIVAL_ID,
                "packVersion" to 3,
                "etag" to "\"e\"",
                "pack" to data("id" to FESTIVAL_ID, "packVersion" to 2),
            ),
        )
        bad.forEach { body ->
            assertThrows<PackSyncError.InvalidPack> {
                PackWire.cachedPack(PackWire.decode(message("pack.snapshot", requestId, body)), FESTIVAL_ID)
            }
        }
    }

    @Test
    fun `8MB를 넘는 팩은 거부한다`() {
        // 메시지 4MB 한도와 별개로 팩 한도를 본다(봉투 없이 직접 만든 메시지).
        val big = PackWire.Message(
            "pack.snapshot",
            requestId,
            data(
                "festivalId" to FESTIVAL_ID,
                "packVersion" to 1,
                "etag" to "\"e\"",
                "pack" to data("id" to FESTIVAL_ID, "packVersion" to 1, "padding" to "x".repeat(PackWire.MAX_PACK_BYTES)),
            ),
        )
        assertThrows<PackSyncError.InvalidPack> { PackWire.cachedPack(big, FESTIVAL_ID) }

        val start = startMessage(
            total = PackWire.MAX_PACK_BYTES + 1,
            count =
                (PackWire.MAX_PACK_BYTES + 1 + PackWire.CHUNK_BYTES - 1) / PackWire.CHUNK_BYTES,
        )
        runTest {
            assertThrowsSuspend<PackSyncError.InvalidChunk> {
                PackWire.chunkedPack(start, ScriptSocket(), requestId, FESTIVAL_ID, 15.seconds)
            }
        }
    }

    @Test
    fun `청크를 순서대로 모아 sha256이 맞으면 진행률과 함께 팩을 만든다`() = runTest {
        val pack = packBytes(4, padding = 100_000)
        val messages = chunked(requestId, pack, 4, "\"four\"")
        val socket = ScriptSocket()
        messages.drop(1).forEach(socket::push)
        val progress = mutableListOf<PackDownloadProgress>()

        val (result, hint) = PackWire.chunkedPack(PackWire.decode(messages.first()), socket, requestId, FESTIVAL_ID, 15.seconds) {
            progress +=
                it
        }

        assertArrayEquals(pack, result.pack)
        assertEquals("\"four\"", result.etag)
        assertFalse(hint)
        assertEquals(listOf(49_152, 98_304, pack.size), progress.map { it.receivedBytes })
        assertTrue(progress.all { it.totalBytes == pack.size })
    }

    @Test
    fun `sha256이 다르거나 대문자면 청크 팩을 거부한다`() = runTest {
        val pack = packBytes(2)
        listOf("0".repeat(64), PackWire.sha256Hex(pack).uppercase()).forEach { digest ->
            val messages = chunked(requestId, pack, 2, "\"two\"", sha256 = digest)
            val socket = ScriptSocket()
            messages.drop(1).forEach(socket::push)
            assertThrowsSuspend<PackSyncError.InvalidChunk> {
                PackWire.chunkedPack(PackWire.decode(messages.first()), socket, requestId, FESTIVAL_ID, 15.seconds)
            }
        }
    }

    @Test
    fun `청크 순서 · requestId · 크기가 어긋나면 거부한다`() = runTest {
        val pack = packBytes(2, padding = 60_000)
        val messages = chunked(requestId, pack, 2, "\"two\"")
        val (start, first, second, complete) = messages
        val cases = listOf(
            listOf(second, first, complete), // 순서
            listOf(first.replace(requestId, "other"), second, complete), // requestId
            listOf(first, complete), // 청크 모자람
            listOf(message("pack.chunk", requestId, data("index" to 0, "base64" to "AAAA")), second, complete), // 크기
            listOf(message("pack.chunk", requestId, data("index" to 0, "base64" to "%%%")), second, complete), // base64
        )
        cases.forEach { tail ->
            val socket = ScriptSocket()
            tail.forEach(socket::push)
            assertThrowsSuspend<Exception> { PackWire.chunkedPack(PackWire.decode(start), socket, requestId, FESTIVAL_ID, 15.seconds) }
        }
    }

    @Test
    fun `chunkCount가 totalBytes와 맞지 않으면 받지 않는다`() = runTest {
        assertThrowsSuspend<PackSyncError.InvalidChunk> {
            PackWire.chunkedPack(startMessage(total = 100, count = 2), ScriptSocket(), requestId, FESTIVAL_ID, 15.seconds)
        }
    }

    @Test
    fun `청크 사이 업데이트 알림은 건너뛰고 힌트로 알린다`() = runTest {
        val pack = packBytes(3)
        val (start, chunk, complete) = chunked(requestId, pack, 3, "\"three\"")
        val socket = ScriptSocket()
        listOf(message("pack.updated", null, data("festivalId" to FESTIVAL_ID)), chunk, complete).forEach(socket::push)
        val (_, hint) = PackWire.chunkedPack(PackWire.decode(start), socket, requestId, FESTIVAL_ID, 15.seconds)
        assertTrue(hint)
    }

    @Test
    fun `응답 대기가 지나면 소켓을 닫고 시간 초과를 알린다`() = runTest {
        val socket = ScriptSocket(waitWhenEmpty = true)
        assertThrowsSuspend<PackSyncError.Timeout> { PackWire.receiveText(socket, 15.seconds) }
        assertEquals(1, socket.cancelCount)
    }

    private fun startMessage(total: Int, count: Int) = PackWire.Message(
        "pack.start",
        requestId,
        data(
            "festivalId" to FESTIVAL_ID,
            "packVersion" to 1,
            "etag" to "\"e\"",
            "totalBytes" to total,
            "chunkCount" to count,
            "sha256" to "a".repeat(64),
        ),
    )
}

internal inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return
        fail("${T::class.simpleName} 대신 $e")
    }
    fail("${T::class.simpleName}가 나지 않았다")
}

internal suspend inline fun <reified T : Throwable> assertThrowsSuspend(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return
        fail("${T::class.simpleName} 대신 $e")
    }
    fail("${T::class.simpleName}가 나지 않았다")
}
