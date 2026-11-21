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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        activityProvider.attach(this)
        enableEdgeToEdge()
        setContent {
            FestiCoreTheme {
                AppFlow()
            }
        }
    }

    override fun onDestroy() {
        activityProvider.detach(this)
        super.onDestroy()
    }
}
