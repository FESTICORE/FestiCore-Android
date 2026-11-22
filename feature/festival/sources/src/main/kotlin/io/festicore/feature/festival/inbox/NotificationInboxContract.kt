package io.festicore.feature.festival.inbox

import io.festicore.domain.usernotification.UserNotificationItem

/** 첫 쪽 상태. iOS `NotificationInboxFeature.Load`. */
enum class NotificationInboxLoad {
    Idle,
    Loading,
    Loaded,
    Failed,
}

/** 알림함 팝업(iOS `fcAlert`, 버튼 "닫기"). */
enum class NotificationInboxDialog(
    val title: String,
    val message: String,
) {
    DestinationUnavailable("아직 열 수 없는 알림이에요", "해당 화면을 준비 중입니다."),
    LoadMoreFailed("알림을 더 불러오지 못했어요", "잠시 후 다시 시도해 주세요."),
    DeleteFailed("알림을 삭제하지 못했어요", "연결을 확인하고 다시 시도해 주세요."),
}

/**
 * 알림함 State(iOS `NotificationInboxFeature.State`). iOS `instanceID`는 두지 않는다.
 * 항목 범위 ViewModel이라 pop하면 진행 중 요청이 취소된다(설계 "상태").
 */
data class NotificationInboxState(
    val load: NotificationInboxLoad = NotificationInboxLoad.Idle,
    val items: List<UserNotificationItem> = emptyList(),
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val isEditing: Boolean = false,
    val isDeleting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    /** 읽음 요청 중에도 화면에서는 즉시 읽음으로 보인다. 다음 첫 쪽 조회가 서버 상태로 맞춘다. */
    val locallyReadIds: Set<String> = emptySet(),
    /** 전체 재조회 뒤 서버가 계속 안 읽음으로 보내도 같은 알림의 읽음 요청을 다시 보내지 않는다. */
    val attemptedReadIds: Set<String> = emptySet(),
    val dialog: NotificationInboxDialog? = null,
) {
    val allLoadedSelected: Boolean get() = items.isNotEmpty() && selectedIds.size == items.size

    fun isRead(item: UserNotificationItem): Boolean = item.readAt != null || item.id in locallyReadIds
}

sealed interface NotificationInboxSideEffect {
    /** 라이브 보드 알림을 열었다. Coordinator가 보드에 그 축제가 있으면 pop하고 보드를 바꾼다(설계 E8). */
    data class FestivalOpened(val festivalId: String) : NotificationInboxSideEffect
}
