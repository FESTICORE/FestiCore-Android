package io.festicore.service.analytics.configuration

import android.content.Context

/** iOS와 같이 Sentry를 Mixpanel보다 먼저 초기화한다. 설정이 없는 SDK는 시작하지 않는다. */
object FestiCoreAnalyticsConfiguration {
    fun configure(
        context: Context,
        mixpanelToken: String,
        sentryDsn: String = "",
        isDebug: Boolean = true,
        isStage: Boolean = true,
        versionName: String = "",
        versionCode: Int = 0,
    ) {
        SentryConfiguration.configure(context, sentryDsn, isDebug, isStage, versionName, versionCode)
        MixpanelConfiguration.configure(context, mixpanelToken)
    }
}
