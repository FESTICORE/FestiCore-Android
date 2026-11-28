package io.festicore.ui.designkit.component.action

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val IconSize = 24.dp
private val BorderWidth = 1.dp
private val SmallIconSize = 20.dp
private val SmallHeight = 64.dp

enum class FCBigButtonSize {
    Regular,
    Small,
}

@Composable
fun FCBigButton(
    label: String,
    sub: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    size: FCBigButtonSize = FCBigButtonSize.Regular,
    onClick: () -> Unit,
) {
    val foreground =
        when {
            isSelected -> FestiCoreSemanticColor.inverseLabel.color
            size == FCBigButtonSize.Regular -> FestiCoreSemanticColor.labelStrong.color
            else -> FestiCoreSemanticColor.labelNormal.color
        }
    val selectedBackground = FestiCoreSemanticColor.inverseBackground.color
    val selectedModifier = Modifier.semantics { selected = isSelected }
    when (size) {
        FCBigButtonSize.Regular -> {
            val shape = RoundedCornerShape(FestiCoreRadius.r24)
            Column(
                modifier =
                    modifier
                        .then(selectedModifier)
                        .fillMaxSize()
                        .clip(shape)
                        .background(
                            if (isSelected) selectedBackground else FestiCoreSemanticColor.backgroundElevated.color,
                        ).then(
                            if (isSelected) Modifier else Modifier.border(BorderWidth, FestiCoreSemanticColor.lineNormal.color, shape),
                        ).clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                        .padding(FestiCoreSpacing.paddingP16),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    colorFilter = ColorFilter.tint(foreground),
                )
                BasicText(
                    text = label,
                    modifier = Modifier.fillMaxWidth(),
                    style = FestiCoreTextStyle.baseTitle1Bold.textStyle.copy(color = foreground, textAlign = TextAlign.Center),
                )
                BasicText(
                    text = sub,
                    modifier = Modifier.fillMaxWidth(),
                    style =
                        FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(
                            color = if (isSelected) foreground else FestiCoreSemanticColor.labelNeutral.color,
                            textAlign = TextAlign.Center,
                        ),
                )
            }
        }

        FCBigButtonSize.Small -> {
            Column(
                modifier =
                    modifier
                        .then(selectedModifier)
                        .fillMaxWidth()
                        .heightIn(min = SmallHeight)
                        .clip(RoundedCornerShape(FestiCoreRadius.r12))
                        .background(
                            if (isSelected) selectedBackground else FestiCoreSemanticColor.fillNormal.color,
                        )
                        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                        .padding(horizontal = FestiCoreSpacing.paddingP8, vertical = FestiCoreSpacing.paddingP12),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM6, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(SmallIconSize),
                    colorFilter = ColorFilter.tint(foreground),
                )
                BasicText(
                    text = label,
                    style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = foreground, textAlign = TextAlign.Center),
                )
            }
        }
    }
}
