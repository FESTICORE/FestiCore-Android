package io.festicore.domain.usernotification

import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.StateFlow

/** 보드 종 아이콘 배지와 상단 배너. [banner]는 24시간 안의 가장 최근 안 읽은 알림. iOS `FestivalFeature.State.notificationUnreadCount` · `notificationBanner`. */
data class UserNotificationBadge(
    val unreadCount: Int = 0,
    val banner: UserNotificationItem? = null,
)

/**
 * 보드 · 알림함이 함께 보는 배지 상태(설계 E7). 앱 범위 저장소 하나가 소유한다.
 * 보드는 보일 때 · 세션이 바뀔 때 [refresh]하고 계정이 바뀌면 [clear]한다. 알림함은 삭제 뒤 [refreshUnreadCount],
 * 알림을 열면 [markReadInBackground]를 부른다. 읽음 요청은 알림함이 닫혀도 끝까지 보낸다(iOS 보드 소유 effect).
 */
interface UserNotificationBadgeUseCase {
    val badge: StateFlow<UserNotificationBadge>

    /**
     * 안 읽은 수를 읽고, 0보다 크면 첫 쪽에서 배너 후보를 고른다(iOS `loadNotificationUnreadCount`).
     * 실패하면 마지막 값을 둔다. 그 사이 [clear]됐으면 응답을 버린다.
     */
    suspend fun refresh()

    /** 안 읽은 수만 다시 읽는다. 0 이하면 배너를 지운다(iOS 알림함 `refreshUnreadCount` → 보드 `notificationUnreadCountChanged`). */
    suspend fun refreshUnreadCount()

    /** 앱 범위에서 읽음을 보내고 성공 · 실패와 관계없이 [refresh]한다(iOS `markNotificationRead`). */
    fun markReadInBackground(id: String)

    /** 계정이 바뀌었다. 배지 0 · 배너 없음, 진행 중 응답은 버린다. */
    fun clear()

    companion object {
        /** 배너로 띄우는 알림의 최대 나이. 미래 시각(서버 시계가 앞섬)도 포함한다. iOS `notificationBannerLifetime`. */
        val BANNER_LIFETIME = 24.hours
    }
}
