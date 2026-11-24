package io.festicore.service.analytics.configuration

import android.content.Context

/**
 * 외부 분석 SDK를 앱 시작 시 한 번 초기화한다(iOS `FestiCoreAnalyticsConfiguration`).
 * 사용자 결정: Sentry 제외. iOS는 Sentry를 Mixpanel보다 먼저 시작하고 Sentry Logs sink를 붙이지만, Android는 Mixpanel만 시작한다.
 * [mixpanelToken]은 app이 `BuildConfig.MIXPANEL_TOKEN`(local.properties · CI 비밀, 없으면 빈 값)으로 넘긴다.
 */
object FestiCoreAnalyticsConfiguration {
    fun configure(context: Context, mixpanelToken: String) {
        MixpanelConfiguration.configure(context, mixpanelToken)
    }
}
