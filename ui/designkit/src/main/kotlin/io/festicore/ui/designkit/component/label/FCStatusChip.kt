package io.festicore.ui.designkit.component.label

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val DotSize = 6.dp
private val DotBorderWidth = 1.dp

/** iOS `FCStatusChip.Status` */
enum class FCStatusChipStatus(internal val title: String) {
    Active("활동 중"),
    Resting("쉬는 중"),
    Scheduled("예정"),
    EndedToday("오늘 종료"),
}

/** 깃발 상태 칩. 항상 점과 단어를 함께 표시한다. */
@Composable
fun FCStatusChip(status: FCStatusChipStatus, modifier: Modifier = Modifier) {
    val foreground =
        when (status) {
            FCStatusChipStatus.Active -> FestiCoreSemanticColor.accentForegroundPurple
            FCStatusChipStatus.Resting -> FestiCoreSemanticColor.accentForegroundGreen
            FCStatusChipStatus.Scheduled -> FestiCoreSemanticColor.accentForegroundOrange
            FCStatusChipStatus.EndedToday -> FestiCoreSemanticColor.labelAssistive
        }.color
    val background =
        when (status) {
            FCStatusChipStatus.Active -> FestiCoreSemanticColor.accentBackgroundPurple
            FCStatusChipStatus.Resting -> FestiCoreSemanticColor.accentBackgroundGreen
            FCStatusChipStatus.Scheduled -> FestiCoreSemanticColor.accentBackgroundOrange
            FCStatusChipStatus.EndedToday -> FestiCoreSemanticColor.fillNormal
        }.color
    Row(
        modifier =
            modifier
                .background(background, RoundedCornerShape(FestiCoreRadius.r8))
                .padding(vertical = FestiCoreSpacing.paddingP4, horizontal = FestiCoreSpacing.paddingP8),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(DotSize)
                    .then(
                        when (status) {
                            FCStatusChipStatus.Scheduled ->
                                Modifier.border(DotBorderWidth, FestiCoreSemanticColor.accentForegroundOrange.color, CircleShape)

                            FCStatusChipStatus.EndedToday -> Modifier.background(FestiCoreSemanticColor.lineStrong.color, CircleShape)

                            else -> Modifier.background(foreground, CircleShape)
                        },
                    ),
        )
        // Figma Status Chip(24)은 줄 높이 박스(16) 기준이다.
        BasicText(
            text = status.title,
            style = FestiCoreTextStyle.baseCaption1Medium.textStyle.copy(color = foreground),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
