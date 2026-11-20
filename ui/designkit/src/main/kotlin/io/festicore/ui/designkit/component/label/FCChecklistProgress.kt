package io.festicore.ui.designkit.component.label

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Spacing = 10.dp
private val TrackHeight = 6.dp

/** 준비물 진행도. 막대 길이는 챙긴 비율이다. */
@Composable
fun FCChecklistProgress(
    checked: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(Spacing),
    ) {
        // Figma Checklist Progress: Base/Headline/Bold(17/24)
        BasicText(
            text = checklistProgressTitle(checked, total),
            style = FestiCoreTextStyle.baseHeadlineBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(TrackHeight)
                    .background(FestiCoreSemanticColor.fillNormal.color, CircleShape),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(checklistProgressRatio(checked, total))
                        .fillMaxHeight()
                        .background(FestiCoreSemanticColor.labelNormal.color, CircleShape),
            )
        }
    }
}

/** Figma S12 H: 모두 챙기면 "준비 끝!" 문구로 바꾼다. */
private fun checklistProgressTitle(checked: Int, total: Int): String = if (total > 0 && checked >= total) {
    "${total}개 모두 챙겼어요 · 준비 끝!"
} else {
    "${total}개 중 ${checked}개 챙겼어요"
}

private fun checklistProgressRatio(checked: Int, total: Int): Float = if (total > 0) checked.coerceIn(0, total).toFloat() / total else 0f
