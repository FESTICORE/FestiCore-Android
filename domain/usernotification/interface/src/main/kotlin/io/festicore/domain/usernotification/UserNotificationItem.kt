package io.festicore.domain.usernotification

import java.time.Instant

/** 내 알림 한 건. [route]는 서버가 정한 목적지 문자열(예: `/festivals/{id}/live`). iOS `UserNotificationItem`. */
data class UserNotificationItem(
    val id: String,
    val type: String,
    val festivalId: String?,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val readAt: Instant?,
    val route: String?,
)

/**
 * 알림 한 쪽. [nextCursor]는 받은 값 그대로 다음 쪽 요청에 넘긴다. iOS `UserNotificationPage`.
 * [sessionId]는 이 쪽을 받은 세션. 이 쪽 알림의 읽음 · 삭제는 이 세션에 묶어 보낸다.
 */
data class UserNotificationPage(
    val items: List<UserNotificationItem>,
    val nextCursor: String?,
    val sessionId: String,
)
