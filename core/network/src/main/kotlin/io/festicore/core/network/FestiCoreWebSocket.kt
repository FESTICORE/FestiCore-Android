package io.festicore.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 열린 텍스트 WebSocket 연결. iOS `FestiCoreWebSocketConnection`.
 * 연결이 닫히면 [receive] · [send]가 [WebSocketError.Closed](또는 [NetworkError.Offline])를 던진다. 열린 뒤 끊긴 연결을 몰래 다시 열지 않는다.
 */
interface FestiCoreWebSocket {
    suspend fun send(text: String)

    /** 다음 텍스트 메시지. 텍스트가 아니면 [WebSocketError.NonTextMessage]. */
    suspend fun receive(): String

    /** 연결을 닫는다. 기다리던 [receive]는 오류로 끝난다. */
    fun cancel()
}

/** [path](앞 `/` 없음)로 handshake를 마친 연결을 연다. 인증 헤더는 붙이지 않는다. iOS `FestiCoreWebSocketClient`. */
fun interface FestiCoreWebSocketClient {
    suspend fun connect(path: String): FestiCoreWebSocket
}

/** WebSocket 전용 오류. 연결 · 입출력 실패는 [NetworkError]를 그대로 쓴다. */
sealed class WebSocketError(cause: Throwable? = null) : Exception(cause) {
    /** `BASE_URL`이 http(s) 절대 주소가 아니다. */
    class InvalidUrl(cause: Throwable? = null) : WebSocketError(cause)

    /** 서버가 101이 아닌 응답으로 handshake를 거부했다. iOS `URLError.badServerResponse`. */
    class HandshakeRejected(cause: Throwable) : WebSocketError(cause)

    class NonTextMessage : WebSocketError()

    /** 서버나 네트워크가 연결을 닫았다. iOS `ENOTCONN`. */
    class Closed(cause: Throwable? = null) : WebSocketError(cause)
}

/** [baseUrl]의 https를 wss로(테스트 서버의 http는 ws로) 바꾸고 [path]를 붙인다. iOS `NetworkClient.makeWebSocketURLRequest`. */
fun webSocketUrl(baseUrl: String, path: String): String {
    val uri = try {
        URI(baseUrl)
    } catch (e: URISyntaxException) {
        throw WebSocketError.InvalidUrl(e)
    }
    val scheme = when (uri.scheme?.lowercase()) {
        "https" -> "wss"
        "http" -> "ws"
        else -> throw WebSocketError.InvalidUrl()
    }
    if (uri.host.isNullOrEmpty()) throw WebSocketError.InvalidUrl()
    val base = baseUrl.substring(baseUrl.indexOf(':')).trimEnd('/')
    return "$scheme$base/${path.trimStart('/')}"
}

/**
 * Ktor `WebSockets` 플러그인 + OkHttp 엔진 클라이언트. 앱 API 클라이언트와 따로 둬 인증 · JSON 플러그인을 거치지 않는다.
 * 테스트는 [FestiCoreWebSocketClient] 가짜 구현을 쓴다(MockEngine은 WebSocket을 지원하지 않는다).
 */
fun createFestiCoreWebSocketClient(baseUrl: String, engine: HttpClientEngine = OkHttp.create()): FestiCoreWebSocketClient {
    val client = HttpClient(engine) { install(WebSockets) }
    return FestiCoreWebSocketClient { path ->
        val url = webSocketUrl(baseUrl, path)
        val session = try {
            client.webSocketSession(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw webSocketHandshakeError(e)
        }
        KtorWebSocket(session)
    }
}

/** handshake 실패 분류. OkHttp는 101이 아닌 응답을 `ProtocolException`, Ktor는 401을 [WebSocketException]으로 알린다. */
internal fun webSocketHandshakeError(error: Throwable): Throwable = when (error) {
    is ProtocolException, is WebSocketException -> WebSocketError.HandshakeRejected(error)
    else -> webSocketTransportError(error)
}

/** 열린 연결의 송수신 실패 분류. 인증서 문제(`SSLException`)는 연결 끊김으로 보지 않는다. */
internal fun webSocketTransportError(error: Throwable): Throwable = when (error) {
    is NetworkError, is WebSocketError -> error
    is ClosedReceiveChannelException, is ClosedSendChannelException -> WebSocketError.Closed(error)
    is SSLException -> NetworkError.Unknown(error)
    is SocketTimeoutException -> NetworkError.Timeout(error)
    is IOException -> NetworkError.Offline(error)
    else -> NetworkError.Unknown(error)
}

private class KtorWebSocket(private val session: DefaultClientWebSocketSession) : FestiCoreWebSocket {
    override suspend fun send(text: String) {
        guard { session.send(Frame.Text(text)) }
    }

    override suspend fun receive(): String {
        while (true) {
            when (val frame = guard { session.incoming.receive() }) {
                is Frame.Text -> return frame.readText()
                is Frame.Binary -> throw WebSocketError.NonTextMessage()
                else -> continue // ping · pong · close는 세션이 처리한다.
            }
        }
    }

    override fun cancel() {
        session.cancel()
    }

    private suspend inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        // 호출한 쪽이 취소됐으면 그대로, 세션만 닫혔으면 끊긴 연결로 알린다.
        currentCoroutineContext().ensureActive()
        throw WebSocketError.Closed(e)
    } catch (e: Throwable) {
        throw webSocketTransportError(e)
    }
}
