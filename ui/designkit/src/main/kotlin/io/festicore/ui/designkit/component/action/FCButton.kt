package io.festicore.ui.designkit.component.action

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val LargeHeight = 52.dp
private val MediumHeight = 40.dp
private val BorderWidth = 1.dp
private const val PRESSED_OPACITY = 0.7f

enum class FCButtonStyle {
    Primary,
    Secondary,
    Ghost,
    Accent,
    Inverse,
    Ink,
    InkOutline,
}

enum class FCButtonSize {
    Large,
    Medium,
}

@Composable
fun FCButton(
    title: String,
    modifier: Modifier = Modifier,
    style: FCButtonStyle = FCButtonStyle.Primary,
    size: FCButtonSize = FCButtonSize.Large,
    isEnabled: Boolean = true,
    isFullWidth: Boolean = false,
    onClick: () -> Unit,
) {
    val isLarge = size == FCButtonSize.Large
    val shape = RoundedCornerShape(if (isLarge) FestiCoreRadius.r12 else FestiCoreRadius.r8)
    val background =
        when (style) {
            FCButtonStyle.Primary -> FestiCoreSemanticColor.primaryNormal.color
            FCButtonStyle.Secondary -> FestiCoreSemanticColor.backgroundElevated.color
            FCButtonStyle.Ghost -> Color.Transparent
            FCButtonStyle.Accent -> FestiCoreSemanticColor.accentPointLime.color
            FCButtonStyle.Inverse -> FestiCoreSemanticColor.inverseBackground.color
            FCButtonStyle.Ink -> FestiCoreSemanticColor.staticInk.color
            FCButtonStyle.InkOutline -> FestiCoreSemanticColor.staticWhite.color
        }
    val foreground =
        when (style) {
            FCButtonStyle.Primary -> FestiCoreSemanticColor.primaryOnPrimary
            FCButtonStyle.Secondary, FCButtonStyle.Ghost -> FestiCoreSemanticColor.labelNormal
            FCButtonStyle.Accent -> FestiCoreSemanticColor.staticInk
            FCButtonStyle.Inverse -> FestiCoreSemanticColor.inverseLabel
            FCButtonStyle.Ink -> FestiCoreSemanticColor.staticWhite
            FCButtonStyle.InkOutline -> FestiCoreSemanticColor.staticInk
        }.color
    val hasBorder = style == FCButtonStyle.Secondary || style == FCButtonStyle.InkOutline
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            modifier
                .alpha(if (isEnabled) 1f else FestiCoreState.disabledOpacity)
                .alpha(if (isPressed) PRESSED_OPACITY else 1f)
                .then(if (isFullWidth) Modifier.fillMaxWidth() else Modifier)
                .height(if (isLarge) LargeHeight else MediumHeight)
                .clip(shape)
                .background(background)
                .then(if (hasBorder) Modifier.border(BorderWidth, FestiCoreSemanticColor.lineStrong.color, shape) else Modifier)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = isEnabled,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(horizontal = if (isLarge) FestiCoreSpacing.paddingP20 else FestiCoreSpacing.paddingP16),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = title,
            style = (if (isLarge) FestiCoreTextStyle.baseCalloutBold else FestiCoreTextStyle.baseSubheadlineBold).textStyle.copy(
                color = foreground,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
