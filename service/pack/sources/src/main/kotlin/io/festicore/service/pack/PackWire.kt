package io.festicore.service.pack

import io.festicore.core.network.FestiCoreWebSocket
import io.festicore.core.network.WebSocketError
import java.security.MessageDigest
import java.util.Base64
import kotlin.time.Duration
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** 팩 WebSocket 메시지 조립 · 해석 · 청크 조립. iOS `PackWire`. */
internal object PackWire {
    const val MAX_PACK_BYTES = 8 * 1024 * 1024
    const val MAX_MESSAGE_BYTES = 4 * 1024 * 1024
    const val MAX_REQUEST_BYTES = 1024
    const val CHUNK_BYTES = 48 * 1024
    const val MAX_ETAG_BYTES = 128

    class Message(
        val event: String,
        val requestId: String?,
        val data: JsonObject?,
    )

    /** `pack.get` 요청. 전체가 1024바이트를 넘으면 [PackSyncError.InvalidMessage]. */
    fun request(
        festivalId: String,
        requestId: String,
        etag: String?,
    ): String {
        val text = buildJsonObject {
            put("event", "pack.get")
            put("requestId", requestId)
            put(
                "data",
                buildJsonObject {
                    put("festivalId", festivalId)
                    etag?.let { put("ifNoneMatch", it) }
                },
            )
        }.toString()
        if (text.encodeToByteArray().size > MAX_REQUEST_BYTES) throw PackSyncError.InvalidMessage()
        return text
    }

    /** 받은 텍스트 하나. 4MB를 넘거나 JSON 객체가 아니거나 `event`가 없으면 [PackSyncError.InvalidMessage]. */
    fun decode(text: String): Message {
        if (text.length > MAX_MESSAGE_BYTES || text.encodeToByteArray().size > MAX_MESSAGE_BYTES) {
            throw PackSyncError.InvalidMessage()
        }
        val root = parseObject(text) ?: throw PackSyncError.InvalidMessage()
        val event = root.string("event") ?: throw PackSyncError.InvalidMessage()
        return Message(event, root.string("requestId"), root["data"] as? JsonObject)
    }

    /** `pack.snapshot` 본문. 팩 JSON은 8MB 이하이고 `id` · `packVersion`이 메시지와 같아야 한다. */
    fun cachedPack(message: Message, festivalId: String): CachedFestivalPack {
        val data = message.data ?: throw PackSyncError.InvalidPack()
        val version = data.int("packVersion")?.takeIf { it > 0 }
        val etag = data.string("etag")?.takeIf(::isValidEtag)
        val pack = data["pack"] as? JsonObject
        if (data.string("festivalId") != festivalId || version == null || etag == null || pack == null) {
            throw PackSyncError.InvalidPack()
        }
        val bytes = pack.toString().encodeToByteArray()
        if (bytes.size > MAX_PACK_BYTES || !isValidPack(bytes, festivalId, version)) throw PackSyncError.InvalidPack()
        return CachedFestivalPack(festivalId, version, etag, bytes)
    }

    /**
     * `pack.start` 뒤 `pack.chunk{index, base64}` × N → `pack.complete`를 받아 조립한다.
     * 청크는 순서 · requestId · 크기(`min(48KB, 남은 바이트)`)가 맞아야 하고, 끝에서 sha256(소문자 hex) · 팩 검증을 한다.
     * 중간의 `pack.updated`는 건너뛰고 힌트로만 돌려준다.
     */
    suspend fun chunkedPack(
        start: Message,
        socket: FestiCoreWebSocket,
        requestId: String,
        festivalId: String,
        timeout: Duration,
        onProgress: (suspend (PackDownloadProgress) -> Unit)? = null,
    ): Pair<CachedFestivalPack, Boolean> {
        val data = start.data ?: throw PackSyncError.InvalidChunk()
        val version = data.int("packVersion")?.takeIf { it > 0 }
        val etag = data.string("etag")?.takeIf(::isValidEtag)
        val total = data.int("totalBytes")?.takeIf { it in 1..MAX_PACK_BYTES }
        val count = data.int("chunkCount")
        val digest = data.string("sha256")
        if (data.string("festivalId") != festivalId || version == null || etag == null || total == null ||
            count != (total + CHUNK_BYTES - 1) / CHUNK_BYTES || digest == null || !isLowerHexDigest(digest)
        ) {
            throw PackSyncError.InvalidChunk()
        }
        val bytes = ByteArray(total)
        var received = 0
        var hadUpdateHint = false
        for (index in 0 until count) {
            val (chunk, hint) = transferMessage(socket, festivalId, timeout)
            hadUpdateHint = hadUpdateHint || hint
            val chunkData = chunk.data
            if (chunk.event != "pack.chunk" || chunk.requestId != requestId || chunkData == null ||
                chunkData.int("index") != index
            ) {
                throw PackSyncError.InvalidChunk()
            }
            val part = chunkData.string("base64")?.let(::decodeBase64) ?: throw PackSyncError.InvalidChunk()
            if (part.size != minOf(CHUNK_BYTES, total - received)) throw PackSyncError.InvalidChunk()
            part.copyInto(bytes, received)
            received += part.size
            onProgress?.invoke(PackDownloadProgress(received, total))
        }
        val (complete, hint) = transferMessage(socket, festivalId, timeout)
        hadUpdateHint = hadUpdateHint || hint
        if (complete.event != "pack.complete" || complete.requestId != requestId || received != total ||
            sha256Hex(bytes) != digest || !isValidPack(bytes, festivalId, version)
        ) {
            throw PackSyncError.InvalidChunk()
        }
        return CachedFestivalPack(festivalId, version, etag, bytes) to hadUpdateHint
    }

    /** [timeout] 안에 텍스트 하나를 받는다. 시간이 지나면 소켓을 닫고 [PackSyncError.Timeout], 텍스트가 아니면 [PackSyncError.InvalidMessage]. */
    suspend fun receiveText(socket: FestiCoreWebSocket, timeout: Duration): String = try {
        withTimeout(timeout) { socket.receive() }
    } catch (_: TimeoutCancellationException) {
        socket.cancel()
        throw PackSyncError.Timeout()
    } catch (_: WebSocketError.NonTextMessage) {
        throw PackSyncError.InvalidMessage()
    }

    private suspend fun transferMessage(
        socket: FestiCoreWebSocket,
        festivalId: String,
        timeout: Duration,
    ): Pair<Message, Boolean> {
        var hadUpdateHint = false
        while (true) {
            val message = decode(receiveText(socket, timeout))
            if (message.event != "pack.updated") return message to hadUpdateHint
            if (message.data?.string("festivalId") == festivalId) hadUpdateHint = true
        }
    }

    /** 팩 JSON이 객체이고 `id == festivalId`, `packVersion == version > 0`이다. iOS `PackCache.isValid`. */
    fun isValidPack(
        bytes: ByteArray,
        festivalId: String,
        version: Int,
    ): Boolean {
        if (version <= 0) return false
        val root = parseObject(bytes.decodeToString()) ?: return false
        return root.string("id") == festivalId && root.int("packVersion") == version
    }

    fun isValidEtag(etag: String): Boolean = etag.isNotEmpty() && etag.encodeToByteArray().size <= MAX_ETAG_BYTES

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun isLowerHexDigest(value: String) = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun decodeBase64(value: String): ByteArray? = try {
        Base64.getDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun parseObject(text: String): JsonObject? = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: IllegalArgumentException) { // SerializationException 포함
        null
    }
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
