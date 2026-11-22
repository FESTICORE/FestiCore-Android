package io.festicore.domain.usernotification

/** 내 알림 서버 저장소. 실패는 [UserNotificationError]. iOS `UserNotificationRepositoryInterface`. */
interface UserNotificationRepository {
    suspend fun notifications(before: String?): UserNotificationPage

    suspend fun unreadCount(): Int

    suspend fun markRead(ids: List<String>)

    suspend fun delete(ids: List<String>)
}
