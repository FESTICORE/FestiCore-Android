package io.festicore.feature.bearer.claim

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

// iOS `BearerNoticeLine.Metric`: Figma Inline Message 아이콘 16, 글자 13/18
private val IconSize = 16.dp

/** 입력 칸 아래 안내 한 줄(iOS `BearerNoticeLine`). 경고는 빨간 삼각형, 안내는 회색 정보 아이콘이다. */
@Composable
internal fun BearerNoticeLine(
    text: String,
    isWarning: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (isWarning) FestiCoreSemanticColor.statusNegative.color else FestiCoreSemanticColor.labelAssistive.color
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(if (isWarning) R.drawable.ic_triangle_exclamation_16 else R.drawable.ic_circle_info_16),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            colorFilter = ColorFilter.tint(color),
        )
        BasicText(text = text, style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = color))
    }
}
