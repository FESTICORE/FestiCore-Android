package io.festicore.service.pack

import io.festicore.core.network.NetworkError
import io.festicore.core.network.WebSocketError
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/** 검증과 디스크 저장을 마친 공개 팩. [pack]은 서버의 공개 팩 JSON 바이트다. iOS `CachedFestivalPack`. */
class CachedFestivalPack(
    val festivalId: String,
    val packVersion: Int,
    val etag: String,
    val pack: ByteArray,
    /** 서버와 마지막으로 버전을 맞춘 시각(epoch ms). 디스크에서 읽은 팩에만 있다. */
    val checkedAtMillis: Long? = null,
) {
    fun withCheckedAt(millis: Long) = CachedFestivalPack(festivalId, packVersion, etag, pack, millis)

    /** 확인 시각은 저장 메타데이터라 같은 팩인지 비교할 때 보지 않는다. */
    override fun equals(other: Any?): Boolean = other is CachedFestivalPack &&
        festivalId == other.festivalId &&
        packVersion == other.packVersion &&
        etag == other.etag &&
        pack.contentEquals(other.pack)

    override fun hashCode(): Int = ((festivalId.hashCode() * 31 + packVersion) * 31 + etag.hashCode()) * 31 + pack.contentHashCode()

    override fun toString(): String = "CachedFestivalPack($festivalId, v$packVersion, ${pack.size}B, checkedAt=$checkedAtMillis)"
}

sealed interface PackSyncResult {
    data class Updated(val pack: CachedFestivalPack) : PackSyncResult

    data class NotModified(val pack: CachedFestivalPack) : PackSyncResult

    data class RetryAfter(val after: Duration) : PackSyncResult

    /** 감시 중 연결이 끊겨 저장된 ETag로 다시 연결한다. 팩을 한 번이라도 받은 뒤에만 알린다. */
    data object Reconnecting : PackSyncResult
}

data class PackDownloadProgress(
    val receivedBytes: Int,
    val totalBytes: Int,
)

sealed interface PackDownloadEvent {
    /** 서버 갱신 알림이나 재시도로 새 요청을 시작할 때 이전 요청의 바이트를 지운다. */
    data object Reset : PackDownloadEvent

    data class Progress(val progress: PackDownloadProgress) : PackDownloadEvent

    /** 완성된 팩을 캐시에 저장하거나 기존 캐시를 검증한 뒤에만 보낸다. */
    data class Finished(val result: PackSyncResult) : PackDownloadEvent
}

/** 팩 WebSocket 동기화와 기기 저장. iOS `PackSyncService`. 실패는 [PackSyncError] · [NetworkError] · [WebSocketError] · `IOException`(저장소). */
interface PackSyncService {
    suspend fun cachedPack(festivalId: String): CachedFestivalPack?

    /** 무결성 검증을 통과한 로컬 팩 목록(축제 ID 순). 네트워크에 연결하지 않는다. 손상 파일만 빼고, 폴더 접근 오류는 던진다. */
    suspend fun cachedPacks(): List<CachedFestivalPack>

    /** 연결 하나로 한 번 맞추고 닫는다. */
    suspend fun sync(festivalId: String): PackSyncResult

    /** 사용자가 요청한 단발 받기. 청크 진행과 저장 완료를 순서대로 보낸다. `retryAfter`는 최대 2번, 합계 30초까지 기다린다. */
    fun download(festivalId: String): Flow<PackDownloadEvent>

    /** 기기에 저장한 팩을 지운다. 없으면 아무것도 하지 않는다. 진행 중인 응답과 감시가 지운 팩을 되살리지 않는다. */
    suspend fun deletePack(festivalId: String)

    /** 변경을 계속 따라간다. 연결이 끊기면 `Reconnecting`을 알리고 저장된 ETag로 다시 요청한다. 전송 실패가 아닌 오류는 흐름을 끝낸다. */
    fun updates(festivalId: String): Flow<PackSyncResult>
}

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
