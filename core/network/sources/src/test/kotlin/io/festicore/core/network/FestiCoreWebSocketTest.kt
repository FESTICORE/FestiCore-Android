package io.festicore.core.network

import io.ktor.client.plugins.websocket.WebSocketException
import java.io.EOFException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FestiCoreWebSocketTest {
    @Test
    fun `https는 wss로 http는 ws로 바꾸고 경로를 붙인다`() {
        assertEquals(
            "wss://festicore-dev.up.railway.app/api/v1/festivals/updates",
            webSocketUrl("https://festicore-dev.up.railway.app/", "api/v1/festivals/updates"),
        )
        assertEquals("ws://localhost:8080/base/api/x", webSocketUrl("http://localhost:8080/base/", "/api/x"))
    }

    @Test
    fun `http(s) 절대 주소가 아니면 InvalidUrl이다`() {
        listOf("", "ftp://host/", "festicore.app/", "https:///nohost", "ht tp://bad").forEach { base ->
            val error = runCatching { webSocketUrl(base, "api") }.exceptionOrNull()
            assertTrue("$base → $error", error is WebSocketError.InvalidUrl)
        }
    }

    @Test
    fun `101이 아닌 handshake 응답은 거부로 분류한다`() {
        assertTrue(
            webSocketHandshakeError(ProtocolException("Expected HTTP 101 response but was '404'")) is WebSocketError.HandshakeRejected,
        )
        assertTrue(
            webSocketHandshakeError(
                WebSocketException("Handshake exception, expected status code 101 but was 401"),
            ) is WebSocketError.HandshakeRejected,
        )
        assertTrue(webSocketHandshakeError(EOFException()) is NetworkError.Offline)
    }

    @Test
    fun `송수신 실패는 끊김 · 오프라인 · 시간 초과로 나누고 인증서 오류는 끊김으로 보지 않는다`() {
        assertTrue(webSocketTransportError(ClosedReceiveChannelException(null)) is WebSocketError.Closed)
        assertTrue(webSocketTransportError(EOFException()) is NetworkError.Offline)
        assertTrue(webSocketTransportError(SocketTimeoutException()) is NetworkError.Timeout)
        assertTrue(webSocketTransportError(SSLHandshakeException("cert")) is NetworkError.Unknown)
        assertTrue(webSocketTransportError(IllegalStateException()) is NetworkError.Unknown)
    }
}
