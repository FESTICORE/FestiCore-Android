package io.festicore.service.pack

import io.festicore.core.network.FestiCoreWebSocket
import io.festicore.core.network.FestiCoreWebSocketClient
import io.festicore.core.network.WebSocketError
import io.festicore.service.api.festival.FestivalApi
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 팩 WebSocket(`api/v1/festivals/updates`) 동기화. iOS `NetworkPackSyncService`.
 * 요청 세대로 늦게 온 응답 · 삭제 뒤 응답이 최신 저장을 덮지 않게 한다. 세대와 저장은 [lock] 안에서만 바꾼다(iOS actor).
 */
class NetworkPackSyncService(
    private val cache: PackCache,
    private val client: FestiCoreWebSocketClient,
    private val responseTimeout: Duration = 15.seconds,
    private val idleTimeout: Duration = 60.seconds,
    private val rejectedHandshakeRetry: Duration = 300.seconds,
    private val downloadRetryBudget: Duration = 30.seconds,
    private val newRequestId: () -> String = { UUID.randomUUID().toString().lowercase() },
) : PackSyncService {
    private val lock = Mutex()
    private val requestGeneration = HashMap<String, Int>()
    private val committedGeneration = HashMap<String, Int>()

    /** 팩을 지운 횟수. 지우기 전에 시작한 감시가 팩을 다시 받지 않게 한다. */
    private val deletionCount = HashMap<String, Int>()

    override suspend fun cachedPack(festivalId: String): CachedFestivalPack? = cache.load(festivalId)

    override suspend fun cachedPacks(): List<CachedFestivalPack> = cache.loadAll()

    override suspend fun sync(festivalId: String): PackSyncResult {
        val socket = connect()
        try {
            return synchronize(festivalId, socket)
        } finally {
            socket.cancel()
        }
    }

    override fun download(festivalId: String): Flow<PackDownloadEvent> = channelFlow {
        if (!isValidFestivalId(festivalId)) throw PackSyncError.InvalidFestivalId()
        val socket = connect()
        try {
            var attempts = 0
            var remainingBudget = downloadRetryBudget
            while (true) {
                currentCoroutineContext().ensureActive()
                if (attempts > 0) send(PackDownloadEvent.Reset)
                attempts += 1
                val result = synchronize(
                    festivalId,
                    socket,
                    onProgress = { send(PackDownloadEvent.Progress(it)) },
                    onReset = { send(PackDownloadEvent.Reset) },
                )
                when (result) {
                    is PackSyncResult.Updated, is PackSyncResult.NotModified -> {
                        send(PackDownloadEvent.Finished(result))
                        return@channelFlow
                    }

                    is PackSyncResult.RetryAfter -> {
                        if (attempts >= MAX_DOWNLOAD_ATTEMPTS) throw PackSyncError.Server(PACK_BUSY)
                        // 서버가 지정한 간격을 줄이면 같은 바쁜 팩에 너무 빨리 다시 요청한다.
                        if (!result.after.isPositive() || result.after > remainingBudget) throw PackSyncError.Timeout()
                        delay(result.after)
                        remainingBudget -= result.after
                    }

                    PackSyncResult.Reconnecting -> throw PackSyncError.InvalidMessage()
                }
            }
        } finally {
            socket.cancel()
        }
    }

    override suspend fun deletePack(festivalId: String) {
        lock.withLock {
            cache.delete(festivalId)
            // 진행 중이던 응답이 지운 팩을 다시 저장하지 않게 세대를 올린다.
            val generation = (requestGeneration[festivalId] ?: 0) + 1
            requestGeneration[festivalId] = generation
            committedGeneration[festivalId] = generation
            deletionCount[festivalId] = (deletionCount[festivalId] ?: 0) + 1
        }
    }

    override fun updates(festivalId: String): Flow<PackSyncResult> = channelFlow {
        if (!isValidFestivalId(festivalId)) throw PackSyncError.InvalidFestivalId()
        val backoff = ReconnectBackoff()
        var rejectedHandshakes = 0
        var hasReceivedPack = false
        val deletionsAtStart = lock.withLock { deletionCount[festivalId] ?: 0 }
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                stopIfDeleted(festivalId, deletionsAtStart)
                val socket = connect()
                try {
                    watch(festivalId, socket, deletionsAtStart) { result ->
                        backoff.reset()
                        rejectedHandshakes = 0
                        if (result is PackSyncResult.Updated || result is PackSyncResult.NotModified) hasReceivedPack = true
                        send(result)
                    }
                } finally {
                    socket.cancel()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: WatchStopped) {
                return@channelFlow
            } catch (_: PackSyncError.PackDeleted) {
                return@channelFlow
            } catch (_: IdleTimeout) {
                // 조용한 연결의 만료는 오류 표시 없이 다시 연다.
                delay(backoff.nextDelay())
            } catch (e: Throwable) {
                if (!PackSyncError.isTransportFailure(e)) throw e
                if (e is WebSocketError.HandshakeRejected) {
                    rejectedHandshakes += 1
                    if (rejectedHandshakes >= MAX_REJECTED_HANDSHAKES) {
                        // 짧은 서버 배포 동안 감시가 멈춘 채 남지 않게 느리게 계속 시도한다.
                        delay(rejectedHandshakeRetry)
                        continue
                    }
                }
                // 끊긴 연결은 저장된 팩을 버리지 않고 그 ETag로 다시 요청한다.
                if (hasReceivedPack) send(PackSyncResult.Reconnecting)
                delay(backoff.nextDelay())
            }
        }
    }

    /** 한 연결에서 맞추고 `pack.updated`를 기다리기를 반복한다. 유휴 [idleTimeout]이 지나면 [IdleTimeout]. */
    private suspend fun ProducerScope<PackSyncResult>.watch(
        festivalId: String,
        socket: FestiCoreWebSocket,
        deletionsAtStart: Int,
        onResult: suspend ProducerScope<PackSyncResult>.(PackSyncResult) -> Unit,
    ) {
        while (true) {
            stopIfDeleted(festivalId, deletionsAtStart)
            val result = synchronize(festivalId, socket)
            onResult(result)
            if (result is PackSyncResult.RetryAfter) {
                delay(result.after.coerceIn(100.milliseconds, 30.seconds))
                continue
            }
            while (true) {
                val text = try {
                    PackWire.receiveText(socket, idleTimeout)
                } catch (_: PackSyncError.Timeout) {
                    throw IdleTimeout()
                }
                val message = PackWire.decode(text)
                if (message.event == "pack.updated" && message.data?.string("festivalId") == festivalId) break
            }
        }
    }

    private suspend fun stopIfDeleted(festivalId: String, since: Int) {
        if (lock.withLock { deletionCount[festivalId] ?: 0 } != since) throw WatchStopped()
    }

    /** 응답 대기 중 업데이트 힌트가 오면 저장된 ETag로 다시 요청한다. 5번 연속이면 `retryAfter(1초)`. */
    private suspend fun synchronize(
        festivalId: String,
        socket: FestiCoreWebSocket,
        onProgress: (suspend (PackDownloadProgress) -> Unit)? = null,
        onReset: (suspend () -> Unit)? = null,
    ): PackSyncResult {
        var receivedSnapshot = false
        repeat(MAX_REQUESTS_PER_SYNC) { requestIndex ->
            if (requestIndex > 0) onReset?.invoke()
            val reply = requestPack(festivalId, socket, onProgress)
            if (reply.result is PackSyncResult.Updated) receivedSnapshot = true
            if (!reply.hadUpdateHint) {
                if (receivedSnapshot && reply.result is PackSyncResult.NotModified) return PackSyncResult.Updated(reply.result.pack)
                return reply.result
            }
        }
        // 서버가 이 연결이 따라잡는 것보다 빨리 발행하고 있다.
        return PackSyncResult.RetryAfter(1.seconds)
    }

    private class Reply(
        val result: PackSyncResult,
        val hadUpdateHint: Boolean,
    )

    private suspend fun requestPack(
        festivalId: String,
        socket: FestiCoreWebSocket,
        onProgress: (suspend (PackDownloadProgress) -> Unit)?,
    ): Reply {
        // 손상 파일은 없는 것으로 보고 ETag 없이 다시 받는다(저장 때 덮어쓴다).
        val stored = cache.loadIntact(festivalId)
        val generation = lock.withLock {
            ((requestGeneration[festivalId] ?: 0) + 1).also { requestGeneration[festivalId] = it }
        }
        val requestId = newRequestId()
        socket.send(PackWire.request(festivalId, requestId, stored?.etag))
        var hadUpdateHint = false
        while (true) {
            val message = PackWire.decode(PackWire.receiveText(socket, responseTimeout))
            if (message.event == "pack.updated") {
                if (message.data?.string("festivalId") == festivalId) hadUpdateHint = true
                continue
            }
            if (message.requestId != requestId) throw PackSyncError.InvalidMessage()
            val data = message.data
            when (message.event) {
                "pack.snapshot" -> {
                    val updated = PackWire.cachedPack(message, festivalId)
                    return Reply(store(updated, generation), hadUpdateHint)
                }

                "pack.start" -> {
                    val (updated, chunkHint) = PackWire.chunkedPack(message, socket, requestId, festivalId, responseTimeout, onProgress)
                    return Reply(store(updated, generation), hadUpdateHint || chunkHint)
                }

                "pack.not_modified" -> {
                    if (stored == null || data?.string("festivalId") != festivalId ||
                        data.int("packVersion") != stored.packVersion || data.string("etag") != stored.etag
                    ) {
                        throw PackSyncError.InvalidMessage()
                    }
                    return Reply(PackSyncResult.NotModified(markChecked(stored, generation)), hadUpdateHint)
                }

                "pack.retry" -> {
                    val milliseconds = data?.int("retryAfterMs")
                    if (data?.string("festivalId") != festivalId || milliseconds == null || milliseconds <= 0) {
                        throw PackSyncError.InvalidMessage()
                    }
                    return Reply(PackSyncResult.RetryAfter(milliseconds.milliseconds), false)
                }

                "pack.error" -> {
                    val code = data?.string("code") ?: throw PackSyncError.InvalidMessage()
                    val milliseconds = data.int("retryAfterMs")
                    if (code == PACK_BUSY && milliseconds != null && milliseconds > 0) {
                        return Reply(PackSyncResult.RetryAfter(milliseconds.milliseconds), false)
                    }
                    throw PackSyncError.Server(code)
                }

                else -> throw PackSyncError.InvalidMessage()
            }
        }
    }

    private suspend fun markChecked(stored: CachedFestivalPack, generation: Int): CachedFestivalPack = lock.withLock {
        val festivalId = stored.festivalId
        if (generation < (committedGeneration[festivalId] ?: 0)) {
            // 더 늦게 시작한 요청이나 삭제가 이미 반영됐다. 세대를 내리거나 지운 팩을 되살리지 않는다.
            return@withLock cache.load(festivalId) ?: throw PackSyncError.PackDeleted()
        }
        runCatching { cache.markChecked(festivalId) }
        committedGeneration[festivalId] = generation
        cache.loadIntact(festivalId) ?: stored
    }

    private suspend fun store(updated: CachedFestivalPack, generation: Int): PackSyncResult = lock.withLock {
        val festivalId = updated.festivalId
        if (generation < (committedGeneration[festivalId] ?: 0)) {
            val latest = cache.load(festivalId) ?: throw PackSyncError.PackDeleted()
            return@withLock PackSyncResult.NotModified(latest)
        }
        val saved = cache.save(updated)
        committedGeneration[festivalId] = generation
        if (saved == updated) PackSyncResult.Updated(saved) else PackSyncResult.NotModified(saved)
    }

    /** 앱 서버 주소가 없거나 wss로 바꿀 수 없으면 [PackSyncError.Unconfigured]. */
    private suspend fun connect(): FestiCoreWebSocket = try {
        client.connect(FestivalApi.PACK_UPDATES)
    } catch (_: WebSocketError.InvalidUrl) {
        throw PackSyncError.Unconfigured()
    }

    private class IdleTimeout : Exception()

    private class WatchStopped : Exception()

    private companion object {
        const val PACK_BUSY = "PACK_BUSY"
        const val MAX_DOWNLOAD_ATTEMPTS = 3
        const val MAX_REJECTED_HANDSHAKES = 3
        const val MAX_REQUESTS_PER_SYNC = 5
    }
}
