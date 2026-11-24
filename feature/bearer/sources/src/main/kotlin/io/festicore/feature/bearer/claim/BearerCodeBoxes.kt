package io.festicore.feature.bearer.claim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

// iOS `BearerCodeBoxes.Metric`의 리터럴 값.
private val BoxHeight = 56.dp
private val CaretWidth = 1.5.dp
private val CaretHeight = 22.dp
private val BorderWidth = 1.5.dp

/** 코드 칸(iOS `BearerCodeBoxes`). 입력은 위에 겹친 투명 입력창이 받고, 칸은 글자와 현재 위치만 그린다. */
@Composable
internal fun BearerCodeBoxes(
    code: String,
    length: Int,
    isFocused: Boolean,
    isError: Boolean,
    isDisabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = "코드 ${length}자리"
                    stateDescription = if (code.isEmpty()) "비어 있음" else code.toList().joinToString(" ")
                },
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8),
    ) {
        repeat(length) { index ->
            val character = code.getOrNull(index)
            val isCurrent = isFocused && !isDisabled && index == minOf(code.length, length - 1) && code.length < length
            val shape = RoundedCornerShape(FestiCoreRadius.r12)
            val borderColor: Color? =
                when {
                    isError -> FestiCoreSemanticColor.statusNegative.color
                    isCurrent -> FestiCoreSemanticColor.labelNormal.color
                    else -> null
                }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(BoxHeight)
                        .background(FestiCoreSemanticColor.fillNormal.color, shape)
                        .then(if (borderColor != null) Modifier.border(BorderWidth, borderColor, shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (character != null) {
                    BasicText(
                        text = character.toString(),
                        style = FestiCoreTextStyle.baseTitle3Bold.textStyleWithoutLineBox.copy(
                            color = FestiCoreSemanticColor.labelNormal.color,
                        ),
                    )
                } else if (isCurrent) {
                    Box(
                        modifier = Modifier.size(
                            width = CaretWidth,
                            height = CaretHeight,
                        ).background(FestiCoreSemanticColor.labelNormal.color),
                    )
                }
            }
        }
    }
}
