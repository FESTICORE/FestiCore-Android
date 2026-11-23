package io.festicore.domain.festival.entity

import java.time.Instant

/**
 * 라이브 보드가 지금 보여 주는 축제(설계 T1). 보드가 쓰고 타임테이블이 읽는다. iOS는 `MainTabCoordinator+Sync`가 보드 상태를 직접 읽는다.
 * [document]는 보드 표시 문서(온라인 상세 ?: 받은 팩) 중 ID가 [festivalId]와 같을 때만 담는다.
 */
data class FestivalBoardSelection(
    val festivalId: String,
    val document: FestivalPackDocument?,
    val isOnlineDetailLoading: Boolean,
    val isOnlineDetailFailed: Boolean,
    val isPackWatchFailed: Boolean,
    val connection: Connection,
    /** 받은 팩을 서버와 마지막으로 맞춘 시각(오프라인 안내 "N분 전 정보"). */
    val packCheckedAt: Instant?,
) {
    /** 보드 팩 감시 상태. iOS `FestivalFeature.Connection`. */
    enum class Connection {
        Loading,
        Cached,
        Connecting,
        Reconnecting,
        Live,
        Offline,
        Unavailable,
    }
}
