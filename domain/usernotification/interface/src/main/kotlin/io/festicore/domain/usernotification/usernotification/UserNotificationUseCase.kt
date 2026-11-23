package io.festicore.domain.usernotification.usernotification

import io.festicore.domain.usernotification.entity.UserNotificationPage

/** 내 알림함 요청. 모두 로그인 세션이 필요하다. iOS `UserNotificationUseCaseInterface`. */
interface UserNotificationUseCase {
    /** [before]가 null이면 첫 쪽. */
    suspend fun notifications(before: String?): UserNotificationPage

    /** 0 이상. */
    suspend fun unreadCount(): Int

    /** 빈 목록이면 보내지 않는다. 100개씩 나눠 보낸다. [sessionId](그 알림을 받은 쪽의 세션)일 때만 보낸다. */
    suspend fun markRead(ids: List<String>, sessionId: String)

    /** 빈 목록이면 보내지 않는다. 100개씩 나눠 보낸다. [sessionId](그 알림을 받은 쪽의 세션)일 때만 보낸다. */
    suspend fun delete(ids: List<String>, sessionId: String)
}
