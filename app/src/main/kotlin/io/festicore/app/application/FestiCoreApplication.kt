package io.festicore.app.application

import android.app.Application
import io.festicore.app.BuildConfig
import io.festicore.app.di.appModules
import io.festicore.core.logger.FestiCoreLogLevel
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.service.config.FirebaseConfiguration
import io.festicore.service.config.FirebaseValues
import io.festicore.ui.sharedui.remoteimage.installFestiCoreImageLoader
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FestiCoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FestiCoreLogger.minimumLevel = if (BuildConfig.DEBUG) FestiCoreLogLevel.DEBUG else FestiCoreLogLevel.NOTICE
        // iOS AppDelegate+Configuration 순서: Firebase → (R6b) 분석
        FirebaseConfiguration.configure(
            this,
            FirebaseValues(
                apiKey = BuildConfig.FIREBASE_API_KEY,
                appId = BuildConfig.FIREBASE_APP_ID,
                projectId = BuildConfig.FIREBASE_PROJECT_ID,
                senderId = BuildConfig.FIREBASE_SENDER_ID,
            ),
        )
        installFestiCoreImageLoader(this)
        startKoin {
            androidContext(this@FestiCoreApplication)
            modules(appModules)
        }
    }
}
