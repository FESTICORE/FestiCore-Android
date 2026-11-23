package io.festicore.core.network

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
