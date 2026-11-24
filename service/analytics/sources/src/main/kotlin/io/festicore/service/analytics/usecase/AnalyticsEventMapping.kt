package io.festicore.service.analytics.usecase

import io.festicore.service.analytics.event.AnalyticsEvent

/** iOS `AnalyticsUseCase.eventName`. 이름은 snake_case로 iOS와 같다. */
internal fun eventName(event: AnalyticsEvent): String = when (event) {
    is AnalyticsEvent.SignUp -> "sign_up"
    is AnalyticsEvent.ScreenView -> "screen_view"
    is AnalyticsEvent.UiAction -> "ui_action"
    is AnalyticsEvent.FlagFollow -> "flag_follow"
    is AnalyticsEvent.TimetableAction -> "timetable_action"
    is AnalyticsEvent.PackSync -> "pack_sync"
}

/** iOS `AnalyticsUseCase.eventProperties`. 화면 · 버튼 이름과 축제 · 깃발 · 공연 ID만 담는다. 토큰 · 이메일 · 닉네임은 담지 않는다. */
internal fun eventProperties(event: AnalyticsEvent): Map<String, Any> = when (event) {
    is AnalyticsEvent.SignUp -> mapOf("method" to event.method.rawValue)

    is AnalyticsEvent.ScreenView ->
        buildMap {
            put("screen", event.screen.rawValue)
            event.referrer?.let { put("referrer", it.rawValue) }
        }

    is AnalyticsEvent.UiAction -> mapOf("action" to event.action.rawValue, "screen" to event.screen.rawValue)

    is AnalyticsEvent.FlagFollow -> mapOf("flag_id" to event.data.flagId, "is_following" to event.data.isFollowing)

    is AnalyticsEvent.TimetableAction ->
        buildMap {
            put("action", event.data.action.rawValue)
            put("festival_id", event.data.festivalId)
            event.data.setId?.let { put("set_id", it) }
        }

    is AnalyticsEvent.PackSync -> mapOf("festival_id" to event.data.festivalId, "result" to event.data.result.rawValue)
}

/** iOS `baseSuperProperties`. 모든 이벤트에 붙는 플랫폼 · 버전. is_logged_in은 identify · reset이 관리한다. */
internal fun baseSuperProperties(appVersion: String, build: String): Map<String, Any> =
    mapOf("os_type" to "android", "app_version" to appVersion, "build" to build)
