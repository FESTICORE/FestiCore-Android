package io.festicore.ui.designkit.component.label

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val Size = 16.dp
private const val MAX_COUNT = 9

/** 안 읽은 알림 개수. 10 이상은 9+로 표시하고 0 이하는 그리지 않는다. */
@Composable
fun FCBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier =
            modifier
                .clearAndSetSemantics { contentDescription = "안 읽은 알림 ${count}개" }
                .defaultMinSize(minWidth = Size, minHeight = Size)
                .background(FestiCoreSemanticColor.statusNegative.color, CircleShape)
                .padding(horizontal = FestiCoreSpacing.paddingP4),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = if (count > MAX_COUNT) "$MAX_COUNT+" else "$count",
            // iOS monospacedDigit와 같은 고정폭 숫자
            style =
                FestiCoreTextStyle.baseCaption2Bold.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.staticWhite.color,
                    fontFeatureSettings = "tnum",
                ),
        )
    }
}
