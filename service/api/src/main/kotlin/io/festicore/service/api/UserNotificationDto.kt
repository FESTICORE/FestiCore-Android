package io.festicore.service.api

import kotlinx.serialization.Serializable

/** `GET api/v1/me/notifications` 응답 `data`. 커서는 받은 값 그대로 다음 요청에 보낸다. iOS `UserNotificationListDataDTO`. */
@Serializable
data class UserNotificationListDto(
    val items: List<UserNotificationItemDto>,
    val nextCursor: String? = null,
)

/** 알림 한 건. 시각은 ISO-8601 문자열이고 domain 매퍼가 해석한다. iOS `UserNotificationItemDTO`. */
@Serializable
data class UserNotificationItemDto(
    val id: String,
    val type: String,
    val festivalId: String? = null,
    val title: String,
    val body: String,
    val createdAt: String,
    val readAt: String? = null,
    val route: String? = null,
)

/** `GET api/v1/me/notifications/unread-count` 응답 `data`. iOS `UserNotificationCountDataDTO`. */
@Serializable
data class UserNotificationCountDto(val unreadCount: Int)
