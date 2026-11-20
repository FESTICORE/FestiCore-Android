package io.festicore.ui.designkit.component.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

private val Size = 96.dp
private val MarkWidth = 44.dp
private val MarkHeight = 31.dp
private val LineWidth = 8.dp

// 체크 꺾이는 점의 가로 위치(iOS CheckMark와 같은 비율)
private const val MARK_ELBOW_RATIO = 0.36f

/** 완료 · 확인 화면에 쓰는 라임 원 + 체크 표시 */
@Composable
fun FCCheckBadge(modifier: Modifier = Modifier) {
    val ink = FestiCoreSemanticColor.staticInk.color
    Box(
        modifier = modifier.size(Size).background(FestiCoreSemanticColor.staticLime.color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(MarkWidth, MarkHeight)) {
            val path =
                Path().apply {
                    moveTo(0f, size.height / 2)
                    lineTo(size.width * MARK_ELBOW_RATIO, size.height)
                    lineTo(size.width, 0f)
                }
            drawPath(path, ink, style = Stroke(width = LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
