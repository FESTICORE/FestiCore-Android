package io.festicore.ui.designkit.component.label

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val Height = 6.dp
private const val PROGRESS_ANIMATION_MILLIS = 300

/**
 * 팩 받는 중 진행 바와 문구(Figma S00 카드의 '팩 받는 중 · 62%').
 * 실측한 진행률이 없으면 움직이는 막대만 보인다.
 *
 * @param progress 실측한 0..1 진행률. `null`이면 진행 중 표시만 한다(iOS `progress(_:)`).
 */
@Composable
fun FCPackDownloadProgress(
    text: String,
    modifier: Modifier = Modifier,
    progress: Double? = null,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        if (progress != null) {
            val reduceMotion = rememberReduceMotion()
            val fraction by animateFloatAsState(
                targetValue = progress.coerceIn(0.0, 1.0).toFloat(),
                animationSpec = if (reduceMotion) snap() else tween(PROGRESS_ANIMATION_MILLIS, easing = EaseOut),
                label = "FCPackDownloadProgress",
            )
            Box(
                modifier =
                    Modifier
                        .clearAndSetSemantics {}
                        .fillMaxWidth()
                        .height(Height)
                        .background(FestiCoreSemanticColor.fillStrong.color, CircleShape),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(FestiCoreSemanticColor.staticLime.color, CircleShape),
                )
            }
        } else {
            // iOS ProgressView(.linear) 불확정 막대. 트랙 색은 시스템 색 대신 fillStrong 토큰을 쓴다.
            LinearProgressIndicator(
                modifier = Modifier.clearAndSetSemantics {}.fillMaxWidth(),
                color = FestiCoreSemanticColor.staticLime.color,
                trackColor = FestiCoreSemanticColor.fillStrong.color,
            )
        }
        BasicText(
            text = text,
            style = FestiCoreTextStyle.baseFootnoteBold.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
    }
}
