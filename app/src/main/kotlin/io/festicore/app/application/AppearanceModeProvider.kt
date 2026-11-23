package io.festicore.app.application

import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import io.festicore.core.storage.AppearanceMode

/**
 * 고른 화면 모드를 앱 전체에 적용한다(iOS 앱 루트 `preferredColorScheme`, 설계 쟁점 M6).
 * 생성된 semantic 색은 `isSystemInDarkTheme()`(= [LocalConfiguration]의 uiMode 야간 비트)를 읽으므로
 * 토큰 코드는 그대로 두고 야간 비트만 덮어쓴다. 시스템 설정과 같게는 기기 값을 그대로 둔다.
 */
@Composable
fun AppearanceModeProvider(mode: AppearanceMode, content: @Composable () -> Unit) {
    val base = LocalConfiguration.current
    val configuration = remember(base, mode) { base.withAppearanceMode(mode) }
    CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}

internal fun Configuration.withAppearanceMode(mode: AppearanceMode): Configuration {
    val night =
        when (mode) {
            AppearanceMode.SYSTEM -> return this
            AppearanceMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            AppearanceMode.DARK -> Configuration.UI_MODE_NIGHT_YES
        }
    return Configuration(this).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night }
}

/** 상태바 · 내비게이션 바 아이콘 색을 화면 모드에 맞춘다. 시스템 설정과 같게는 지금까지의 기본 동작(기기 설정)을 쓴다. */
fun ComponentActivity.applySystemBarStyle(mode: AppearanceMode) {
    when (mode) {
        AppearanceMode.SYSTEM -> enableEdgeToEdge()
        AppearanceMode.LIGHT -> SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT).let { enableEdgeToEdge(it, it) }
        AppearanceMode.DARK -> SystemBarStyle.dark(Color.TRANSPARENT).let { enableEdgeToEdge(it, it) }
    }
}
