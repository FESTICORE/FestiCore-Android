package io.festicore.ui.designkit.component.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Height = 20.dp
private val LineHeight = 2.dp

/**
 * 현재 시각 선. Inverse 색을 쓴다.
 *
 * @param time 표시할 시각(iOS `time(_:)`도 이 값을 바꾼다).
 */
@Composable
fun FCNowLine(time: String, modifier: Modifier = Modifier) {
    val inverseBackground = FestiCoreSemanticColor.inverseBackground.color
    Box(
        modifier =
            modifier
                .height(Height)
                .clearAndSetSemantics { contentDescription = "현재 시각 $time" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(LineHeight)
                    .background(inverseBackground),
        )
        BasicText(
            text = time,
            modifier =
                Modifier
                    .background(inverseBackground, RoundedCornerShape(FestiCoreRadius.r8))
                    .padding(vertical = FestiCoreSpacing.marginM2, horizontal = FestiCoreSpacing.marginM6),
            style = FestiCoreTextStyle.baseCaption2Bold.textStyle.copy(color = FestiCoreSemanticColor.inverseLabel.color),
            maxLines = 1,
        )
    }
}
