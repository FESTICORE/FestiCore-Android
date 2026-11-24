package io.festicore.ui.designkit.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable

/**
 * 국소 다크 고정(iOS `.environment(\.colorScheme, .dark)`). 기기 · 앱 화면 모드와 관계없이 [content]의 생성된 semantic 색이 다크 값을 쓴다.
 * 안에서 띄운 시트 · 다이얼로그도 바깥 야간 비트를 다시 넣으므로([ProvideNightMode]) 함께 다크가 된다. S09 기수 보드가 쓴다.
 */
@Composable
fun FestiCoreDarkMode(content: @Composable () -> Unit) {
    ProvideNightMode(Configuration.UI_MODE_NIGHT_YES, content)
}
