package io.festicore.service.api

/** 내 알림 API 경로. `BASE_URL`이 `/`로 끝나므로 앞 `/`를 붙이지 않는다. iOS `UserNotificationAPI`. */
object UserNotificationApiPath {
    const val LIST = "api/v1/me/notifications"
    const val UNREAD_COUNT = "$LIST/unread-count"
    const val READ = "$LIST/read"
    const val DELETE = "$LIST/delete"
}
