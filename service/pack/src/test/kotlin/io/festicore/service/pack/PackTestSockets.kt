package io.festicore.service.pack

import io.festicore.core.network.FestiCoreWebSocket
import io.festicore.core.network.FestiCoreWebSocketClient
import io.festicore.core.network.WebSocketError
import java.util.Base64
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal const val FESTIVAL_ID = "fst_example"

/** iOS `ScriptSocket`. 요청을 받으면 [response]가 돌려준 메시지를 차례로 내보낸다. 비면 [emptyError]로 끝나거나([waitWhenEmpty]) 기다린다. */
internal class ScriptSocket(
    private val waitWhenEmpty: Boolean = false,
    private val emptyError: () -> Throwable = { WebSocketError.Closed() },
    private val response: (JsonObject) -> List<String> = { emptyList() },
) : FestiCoreWebSocket {
    private val messages = ArrayDeque<String>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** 보낸 요청 전체 · 그 `data`. */
    val sent = mutableListOf<JsonObject>()
    val requests = mutableListOf<JsonObject>()
    var cancelCount = 0
        private set

    override suspend fun send(text: String) {
        val request = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
        sent += request
        requests += request.getValue("data").jsonObject
        messages.addAll(response(request))
        wake.trySend(Unit)
    }

    override suspend fun receive(): String {
        while (true) {
            messages.removeFirstOrNull()?.let { return it }
            if (cancelCount > 0) throw WebSocketError.Closed()
            if (!waitWhenEmpty) throw emptyError()
            wake.receive()
        }
    }

    override fun cancel() {
        cancelCount += 1
        wake.trySend(Unit)
    }

    /** 서버가 요청 없이 보내는 메시지. */
    fun push(text: String) {
        messages.addLast(text)
        wake.trySend(Unit)
    }
}

/** iOS `SocketFactory`. [failures]를 먼저 던지고, 그다음 [sockets]를 차례로 연다. 다 쓰면 빈 소켓. */
internal class ScriptClient(
    sockets: List<ScriptSocket>,
    failures: List<Throwable> = emptyList(),
) : FestiCoreWebSocketClient {
    private val sockets = ArrayDeque(sockets)
    private val failures = ArrayDeque(failures)
    val paths = mutableListOf<String>()

    override suspend fun connect(path: String): FestiCoreWebSocket {
        paths += path
        failures.removeFirstOrNull()?.let { throw it }
        return sockets.removeFirstOrNull() ?: ScriptSocket()
    }
}

internal fun message(
    event: String,
    requestId: String?,
    data: JsonObject?,
): String = buildJsonObject {
    put("event", event)
    requestId?.let { put("requestId", it) }
    data?.let { put("data", it) }
}.toString()

internal fun data(vararg pairs: Pair<String, Any>): JsonObject = JsonObject(pairs.associate { (k, v) -> k to v.toJson() })

private fun Any.toJson(): JsonElement = when (this) {
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> error("지원하지 않는 값 $this")
}

internal val JsonObject.requestId: String get() = getValue("requestId").jsonPrimitive.content

internal fun JsonObject.ifNoneMatch(): String? = (this["ifNoneMatch"] as? JsonPrimitive)?.content

internal fun packBytes(
    version: Int,
    id: String = FESTIVAL_ID,
    padding: Int = 0,
): ByteArray {
    val pad = if (padding > 0) ",\"padding\":\"${"x".repeat(padding)}\"" else ""
    return "{\"id\":\"$id\",\"packVersion\":$version$pad}".encodeToByteArray()
}

internal fun snapshot(
    request: JsonObject,
    version: Int,
    etag: String,
): String = message(
    "pack.snapshot",
    request.requestId,
    data(
        "festivalId" to FESTIVAL_ID,
        "packVersion" to version,
        "etag" to etag,
        "pack" to data("id" to FESTIVAL_ID, "packVersion" to version),
    ),
)

/** `pack.start` + 48KB 청크 + `pack.complete`. */
internal fun chunked(
    requestId: String,
    pack: ByteArray,
    version: Int,
    etag: String,
    sha256: String = PackWire.sha256Hex(pack),
): List<String> {
    val chunks = (pack.indices step PackWire.CHUNK_BYTES).mapIndexed { index, offset ->
        val part = pack.copyOfRange(offset, minOf(offset + PackWire.CHUNK_BYTES, pack.size))
        message("pack.chunk", requestId, data("index" to index, "base64" to Base64.getEncoder().encodeToString(part)))
    }
    val start = message(
        "pack.start",
        requestId,
        data(
            "festivalId" to FESTIVAL_ID,
            "packVersion" to version,
            "etag" to etag,
            "totalBytes" to pack.size,
            "chunkCount" to chunks.size,
            "sha256" to sha256,
        ),
    )
    return listOf(start) + chunks + message("pack.complete", requestId, null)
}
