package io.festicore.app

import android.app.Application
import io.festicore.app.di.appModules
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.core.logger.LogLevel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class FestiCoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FestiCoreLogger.minimumLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.NOTICE
        startKoin {
            androidContext(this@FestiCoreApplication)
            modules(appModules)
        }
    }
}
