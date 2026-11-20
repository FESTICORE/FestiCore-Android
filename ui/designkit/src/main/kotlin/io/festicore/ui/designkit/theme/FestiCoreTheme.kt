package io.festicore.ui.designkit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

/** Material 기본 컴포넌트도 semantic 색과 Pretendard 본문을 쓰게 한다. 화면 코드는 토큰을 직접 쓴다. */
@Composable
fun FestiCoreTheme(content: @Composable () -> Unit) {
    val background = FestiCoreSemanticColor.backgroundNormal.color
    val label = FestiCoreSemanticColor.labelNormal.color
    val base = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    val colorScheme =
        base.copy(
            primary = FestiCoreSemanticColor.primaryNormal.color,
            onPrimary = FestiCoreSemanticColor.primaryOnPrimary.color,
            background = background,
            onBackground = label,
            surface = background,
            onSurface = label,
        )
    MaterialTheme(colorScheme = colorScheme) {
        ProvideTextStyle(value = FestiCoreTextStyle.baseBodyRegular.textStyle, content = content)
    }
}
