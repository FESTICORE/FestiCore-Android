package io.festicore.service.analytics.configuration

import android.content.Context
import io.festicore.core.logger.FestiCoreLogSink
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.service.analytics.log.SentryLogSink
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions
import java.net.URI

internal object SentryConfiguration {
    @Volatile
    private var hasValidConfiguration = false

    private val logSink = SentryLogSink()

    fun isEnabled(sdkEnabled: () -> Boolean = Sentry::isEnabled): Boolean = hasValidConfiguration && sdkEnabled()

    fun disable() {
        hasValidConfiguration = false
        runCatching { Sentry.close() }
    }

    internal fun resetForTesting() {
        hasValidConfiguration = false
    }

    fun dsn(raw: String): String? {
        val value = raw.trim()
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
        if (uri.rawUserInfo?.matches(Regex("[a-zA-Z0-9]+")) != true) return null
        if (uri.rawPath?.matches(Regex("/[0-9]+")) != true) return null
        if (uri.rawQuery != null || uri.rawFragment != null || uri.port != -1) return null
        return value
    }

    fun environment(isDebug: Boolean, isStage: Boolean): String = if (isDebug || isStage) "stage" else "production"

    fun configure(
        context: Context,
        rawDsn: String,
        isDebug: Boolean,
        isStage: Boolean,
        versionName: String,
        versionCode: Int,
        sdkEnabled: () -> Boolean = Sentry::isEnabled,
        registerSink: (FestiCoreLogSink) -> Unit = FestiCoreLogger::addSink,
        initialize: (Context, (SentryAndroidOptions) -> Unit) -> Unit = { appContext, configureOptions ->
            SentryAndroid.init(appContext) { options -> configureOptions(options) }
        },
    ) {
        hasValidConfiguration = false
        val dsn = dsn(rawDsn) ?: return
        initialize(context.applicationContext ?: context) { options ->
            options.dsn = dsn
            options.environment = environment(isDebug, isStage)
            options.release = "festicore-android@$versionName+$versionCode"
            // SDK 원문 진단에는 식별자와 페이로드가 포함될 수 있다. 앱의 안전한 로그만 사용한다.
            options.isDebug = false
            options.setDiagnosticLevel(if (isDebug && isStage) io.sentry.SentryLevel.INFO else io.sentry.SentryLevel.ERROR)
            options.isSendDefaultPii = false
            options.setBeforeSend { event, _ -> if (FestiCoreAnalyticsConfiguration.collectionEnabled) event else null }
            options.setBeforeSendTransaction { event, _ -> if (FestiCoreAnalyticsConfiguration.collectionEnabled) event else null }
            options.logs.setBeforeSend { event -> if (FestiCoreAnalyticsConfiguration.collectionEnabled) event else null }
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableSystemEventBreadcrumbs = false
            options.tracesSampleRate = 0.2
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.sessionReplay.sessionSampleRate = 0.0
            options.sessionReplay.onErrorSampleRate = 0.0
        }
        hasValidConfiguration = sdkEnabled()
        if (hasValidConfiguration) registerSink(logSink)
    }
}
