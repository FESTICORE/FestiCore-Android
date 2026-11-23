package io.festicore.service.apiendpoint.usernotification

import kotlinx.serialization.Serializable

/** 읽음 · 삭제 요청 본문 `{"ids":[…]}`. iOS `UserNotificationIDsBody`. */
@Serializable
data class UserNotificationIdsBody(val ids: List<String>)
