package io.festicore.service.analytics.configuration

import android.content.Context
import io.festicore.core.logger.FestiCoreLogLevel
import io.festicore.core.storage.shared.AnalyticsConsentStore
import io.festicore.service.analytics.usecase.FirebaseAnalyticsApiClient
import io.festicore.service.analytics.usecase.FirebaseAnalyticsClient

/** 선택적 분석·진단은 저장된 동의가 있을 때만 시작한다. SDK 하나의 실패는 앱/다른 SDK를 막지 않는다. */
object FestiCoreAnalyticsConfiguration {
    fun minimumLogLevel(isDebug: Boolean, isStage: Boolean): FestiCoreLogLevel = when {
        !isStage -> FestiCoreLogLevel.NOTICE
        isDebug -> FestiCoreLogLevel.DEBUG
        else -> FestiCoreLogLevel.INFO
    }

    @Volatile internal var collectionEnabled = false
        private set
    internal var firebase: FirebaseAnalyticsClient? = null
        private set
    private var consentStore: AnalyticsConsentStore? = null
    private var start: () -> Unit = {}

    fun configure(
        context: Context,
        mixpanelToken: String,
        sentryDsn: String = "",
        isDebug: Boolean = true,
        isStage: Boolean = true,
        versionName: String = "",
        versionCode: Int = 0,
        consentStore: AnalyticsConsentStore? = null,
    ) {
        this.consentStore = consentStore
        collectionEnabled = runCatching { consentStore?.isEnabled() == true }.getOrDefault(false)
        firebase = runCatching { FirebaseAnalyticsApiClient.create(context) }.getOrNull()
        start = {
            runCatching { SentryConfiguration.configure(context, sentryDsn, isDebug, isStage, versionName, versionCode) }
            runCatching {
                MixpanelConfiguration.configure(context, mixpanelToken)
                MixpanelConfiguration.mainInstance?.setCollectionEnabled(true)
            }
            runCatching { firebase?.setCollectionEnabled(true) }
        }
        if (collectionEnabled) start() else stop()
    }

    @Synchronized
    internal fun setCollectionEnabled(enabled: Boolean): Boolean {
        if (enabled && collectionEnabled) return true
        // 거부는 저장 실패에도 즉시 적용한다. 허용은 저장에 성공한 뒤 적용한다.
        if (!enabled) {
            collectionEnabled = false
            stop()
        }
        val saved = runCatching { consentStore?.setEnabled(enabled) == true }.getOrDefault(false)
        if (!saved) return false
        collectionEnabled = enabled
        if (enabled) start()
        return true
    }

    private fun stop() {
        runCatching { MixpanelConfiguration.mainInstance?.setCollectionEnabled(false) }
        SentryConfiguration.disable()
        runCatching { firebase?.setCollectionEnabled(false) }
        runCatching { firebase?.reset() }
    }
}
