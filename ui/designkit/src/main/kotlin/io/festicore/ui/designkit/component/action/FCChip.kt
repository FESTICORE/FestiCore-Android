package io.festicore.ui.designkit.component.action

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Height = 32.dp
private val BorderWidth = 1.dp

@Composable
fun FCChip(
    label: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .semantics { selected = isSelected }
                .height(Height)
                .clip(CircleShape)
                .background(
                    if (isSelected) FestiCoreSemanticColor.inverseBackground.color else FestiCoreSemanticColor.backgroundElevated.color,
                )
                .then(if (isSelected) Modifier else Modifier.border(BorderWidth, FestiCoreSemanticColor.lineNormal.color, CircleShape))
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = FestiCoreSpacing.paddingP12),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style =
                FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(
                    color = if (isSelected) FestiCoreSemanticColor.inverseLabel.color else FestiCoreSemanticColor.labelNeutral.color,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
