package io.festicore.service.analytics.di

import android.content.Context
import android.os.Build
import io.festicore.service.analytics.configuration.MixpanelConfiguration
import io.festicore.service.analytics.usecase.AnalyticsUseCase
import io.festicore.service.analytics.usecase.AnalyticsUseCaseImpl
import io.festicore.service.analytics.usecase.SentryApiClient
import io.festicore.service.analytics.usecase.baseSuperProperties
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Mixpanel은 `FestiCoreApplication`이 [io.festicore.service.analytics.configuration.FestiCoreAnalyticsConfiguration]으로 먼저 시작한다. */
val serviceAnalyticsModule: Module = module {
    single<AnalyticsUseCase> {
        AnalyticsUseCaseImpl(
            mixpanel = { MixpanelConfiguration.mainInstance },
            baseSuperProperties = androidContext().appBaseSuperProperties(),
            sentry = SentryApiClient(),
        )
    }
}

/** iOS `CFBundleShortVersionString` · `CFBundleVersion` 자리. 읽지 못하면 빈 값(iOS와 같음). */
private fun Context.appBaseSuperProperties(): Map<String, Any> {
    val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
    val build =
        when {
            info == null -> ""

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> info.longVersionCode.toString()

            else ->
                @Suppress("DEPRECATION")
                info.versionCode.toString()
        }
    return baseSuperProperties(appVersion = info?.versionName.orEmpty(), build = build)
}
