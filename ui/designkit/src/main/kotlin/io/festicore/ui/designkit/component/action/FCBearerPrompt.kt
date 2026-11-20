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

private val FlagSize = 36.dp
private val IconSize = 16.dp
private const val SUB_OPACITY = 0.72f

@Composable
fun FCBearerPrompt(
    modifier: Modifier = Modifier,
    title: String = "기수이신가요?",
    sub: String = "운영팀에게 받은 코드로 깃발을 올려 보세요",
    onClick: () -> Unit,
) {
    val ink = FestiCoreSemanticColor.staticInk.color
    val lime = FestiCoreSemanticColor.accentPointLime.color
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(FestiCoreRadius.r16))
                .background(lime)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                .padding(vertical = FestiCoreSpacing.paddingP12)
                .padding(start = FestiCoreSpacing.paddingP12, end = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(FlagSize).background(ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_flag_16),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                colorFilter = ColorFilter.tint(lime),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM2),
        ) {
            BasicText(
                text = title,
                style = FestiCoreTextStyle.baseSubheadlineBold.textStyle.copy(color = ink),
            )
            BasicText(
                text = sub,
                modifier = Modifier.alpha(SUB_OPACITY),
                style = FestiCoreTextStyle.baseCaption1Medium.textStyle.copy(color = ink),
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_chevron_right_16),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            colorFilter = ColorFilter.tint(ink),
        )
    }
}
