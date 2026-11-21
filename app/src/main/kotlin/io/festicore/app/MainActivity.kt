package io.festicore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.festicore.app.navigation.AppFlow
import io.festicore.ui.designkit.theme.FestiCoreTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val activityProvider: MainActivityProvider by inject()

    /** 첫 화면을 그릴 수 있는지. 그 전까지 시스템 스플래시(staticInk)를 남겨 빈 화면 · 상태바가 보이지 않게 한다. */
    private var isLaunchContentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !isLaunchContentReady }
        super.onCreate(savedInstanceState)
        activityProvider.attach(this)
        enableEdgeToEdge()
        setContent {
            FestiCoreTheme {
                AppFlow(onLaunchContentReady = { isLaunchContentReady = true })
            }
        }
    }

    override fun onDestroy() {
        activityProvider.detach(this)
        super.onDestroy()
    }
}
