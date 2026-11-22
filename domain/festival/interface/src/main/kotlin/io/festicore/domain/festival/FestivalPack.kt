package io.festicore.domain.festival

import java.time.Instant
import kotlin.time.Duration

/**
 * 기기에 받은 축제 팩. [document]는 팩 JSON을 해석한 문서이고, 해석하지 못하면 null(팩 저장은 유지).
 * iOS `CachedFestivalPack`(바이트 대신 해석한 문서를 둔다).
 */
data class CachedFestivalPack(
    val festivalId: String,
    val packVersion: Int,
    val etag: String,
    /** 서버와 마지막으로 버전을 맞춘 시각. */
    val checkedAt: Instant?,
    val document: FestivalPackDocument?,
)

/** 팩 맞추기 결과. iOS `PackSyncResult`. */
sealed interface PackSyncResult {
    data class Updated(val pack: CachedFestivalPack) : PackSyncResult

    data class NotModified(val pack: CachedFestivalPack) : PackSyncResult

    data class RetryAfter(val after: Duration) : PackSyncResult

    /** 감시 연결이 끊겨 저장된 팩으로 버티며 다시 연결하는 중. */
    data object Reconnecting : PackSyncResult
}

data class PackDownloadProgress(
    val receivedBytes: Int,
    val totalBytes: Int,
) {
    /** 0.0~1.0. */
    val fraction: Float get() = if (totalBytes <= 0) 0f else (receivedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

/** 단발 팩 받기 이벤트. iOS `PackDownloadEvent`. */
sealed interface PackDownloadEvent {
    /** 재요청을 시작해 이전 진행률을 지운다. */
    data object Reset : PackDownloadEvent

    data class Progress(val progress: PackDownloadProgress) : PackDownloadEvent

    /** 저장(또는 기존 팩 확인)까지 끝났다. */
    data class Finished(val result: PackSyncResult) : PackDownloadEvent
}

/**
 * 목록 · 보드가 함께 보는 팩 상태(설계 D1).
 * [downloads]: 받는 중인 축제 → 진행률(첫 청크 전 · 스냅샷 한 번에 받는 중이면 null). 끝나면 빠진다.
 * [failures]: 마지막 받기가 실패한 축제 → 오류. 다시 받기를 시작하면 빠진다.
 */
data class FestivalPackState(
    val packs: Map<String, CachedFestivalPack> = emptyMap(),
    val downloads: Map<String, PackDownloadProgress?> = emptyMap(),
    val failures: Map<String, FestivalPackError> = emptyMap(),
)

/** 팩 저장 · 동기화 오류. iOS `PackSyncError` · `FestivalPackError`. */
sealed class FestivalPackError : Exception() {
    /** 연결 실패 · 끊김 · 응답 시간 초과. */
    class Offline : FestivalPackError()

    class InvalidFestivalId : FestivalPackError()

    /** 메시지 · 팩 · 청크 검증 실패(sha256 · 순서 · 한도 포함). */
    class InvalidPack : FestivalPackError()

    /** 팩이 8MB를 넘는다. */
    class PackTooLarge : FestivalPackError()

    /** 응답을 기다리는 동안 팩을 지웠다. */
    class PackDeleted : FestivalPackError()

    /** 서버 `pack.error` 코드(재시도 한도를 넘긴 `PACK_BUSY` 포함). */
    class Server(val code: String) : FestivalPackError()

    /** 서버 주소 없음 · 저장소 접근 실패 등. */
    class Unavailable : FestivalPackError()
}
