package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val TimeWidth = 48.dp
private val SelectSize = 28.dp
private val IconSize = 16.dp
private val OverlapIconSize = 12.dp

/**
 * 내 일정 고르기 한 줄. 시간이 겹치면 경고 문구를 표시한다.
 *
 * @param overlapText 겹침 경고 문구(iOS `overlapText(_:)`).
 */
@Composable
fun FCSetSelectRow(
    artist: String,
    stage: String,
    start: String,
    end: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    overlapText: String? = null,
    onClick: () -> Unit,
) {
    // Figma Set Select Row: 위아래 12 + 시간 열(24 + 4 + 16) = 68, 줄 사이 구분선 1을 더해 69 간격.
    Row(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .selectable(selected = isSelected, role = Role.Checkbox, onClick = onClick)
                .background(
                    if (isSelected) FestiCoreSemanticColor.accentBackgroundLime.color else FestiCoreSemanticColor.backgroundElevated.color,
                ).padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(TimeWidth),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        ) {
            SetSelectText(start, FestiCoreTextStyle.baseCalloutBold, FestiCoreSemanticColor.labelNormal, isMonospacedDigit = true)
            SetSelectText(end, FestiCoreTextStyle.baseCaption1Regular, FestiCoreSemanticColor.labelAssistive, isMonospacedDigit = true)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        ) {
            // Figma Set Select Row 아티스트: Base/Headline/Bold
            SetSelectText(artist, FestiCoreTextStyle.baseHeadlineBold, FestiCoreSemanticColor.labelNormal)
            SetSelectText(stage, FestiCoreTextStyle.baseCaption2Medium, FestiCoreSemanticColor.labelAssistive)
            if (overlapText != null) {
                val orange = FestiCoreSemanticColor.accentForegroundOrange.color
                Row(
                    modifier = Modifier.padding(top = FestiCoreSpacing.paddingP4),
                    horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_triangle_exclamation_16),
                        contentDescription = null,
                        modifier = Modifier.size(OverlapIconSize),
                        colorFilter = ColorFilter.tint(orange),
                    )
                    SetSelectText(overlapText, FestiCoreTextStyle.baseCaption1Medium, FestiCoreSemanticColor.accentForegroundOrange)
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .size(SelectSize)
                    .background(if (isSelected) FestiCoreSemanticColor.staticLime.color else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(if (isSelected) R.drawable.ic_check else R.drawable.ic_plus_16),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                colorFilter =
                    ColorFilter.tint(
                        if (isSelected) FestiCoreSemanticColor.staticInk.color else FestiCoreSemanticColor.labelAssistive.color,
                    ),
            )
        }
    }
}

@Composable
private fun SetSelectText(
    text: String,
    textStyle: FestiCoreTextStyle,
    color: FestiCoreSemanticColor,
    isMonospacedDigit: Boolean = false,
) {
    BasicText(
        text = text,
        style =
            textStyle.textStyle.copy(
                color = color.color,
                fontFeatureSettings = if (isMonospacedDigit) TABULAR_NUMBERS else null,
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
