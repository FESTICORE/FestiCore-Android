package io.festicore.ui.designkit.component.label

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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

private val IconSize = 16.dp

// Figma 오프라인 띠 높이 36 실측(줄 높이 박스 포함).
private val VerticalPadding = 9.dp

/** 오프라인·N분 전 정보 안내 띠. Top Bar 바로 아래 화면 너비로 두고 바깥 여백을 더하지 않는다. */
@Composable
fun FCOfflineBadge(message: String, modifier: Modifier = Modifier) {
    val color = FestiCoreSemanticColor.statusInfo.color
    Row(
        modifier =
            modifier
                .background(FestiCoreSemanticColor.accentBackgroundBlue.color)
                .padding(vertical = VerticalPadding, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_circle_info_16),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            colorFilter = ColorFilter.tint(color),
        )
        BasicText(
            text = message,
            modifier = Modifier.weight(1f),
            style = FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(color = color),
        )
    }
}
