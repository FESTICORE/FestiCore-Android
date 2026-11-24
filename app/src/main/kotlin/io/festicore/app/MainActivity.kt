package io.festicore.app
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.festicore.app.application.AppearanceModeProvider
import io.festicore.app.application.MainActivityProvider
import io.festicore.app.application.applySystemBarStyle
import io.festicore.app.deeplink.AppDeeplinkBridge
import io.festicore.app.navigation.AppFlow
import io.festicore.core.storage.shared.AppearanceModeStore
import io.festicore.ui.designkit.theme.FestiCoreTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val activityProvider: MainActivityProvider by inject()
    private val deeplinkBridge: AppDeeplinkBridge by inject()
    private val appearanceModeStore: AppearanceModeStore by inject()

    /** 첫 화면을 그릴 수 있는지. 그 전까지 시스템 스플래시(staticInk)를 남겨 빈 화면 · 상태바가 보이지 않게 한다. */
    private var isLaunchContentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !isLaunchContentReady }
        super.onCreate(savedInstanceState)
        activityProvider.attach(this)
        // 회전 등으로 다시 만들 때는 이미 받은 인텐트라 다시 넣지 않는다.
        if (savedInstanceState == null) deeplinkBridge.handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            // 저장된 화면 모드를 읽기 전에는 그리지 않는다. 그동안 시스템 스플래시가 남아 다른 모드가 잠깐 보이지 않는다.
            val mode by appearanceModeStore.appearanceMode.collectAsStateWithLifecycle(initialValue = null)
            mode?.let { current ->
                LaunchedEffect(current) { applySystemBarStyle(current) }
                AppearanceModeProvider(current) {
                    FestiCoreTheme {
                        AppFlow(onLaunchContentReady = { isLaunchContentReady = true })
                    }
                }
            }
        }
    }

    /** `singleTop`이라 앱이 떠 있을 때 온 링크는 새 Activity 대신 여기로 온다(설계 P8). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deeplinkBridge.handleIntent(intent)
    }

    override fun onDestroy() {
        activityProvider.detach(this)
        super.onDestroy()
    }
}
