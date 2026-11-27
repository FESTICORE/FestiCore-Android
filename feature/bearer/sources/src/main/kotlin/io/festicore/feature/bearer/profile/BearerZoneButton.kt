package io.festicore.feature.bearer.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

// iOS `BearerZoneButton`: Figma S10 존 버튼 높이 44, 아이콘 16, 테두리 1, 글자 최소 0.85배
private val MinHeight = 44.dp
private val IconSize = 16.dp
private val BorderWidth = 1.dp
private const val MINIMUM_SCALE = 0.85f

/** 존 하나를 고르는 칸(iOS `BearerZoneButton`). 고르면 Inverse로 칠한다. 기수 보드 존 시트(BS4)도 같이 쓴다. */
@Composable
internal fun BearerZoneButton(
    name: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(FestiCoreRadius.r16)
    val foreground = if (isSelected) FestiCoreSemanticColor.inverseLabel.color else FestiCoreSemanticColor.labelNormal.color
    val background = if (isSelected) FestiCoreSemanticColor.inverseBackground.color else FestiCoreSemanticColor.backgroundNormal.color
    val textStyle = FestiCoreTextStyle.baseFootnoteMedium.textStyleWithoutLineBox.copy(color = foreground)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MinHeight)
                .clip(shape)
                .background(background)
                .then(if (isSelected) Modifier else Modifier.border(BorderWidth, FestiCoreSemanticColor.lineNormal.color, shape))
                .semantics { selected = isSelected }
                .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = FestiCoreSpacing.paddingP12),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_location_dot_16),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            colorFilter = ColorFilter.tint(foreground),
        )
        BasicText(
            text = name,
            modifier = Modifier.weight(1f),
            style = textStyle,
            maxLines = 1,
            // iOS minimumScaleFactor(0.85)
            autoSize = TextAutoSize.StepBased(minFontSize = textStyle.fontSize * MINIMUM_SCALE, maxFontSize = textStyle.fontSize),
        )
    }
}
