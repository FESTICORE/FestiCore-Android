package io.festicore.ui.designkit.component.overlay

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val IconSize = 16.dp

/**
 * 화면 안 안내 띠. 누르면 자세한 내용으로 이동한다.
 *
 * [icon]은 iOS `icon(_:)`, [showsChevron]은 `showsChevron(_:)` 설정이다. 누를 곳이 없는 안내는 화살표를 끈다(Figma S06 H).
 */
@Composable
fun FCBanner(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_triangle_exclamation_16,
    showsChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(FestiCoreRadius.r12)
    val tint = ColorFilter.tint(FestiCoreSemanticColor.inverseLabel.color)
    // Figma Banner(42) = 위아래 12 + Base/Footnote/Bold 줄 높이 18
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(FestiCoreSemanticColor.inverseBackground.color, shape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(IconSize), colorFilter = tint)
        BasicText(
            text = text,
            modifier = Modifier.weight(1f),
            style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = FestiCoreSemanticColor.inverseLabel.color),
        )
        if (showsChevron) {
            Image(
                painter = painterResource(R.drawable.ic_chevron_right_16),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                colorFilter = tint,
            )
        }
    }
}
