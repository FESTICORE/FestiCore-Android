package io.festicore.ui.designkit.component.label

import android.provider.Settings
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

private val OuterSize = 16.dp
private val InnerSize = 11.dp
private val DotSize = 6.dp
private const val OUTER_OPACITY = 0.16f
private const val INNER_OPACITY = 0.32f
private const val COLLAPSED_SCALE = 0.7f
private const val PERIOD_MILLIS = 1_200

/** 지금 공연 중 불빛(Secondary). 바깥 링이 1.2초 주기로 일렁이며 "공연 중" 글자와 함께 쓴다. */
@Composable
fun FCLivePulse(modifier: Modifier = Modifier) {
    val color = FestiCoreSemanticColor.secondaryNormal.color
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "FCLivePulse")
    // iOS: easeInOut(period / 2).repeatForever(autoreverses: true)로 0.7 → 1 → 0.7
    val animatedScale =
        transition.animateFloat(
            initialValue = COLLAPSED_SCALE,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PERIOD_MILLIS / 2, easing = EaseInOut), RepeatMode.Reverse),
            label = "scale",
        )
    Box(
        modifier = modifier.clearAndSetSemantics {}.size(OuterSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(OuterSize)
                    .graphicsLayer {
                        val scale = if (reduceMotion) 1f else animatedScale.value
                        scaleX = scale
                        scaleY = scale
                    }.background(color.copy(alpha = OUTER_OPACITY), CircleShape),
        )
        Box(modifier = Modifier.size(InnerSize).background(color.copy(alpha = INNER_OPACITY), CircleShape))
        Box(modifier = Modifier.size(DotSize).background(color, CircleShape))
    }
}

/** iOS `accessibilityReduceMotion` 대응: 시스템 애니메이션 배율이 0이면 움직임을 줄인다. */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
