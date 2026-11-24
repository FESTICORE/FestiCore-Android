package io.festicore.app.application

import android.app.Application
import io.festicore.app.BuildConfig
import io.festicore.app.di.appModules
import io.festicore.core.logger.FestiCoreLogLevel
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.ui.sharedui.remoteimage.installFestiCoreImageLoader
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FestiCoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FestiCoreLogger.minimumLevel = if (BuildConfig.DEBUG) FestiCoreLogLevel.DEBUG else FestiCoreLogLevel.NOTICE
        installFestiCoreImageLoader(this)
        startKoin {
            androidContext(this@FestiCoreApplication)
            modules(appModules)
        }
    }
}
