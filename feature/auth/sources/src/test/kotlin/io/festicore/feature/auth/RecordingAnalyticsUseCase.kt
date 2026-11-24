package io.festicore.feature.auth

import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.usecase.AnalyticsUseCase

/** 화면이 보낸 분석 호출을 기록하는 가짜 sink. */
class RecordingAnalyticsUseCase : AnalyticsUseCase {
    val events = mutableListOf<AnalyticsEvent>()
    val identified = mutableListOf<Pair<String, String?>>()
    var resetCount = 0

    override fun registerBaseProperties() = Unit

    override fun identify(userId: String, method: String?) {
        identified += userId to method
    }

    override fun track(event: AnalyticsEvent) {
        events += event
    }

    override fun reset() {
        resetCount++
    }
}
