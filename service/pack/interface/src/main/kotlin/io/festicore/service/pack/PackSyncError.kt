package io.festicore.service.pack

import io.festicore.core.network.error.NetworkError
import io.festicore.core.network.websocket.WebSocketError

/** 팩 동기화 실패 이유. iOS `PackSyncError`. */
sealed class PackSyncError(message: String? = null) : Exception(message) {
    /** 앱 서버 주소가 없거나 wss로 바꿀 수 없다. */
    class Unconfigured : PackSyncError()

    class InvalidFestivalId : PackSyncError()

    class InvalidMessage : PackSyncError()

    class InvalidPack : PackSyncError()

    class InvalidChunk : PackSyncError()

    /** 응답 대기(15초) 시간 초과. */
    class Timeout : PackSyncError()

    class PackTooLarge : PackSyncError()

    /** 응답을 기다리는 동안 팩이 지워졌다. */
    class PackDeleted : PackSyncError()

    /** `pack.error`의 `code`(예: `PACK_BUSY` · `FESTIVAL_NOT_FOUND`). */
    class Server(val code: String) : PackSyncError(code)

    companion object {
        /** 연결 · 전송 실패나 응답 시간 초과인지. 감시 재접속과 도메인의 오프라인 판단이 함께 쓴다. iOS `isTransportFailure`. */
        fun isTransportFailure(error: Throwable): Boolean = when (error) {
            is Timeout,
            is WebSocketError.Closed,
            is WebSocketError.HandshakeRejected,
            is NetworkError.Offline,
            is NetworkError.Timeout,
            -> true

            else -> false
        }
    }
}
