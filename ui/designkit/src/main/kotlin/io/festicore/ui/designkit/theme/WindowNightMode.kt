package io.festicore.ui.designkit.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 지금 화면의 야간 비트(앱 화면 모드). 새 창(Dialog · ModalBottomSheet)을 띄우기 전에 바깥에서 읽어 [ProvideNightMode]에 넘긴다.
 * 새 창은 [LocalConfiguration]을 창의 시스템 값으로 다시 제공해, 앱 루트가 덮어쓴 화면 모드를 잃는다.
 */
@Composable
@ReadOnlyComposable
internal fun currentNightMode(): Int = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK

/** 새 창 안에서 [nightMode]를 다시 적용해 생성된 semantic 색이 띄운 화면과 같은 화면 모드를 따르게 한다. */
@Composable
internal fun ProvideNightMode(nightMode: Int, content: @Composable () -> Unit) {
    val base = LocalConfiguration.current
    val configuration =
        remember(base, nightMode) {
            if (base.uiMode and Configuration.UI_MODE_NIGHT_MASK == nightMode) {
                base
            } else {
                Configuration(base).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode }
            }
        }
    CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}
