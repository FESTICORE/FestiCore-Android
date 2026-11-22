package io.festicore.domain.usernotification

/** 내 알림 서버 저장소. 실패는 [UserNotificationError]. iOS `UserNotificationRepositoryInterface`. */
interface UserNotificationRepository {
    suspend fun notifications(before: String?): UserNotificationPage

    suspend fun unreadCount(): Int

    /** [sessionId] 세션일 때만 보낸다. 세션이 바뀌었으면 보내지 않고 [UserNotificationError.Unauthenticated]. */
    suspend fun markRead(ids: List<String>, sessionId: String)

    /** [sessionId] 세션일 때만 보낸다. 세션이 바뀌었으면 보내지 않고 [UserNotificationError.Unauthenticated]. */
    suspend fun delete(ids: List<String>, sessionId: String)
}
