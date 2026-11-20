package io.festicore.ui.designkit.component.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import java.text.BreakIterator

enum class FCAvatarTint {
    Gray,
    Lime,
    Purple,
    Red,
    Orange,
    Green,
    Blue,
    Pink,
    Yellow,
}

enum class FCAvatarSize(
    internal val side: Dp,
    internal val textStyle: FestiCoreTextStyle,
) {
    Medium(40.dp, FestiCoreTextStyle.baseSubheadlineBold),
    Large(48.dp, FestiCoreTextStyle.baseBodyBold),
    Small(28.dp, FestiCoreTextStyle.baseCaption1Bold),
}

@Composable
fun FCAvatar(
    initial: String,
    modifier: Modifier = Modifier,
    tint: FCAvatarTint = FCAvatarTint.Gray,
    size: FCAvatarSize = FCAvatarSize.Medium,
) {
    val (background, foreground) =
        when (tint) {
            FCAvatarTint.Gray -> FestiCoreSemanticColor.fillStrong to FestiCoreSemanticColor.labelNeutral
            FCAvatarTint.Lime -> FestiCoreSemanticColor.accentBackgroundLime to FestiCoreSemanticColor.accentForegroundLime
            FCAvatarTint.Purple -> FestiCoreSemanticColor.accentBackgroundPurple to FestiCoreSemanticColor.accentForegroundPurple
            FCAvatarTint.Red -> FestiCoreSemanticColor.accentBackgroundRed to FestiCoreSemanticColor.accentForegroundRed
            FCAvatarTint.Orange -> FestiCoreSemanticColor.accentBackgroundOrange to FestiCoreSemanticColor.accentForegroundOrange
            FCAvatarTint.Green -> FestiCoreSemanticColor.accentBackgroundGreen to FestiCoreSemanticColor.accentForegroundGreen
            FCAvatarTint.Blue -> FestiCoreSemanticColor.accentBackgroundBlue to FestiCoreSemanticColor.accentForegroundBlue
            FCAvatarTint.Pink -> FestiCoreSemanticColor.accentBackgroundPink to FestiCoreSemanticColor.accentForegroundPink
            FCAvatarTint.Yellow -> FestiCoreSemanticColor.accentBackgroundYellow to FestiCoreSemanticColor.accentForegroundYellow
        }
    // iOS prefix(1)처럼 자소 단위 첫 글자를 쓴다(이모지 · 결합 문자 보존).
    val boundary = BreakIterator.getCharacterInstance().apply { setText(initial) }.next()
    val first = if (boundary == BreakIterator.DONE) "" else initial.substring(0, boundary)
    Box(
        modifier =
            modifier
                .clearAndSetSemantics {}
                .size(size.side)
                .background(background.color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = first,
            style = size.textStyle.textStyle.copy(color = foreground.color),
        )
    }
}
