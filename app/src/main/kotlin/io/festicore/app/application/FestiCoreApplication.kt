package io.festicore.app.application

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import io.festicore.app.BuildConfig
import io.festicore.app.MainActivity
import io.festicore.app.di.appModules
import io.festicore.core.logger.FestiCoreLogLevel
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.core.utility.deeplink.FestiCoreDeeplink
import io.festicore.feature.festival.widget.FestivalWidgetHost
import io.festicore.feature.festival.widget.FestivalWidgetUpdater
import io.festicore.service.analytics.configuration.FestiCoreAnalyticsConfiguration
import io.festicore.service.analytics.usecase.AnalyticsUseCase
import io.festicore.service.config.FirebaseConfiguration
import io.festicore.service.config.FirebaseValues
import io.festicore.ui.sharedui.remoteimage.installFestiCoreImageLoader
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FestiCoreApplication :
    Application(),
    FestivalWidgetHost {
    override val festivalWidgetUpdater: FestivalWidgetUpdater by lazy { get() }
    override fun widgetOpenIntent(festivalId: String?): PendingIntent {
        val destination = festivalId?.let { FestiCoreDeeplink.Festival(it) } ?: FestiCoreDeeplink.FestivalList
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(destination.encoded))
            .setClass(this, MainActivity::class.java)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onCreate() {
        super.onCreate()
        FestiCoreLogger.minimumLevel = if (BuildConfig.DEBUG) FestiCoreLogLevel.DEBUG else FestiCoreLogLevel.NOTICE
        // iOS AppDelegate+Configuration 순서: Firebase → 분석 → 공통 슈퍼 프로퍼티
        FirebaseConfiguration.configure(
            this,
            FirebaseValues(
                apiKey = BuildConfig.FIREBASE_API_KEY,
                appId = BuildConfig.FIREBASE_APP_ID,
                projectId = BuildConfig.FIREBASE_PROJECT_ID,
                senderId = BuildConfig.FIREBASE_SENDER_ID,
            ),
        )
        FestiCoreAnalyticsConfiguration.configure(
            context = this,
            mixpanelToken = BuildConfig.MIXPANEL_TOKEN,
            sentryDsn = BuildConfig.SENTRY_DSN,
            isDebug = BuildConfig.DEBUG,
            isStage = BuildConfig.IS_STAGE,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
        )
        installFestiCoreImageLoader(this)
        startKoin {
            androidContext(this@FestiCoreApplication)
            modules(appModules)
        }
        // iOS는 분석 설정 직후 부른다. Android는 AnalyticsUseCase가 Koin에 있어 Koin 시작 뒤에 부른다. 토큰이 없으면 아무것도 하지 않는다.
        get<AnalyticsUseCase>().registerBaseProperties()
        festivalWidgetUpdater.start()
    }
}
