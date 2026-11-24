package io.festicore.service.pack

import android.app.Application
import io.festicore.core.network.error.NetworkError
import io.festicore.core.network.websocket.WebSocketError
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** iOS `PackSyncTests`에서 Android에 해당하는 사례. 실서버 대신 [ScriptSocket]을 쓴다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NetworkPackSyncServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val directory: File get() = File(folder.root, "packs")

    private fun service(client: ScriptClient) = NetworkPackSyncService(PackCache(directory, io = Dispatchers.Unconfined), client)

    private fun service(vararg sockets: ScriptSocket) = service(ScriptClient(sockets.toList()))

    private fun snapshotSocket(version: Int, etag: String) = ScriptSocket { listOf(snapshot(it, version, etag)) }

    @Test
    fun `완성된 팩은 새 서비스 인스턴스에서 네트워크 없이 복원하고 공개 팩 채널로 연결한다`() = runTest {
        val client = ScriptClient(listOf(snapshotSocket(1, "\"one\"")))
        val result = service(client).sync(FESTIVAL_ID)

        assertTrue(result is PackSyncResult.Updated)
        assertEquals(listOf("api/v1/festivals/updates"), client.paths)
        assertEquals(1, service(ScriptClient(emptyList())).cachedPack(FESTIVAL_ID)?.packVersion)
    }

    @Test
    fun `단발 동기화가 끝나면 웹소켓 연결을 닫는다`() = runTest {
        val socket = snapshotSocket(1, "\"one\"")
        service(socket).sync(FESTIVAL_ID)
        assertEquals(1, socket.cancelCount)
    }

    @Test
    fun `서버 주소가 없으면 Unconfigured, 축제 ID가 틀리면 InvalidFestivalId`() = runTest {
        val unconfigured = service(ScriptClient(emptyList(), failures = listOf(WebSocketError.InvalidUrl())))
        assertThrowsSuspend<PackSyncError.Unconfigured> { unconfigured.sync(FESTIVAL_ID) }
        assertThrowsSuspend<PackSyncError.InvalidFestivalId> { service().updates("bad/id").toList() }
        assertThrowsSuspend<PackSyncError.InvalidFestivalId> { service().download("").toList() }
    }

    @Test
    fun `끊긴 연결 뒤 이전 ETag로 재접속해 최신 팩을 저장한다`() = runTest {
        val first = snapshotSocket(1, "\"one\"")
        val second = snapshotSocket(2, "\"two\"")
        val results = service(first, second).updates(FESTIVAL_ID).take(3).toList()

        assertEquals(listOf(1, null, 2), results.map { (it as? PackSyncResult.Updated)?.pack?.packVersion })
        assertEquals(PackSyncResult.Reconnecting, results[1])
        assertEquals("\"one\"", second.requests.single().ifNoneMatch())
        assertEquals(2, service().cachedPack(FESTIVAL_ID)?.packVersion)
    }

    @Test
    fun `조용한 감시 연결의 유휴 만료는 재접속 표시 없이 다시 연다`() = runTest {
        val quiet = ScriptSocket(waitWhenEmpty = true) { listOf(snapshot(it, 1, "\"one\"")) }
        val next = ScriptSocket(waitWhenEmpty = true) { request ->
            listOf(
                message("pack.not_modified", request.requestId, data("festivalId" to FESTIVAL_ID, "packVersion" to 1, "etag" to "\"one\"")),
            )
        }
        val results = service(quiet, next).updates(FESTIVAL_ID).take(2).toList()

        assertTrue(results[0] is PackSyncResult.Updated)
        assertTrue(results[1] is PackSyncResult.NotModified)
        // 유휴 60초 + 재접속 1초
        assertEquals(61_000L, testScheduler.currentTime)
        assertTrue(quiet.cancelCount > 0) // 유휴 만료로 닫은 연결은 다시 쓰지 않는다
    }

    @Test
    fun `첫 연결 실패는 재접속 표시 없이 지수 지연으로 다시 시도한다`() = runTest {
        val client =
            ScriptClient(
                listOf(snapshotSocket(1, "\"one\"")),
                failures = listOf(NetworkError.Offline(IOException()), NetworkError.Offline(IOException())),
            )
        val first = service(client).updates(FESTIVAL_ID).first()

        assertTrue(first is PackSyncResult.Updated)
        assertEquals(3_000L, testScheduler.currentTime) // 1초 + 2초
    }

    @Test
    fun `handshake 거부가 3번 이어지면 300초 뒤 다시 시도해 복구한다`() = runTest {
        val rejected = List(3) { WebSocketError.HandshakeRejected(IOException("502")) }
        val client = ScriptClient(listOf(snapshotSocket(1, "\"one\"")), failures = rejected)

        val first = service(client).updates(FESTIVAL_ID).first()

        assertTrue(first is PackSyncResult.Updated)
        assertEquals(4, client.paths.size)
        assertEquals(303_000L, testScheduler.currentTime) // 1초 + 2초 + 300초
    }

    @Test
    fun `서버 오류와 인증서 오류는 무한 재접속하지 않고 흐름을 끝낸다`() = runTest {
        val serverError = ScriptSocket { listOf(message("pack.error", it.requestId, data("code" to "INTERNAL"))) }
        assertThrowsSuspend<PackSyncError.Server> { service(serverError).updates(FESTIVAL_ID).toList() }

        val untrusted = ScriptClient(emptyList(), failures = listOf(NetworkError.Unknown(javax.net.ssl.SSLHandshakeException("cert"))))
        assertThrowsSuspend<NetworkError.Unknown> { service(untrusted).updates(FESTIVAL_ID).toList() }
        assertEquals(1, untrusted.paths.size)
    }

    @Test
    fun `응답 전 업데이트 알림을 받으면 저장된 ETag로 다시 요청하고 5번 이어지면 재시도로 표시한다`() = runTest {
        service(snapshotSocket(1, "\"one\"")).sync(FESTIVAL_ID)
        val socket = ScriptSocket { request ->
            listOf(
                message("pack.updated", null, data("festivalId" to FESTIVAL_ID)),
                message("pack.not_modified", request.requestId, data("festivalId" to FESTIVAL_ID, "packVersion" to 1, "etag" to "\"one\"")),
            )
        }

        val result = service(socket).sync(FESTIVAL_ID)

        assertEquals(PackSyncResult.RetryAfter(1.seconds), result)
        assertEquals(5, socket.requests.size)
        assertTrue(socket.requests.all { it.ifNoneMatch() == "\"one\"" })
    }

    @Test
    fun `완성된 청크는 해시와 순서 검증 후 저장하고 중간 알림이 오면 한 번 더 확인한다`() = runTest {
        val pack = packBytes(3)
        val socket = ScriptSocket { request ->
            if (request.getValue("data").jsonObject.ifNoneMatch() == "\"three\"") {
                listOf(
                    message(
                        "pack.not_modified",
                        request.requestId,
                        data(
                            "festivalId" to FESTIVAL_ID,
                            "packVersion" to 3,
                            "etag" to "\"three\"",
                        ),
                    ),
                )
            } else {
                val (start, chunk, complete) = chunked(request.requestId, pack, 3, "\"three\"")
                listOf(start, message("pack.updated", null, data("festivalId" to FESTIVAL_ID)), chunk, complete)
            }
        }

        val result = service(socket).sync(FESTIVAL_ID)

        assertEquals(3, (result as PackSyncResult.Updated).pack.packVersion)
        assertEquals(2, socket.requests.size)
    }

    @Test
    fun `청크 일부 또는 잘못된 SHA-256은 기존 팩을 교체하지 않는다`() = runTest {
        service(snapshotSocket(1, "\"one\"")).sync(FESTIVAL_ID)
        for (partial in listOf(true, false)) {
            val failing = ScriptSocket { request ->
                val messages =
                    chunked(
                        request.requestId,
                        packBytes(2),
                        2,
                        "\"two\"",
                        sha256 = if (partial) PackWire.sha256Hex(packBytes(2)) else "0".repeat(64),
                    )
                if (partial) messages.take(1) else messages
            }
            assertThrowsSuspend<Exception> { service(failing).sync(FESTIVAL_ID) }
            assertEquals(1, service().cachedPack(FESTIVAL_ID)?.packVersion)
            assertEquals("\"one\"", failing.requests.first().ifNoneMatch())
        }
    }

    @Test
    fun `응답 시간 초과는 기존 팩을 유지한다`() = runTest {
        service(snapshotSocket(1, "\"one\"")).sync(FESTIVAL_ID)
        val silent = ScriptSocket(waitWhenEmpty = true)

        assertThrowsSuspend<PackSyncError.Timeout> { service(silent).sync(FESTIVAL_ID) }
        assertEquals(15_000L, testScheduler.currentTime)
        assertEquals(1, service().cachedPack(FESTIVAL_ID)?.packVersion)
    }

    @Test
    fun `변경 없음은 확인 시각을 새로 남기고 재시도 · 서버 오류는 저장 팩을 보존한다`() = runTest {
        service(snapshotSocket(1, "\"one\"")).sync(FESTIVAL_ID)
        val firstChecked = service().cachedPack(FESTIVAL_ID)?.checkedAtMillis
        Thread.sleep(5)
        val notModified = ScriptSocket {
            message(
                "pack.not_modified",
                it.requestId,
                data(
                    "festivalId" to FESTIVAL_ID,
                    "packVersion" to 1,
                    "etag" to "\"one\"",
                ),
            ).let(::listOf)
        }
        val result = service(notModified).sync(FESTIVAL_ID)
        assertTrue(result is PackSyncResult.NotModified)
        assertTrue(requireNotNull(service().cachedPack(FESTIVAL_ID)?.checkedAtMillis) > requireNotNull(firstChecked))

        val retry = ScriptSocket { listOf(message("pack.retry", it.requestId, data("festivalId" to FESTIVAL_ID, "retryAfterMs" to 1500))) }
        assertEquals(PackSyncResult.RetryAfter(1.5.seconds), service(retry).sync(FESTIVAL_ID))
        val busy = ScriptSocket { listOf(message("pack.error", it.requestId, data("code" to "PACK_BUSY", "retryAfterMs" to 2000))) }
        assertEquals(PackSyncResult.RetryAfter(2.seconds), service(busy).sync(FESTIVAL_ID))
        assertEquals(1, service().cachedPack(FESTIVAL_ID)?.packVersion)
    }

    @Test
    fun `다른 요청의 응답이나 저장 팩과 다른 변경 없음은 잘못된 메시지다`() = runTest {
        val wrongRequest = ScriptSocket { listOf(snapshot(it, 1, "\"one\"").replace(it.requestId, "other")) }
        assertThrowsSuspend<PackSyncError.InvalidMessage> { service(wrongRequest).sync(FESTIVAL_ID) }
        val noStored =
            ScriptSocket {
                listOf(
                    message(
                        "pack.not_modified",
                        it.requestId,
                        data(
                            "festivalId" to FESTIVAL_ID,
                            "packVersion" to 1,
                            "etag" to "\"one\"",
                        ),
                    ),
                )
            }
        assertThrowsSuspend<PackSyncError.InvalidMessage> { service(noStored).sync(FESTIVAL_ID) }
    }

    @Test
    fun `텍스트가 아닌 메시지는 잘못된 메시지로 처리하고 연결을 닫는다`() = runTest {
        val socket = ScriptSocket(emptyError = { WebSocketError.NonTextMessage() })
        assertThrowsSuspend<PackSyncError.InvalidMessage> { service(socket).sync(FESTIVAL_ID) }
        assertEquals(1, socket.cancelCount)
    }

    @Test
    fun `단발 다운로드는 검증된 청크의 누적 바이트와 저장 완료를 순서대로 보낸다`() = runTest {
        val pack = packBytes(4, padding = 50_000)
        val socket = ScriptSocket { chunked(it.requestId, pack, 4, "\"four\"") }

        val events = service(socket).download(FESTIVAL_ID).toList()

        assertEquals(PackDownloadEvent.Progress(PackDownloadProgress(49_152, pack.size)), events[0])
        assertEquals(PackDownloadEvent.Progress(PackDownloadProgress(pack.size, pack.size)), events[1])
        assertEquals(4, ((events[2] as PackDownloadEvent.Finished).result as PackSyncResult.Updated).pack.packVersion)
        assertEquals(3, events.size)
        assertEquals(1, socket.cancelCount)
    }

    @Test
    fun `단일 스냅샷은 진행률 없이 저장 완료만 보내고 4MB 넘는 팩은 청크로 받는다`() = runTest {
        val single = service(snapshotSocket(1, "\"one\"")).download(FESTIVAL_ID).toList()
        assertEquals(1, single.size)

        val big = packBytes(5, padding = PackWire.MAX_MESSAGE_BYTES)
        val events = service(ScriptSocket { chunked(it.requestId, big, 5, "\"five\"") }).download(FESTIVAL_ID).toList()
        assertEquals(5, ((events.last() as PackDownloadEvent.Finished).result as PackSyncResult.Updated).pack.packVersion)
        assertEquals(big.size, (events[events.size - 2] as PackDownloadEvent.Progress).progress.receivedBytes)
    }

    @Test
    fun `전송 중 서버 갱신 알림으로 다시 요청하면 이전 진행률을 지운다`() = runTest {
        val pack = packBytes(2)
        var sent = 0
        val socket = ScriptSocket { request ->
            sent += 1
            val (start, chunk, complete) = chunked(request.requestId, pack, 2, "\"two\"")
            if (sent ==
                1
            ) {
                listOf(start, chunk, message("pack.updated", null, data("festivalId" to FESTIVAL_ID)), complete)
            } else {
                listOf(start, chunk, complete)
            }
        }

        val events = service(socket).download(FESTIVAL_ID).toList()

        assertEquals(PackDownloadEvent.Reset, events[1])
        assertTrue(events.last() is PackDownloadEvent.Finished)
    }

    @Test
    fun `단발 다운로드의 PACK_BUSY는 최초 요청 포함 세 번만 시도한다`() = runTest {
        val socket = ScriptSocket { listOf(message("pack.error", it.requestId, data("code" to "PACK_BUSY", "retryAfterMs" to 1000))) }

        val error = runCatching { service(socket).download(FESTIVAL_ID).toList() }.exceptionOrNull()

        assertEquals("PACK_BUSY", (error as PackSyncError.Server).code)
        assertEquals(3, socket.requests.size)
        assertEquals(2_000L, testScheduler.currentTime)
    }

    @Test
    fun `서버 재시도 간격이 남은 예산보다 길면 기다리지 않고 시간 초과다`() = runTest {
        val socket =
            ScriptSocket { listOf(message("pack.retry", it.requestId, data("festivalId" to FESTIVAL_ID, "retryAfterMs" to 31_000))) }
        assertThrowsSuspend<PackSyncError.Timeout> { service(socket).download(FESTIVAL_ID).toList() }
        assertEquals(1, socket.requests.size)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `단발 다운로드 취소는 대기 중인 웹소켓을 닫는다`() = runTest {
        val socket = ScriptSocket(waitWhenEmpty = true)
        val job = async { service(socket).download(FESTIVAL_ID).toList() }
        runCurrent()
        job.cancel()
        runCurrent()
        assertEquals(1, socket.cancelCount)
    }

    @Test
    fun `응답을 기다리는 동안 팩을 지우면 늦은 응답이 팩을 되살리지 않는다`() = runTest {
        val pendingSocket = ScriptSocket(waitWhenEmpty = true)
        val syncService = service(ScriptClient(listOf(snapshotSocket(1, "\"one\""), pendingSocket)))
        syncService.sync(FESTIVAL_ID)
        val pending = async { runCatching { syncService.sync(FESTIVAL_ID) } }
        runCurrent()

        syncService.deletePack(FESTIVAL_ID)
        pendingSocket.push(snapshot(pendingSocket.sent.single(), 2, "\"two\""))

        assertTrue(pending.await().exceptionOrNull() is PackSyncError.PackDeleted)
        assertNull(syncService.cachedPack(FESTIVAL_ID))
    }

    @Test
    fun `감시 중 팩을 지우면 업데이트 알림을 받아도 다시 받지 않고 감시를 멈춘다`() = runTest {
        val socket = ScriptSocket(waitWhenEmpty = true) { listOf(snapshot(it, 1, "\"one\"")) }
        val syncService = service(socket)
        val results = async { syncService.updates(FESTIVAL_ID).toList() }
        runCurrent()

        syncService.deletePack(FESTIVAL_ID)
        socket.push(message("pack.updated", null, data("festivalId" to FESTIVAL_ID)))

        assertEquals(1, results.await().size)
        assertEquals(1, socket.requests.size)
        assertNull(syncService.cachedPack(FESTIVAL_ID))
    }

    @Test
    fun `구버전 응답은 최신 캐시를 덮지 않는다`() = runTest {
        service(snapshotSocket(3, "\"three\"")).sync(FESTIVAL_ID)
        val result = service(snapshotSocket(2, "\"two\"")).sync(FESTIVAL_ID)

        assertEquals(3, (result as PackSyncResult.NotModified).pack.packVersion)
        assertEquals(3, service().cachedPack(FESTIVAL_ID)?.packVersion)
    }
}
