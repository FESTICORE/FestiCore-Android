package io.festicore.ui.designkit.component.skeleton

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.label.rememberReduceMotion
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

private const val ROTATION_DEGREES = 5f
private const val PERIOD_MILLIS = 1000
private const val FADE_MILLIS = 200
private val MinimumBlurRadius = 30.dp
private const val LOADING_LABEL = "불러오는 중"

/** 데이터를 기다리는 동안 보이는 자리. 밝은 띠가 1초 주기로 지나가며, 동작 줄이기 설정이면 멈춘다. 크기는 [modifier]로 준다. */
@Composable
fun FCSkeletonView(shape: FCSkeletonShape, modifier: Modifier = Modifier) {
    val fill = FestiCoreSemanticColor.fillStrong.color
    val shimmer = FestiCoreSemanticColor.staticWhite.color
    val phase = rememberShimmerPhase()
    Box(
        modifier =
            modifier
                .drawBehind { drawSkeleton(shape, fill, shimmer, phase?.value, alpha = 1f) }
                .semantics { contentDescription = LOADING_LABEL },
    )
}

/**
 * iOS `View.skeleton(isLoading:shape:width:height:)`. 로딩 중에는 원본을 숨기고 스켈레톤을 보인다.
 * 데이터가 오기 전 원본이 비어 크기를 잃는 경우 [width] · [height]로 자리를 빌려준다.
 */
fun Modifier.skeleton(
    isLoading: Boolean,
    shape: FCSkeletonShape,
    width: Dp? = null,
    height: Dp? = null,
): Modifier = composed {
    val progress by animateFloatAsState(
        targetValue = if (isLoading) 1f else 0f,
        animationSpec = tween(FADE_MILLIS, easing = EaseInOut),
        label = "skeleton",
    )
    val fill = FestiCoreSemanticColor.fillStrong.color
    val shimmer = FestiCoreSemanticColor.staticWhite.color
    val phase = if (isLoading || progress > 0f) rememberShimmerPhase() else null
    this
        .then(
            if (isLoading) {
                Modifier
                    .defaultMinSize(minWidth = width ?: Dp.Unspecified, minHeight = height ?: Dp.Unspecified)
                    .clearAndSetSemantics { contentDescription = LOADING_LABEL }
            } else {
                Modifier
            },
        ).drawWithContent {
            if (progress < 1f) {
                val layerPaint = Paint().apply { alpha = 1f - progress }
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                drawContent()
                drawContext.canvas.restore()
            }
            if (progress > 0f) drawSkeleton(shape, fill, shimmer, phase?.value, alpha = progress)
        }
}

// 동작 줄이기면 null을 돌려 띠를 그리지 않는다.
@Composable
private fun rememberShimmerPhase(): State<Float>? {
    if (rememberReduceMotion()) return null
    return rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PERIOD_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerPhase",
    )
}

private fun DrawScope.drawSkeleton(
    shape: FCSkeletonShape,
    fill: Color,
    shimmer: Color,
    phase: Float?,
    alpha: Float,
) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val clip = Path().apply { addOutline(outline) }
    clipPath(clip) {
        drawRect(fill, alpha = alpha)
        if (phase == null) return@clipPath
        // iOS: 폭 절반의 흰 띠를 흐림(반지름 = max(띠 폭/2, 30))으로 퍼뜨려 5도 기울이고 soft light로 지나가게 한다.
        val bandWidth = size.width / 2
        val blurRadius = maxOf(bandWidth / 2, MinimumBlurRadius.toPx())
        val blurDiameter = blurRadius * 2
        val minX = -(bandWidth + blurDiameter)
        val maxX = size.width + bandWidth + blurDiameter
        val bandLeft = minX + (maxX - minX) * phase
        rotate(ROTATION_DEGREES) {
            translate(left = bandLeft) {
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            0f to shimmer.copy(alpha = 0f),
                            0.5f to shimmer,
                            1f to shimmer.copy(alpha = 0f),
                            startX = -blurRadius,
                            endX = bandWidth + blurRadius,
                        ),
                    topLeft = Offset(-blurRadius, -size.height / 2),
                    size = Size(bandWidth + blurDiameter, size.height * 2),
                    alpha = alpha,
                    blendMode = BlendMode.Softlight,
                )
            }
        }
    }
}
