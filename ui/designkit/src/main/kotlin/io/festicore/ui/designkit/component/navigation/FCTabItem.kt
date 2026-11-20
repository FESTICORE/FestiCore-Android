package io.festicore.ui.designkit.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val IndicatorHeight = 2.dp
private val IndicatorRadius = 2.dp

/**
 * 탭 한 칸. 선택되면 글자를 굵게 하고 아래 밑줄을 그린다.
 *
 * @param isSelected 선택 여부(iOS `isSelected(_:)`).
 * @param indicatorColor 선택된 밑줄 색(iOS `indicatorColor(_:)`).
 */
@Composable
fun FCTabItem(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    indicatorColor: Color = FestiCoreSemanticColor.labelNormal.color,
) {
    val textStyle = if (isSelected) FestiCoreTextStyle.baseCalloutBold else FestiCoreTextStyle.baseCalloutMedium
    // Figma Tab Item(46) = 위 12 + 줄 높이 박스 24 + 간격 8 + 밑줄 2
    Column(
        modifier =
            modifier
                .padding(top = FestiCoreSpacing.paddingP12)
                .padding(horizontal = FestiCoreSpacing.paddingP16)
                .width(IntrinsicSize.Max),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = label,
            style =
                textStyle.textStyle.copy(
                    color = if (isSelected) FestiCoreSemanticColor.labelNormal.color else FestiCoreSemanticColor.labelAssistive.color,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IndicatorHeight)
                    .background(
                        if (isSelected) indicatorColor else Color.Transparent,
                        RoundedCornerShape(topStart = IndicatorRadius, topEnd = IndicatorRadius),
                    ),
        )
    }
}
