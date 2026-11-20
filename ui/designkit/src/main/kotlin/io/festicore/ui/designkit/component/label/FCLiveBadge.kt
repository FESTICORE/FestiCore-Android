package io.festicore.ui.designkit.component.label

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

/** iOS `FCLiveBadge.Kind` */
enum class FCLiveBadgeKind {
    Now,
    Next,
}

/** 지금·다음 무대 표시. now는 Inverse, next는 Fill/Normal이다. */
@Composable
fun FCLiveBadge(kind: FCLiveBadgeKind, modifier: Modifier = Modifier) {
    val isNow = kind == FCLiveBadgeKind.Now
    // Figma Live Badge(24)는 줄 높이 박스(16) 기준이다.
    BasicText(
        text = if (isNow) "지금" else "다음",
        modifier =
            modifier
                .background(
                    if (isNow) FestiCoreSemanticColor.inverseBackground.color else FestiCoreSemanticColor.fillNormal.color,
                    RoundedCornerShape(FestiCoreRadius.r8),
                )
                .padding(vertical = FestiCoreSpacing.paddingP4, horizontal = FestiCoreSpacing.marginM6),
        style =
            FestiCoreTextStyle.baseCaption1Bold.textStyle.copy(
                color = if (isNow) FestiCoreSemanticColor.inverseLabel.color else FestiCoreSemanticColor.labelNeutral.color,
            ),
    )
}
