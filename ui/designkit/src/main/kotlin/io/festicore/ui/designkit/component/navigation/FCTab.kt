package io.festicore.ui.designkit.component.navigation

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.label.rememberReduceMotion
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

private val IndicatorHeight = 2.dp
private val IndicatorRadius = 2.dp
private const val INDICATOR_DURATION_MILLIS = 200

/**
 * 같은 폭의 탭 줄. 칸마다 밑줄을 그리지 않고 선택 칸을 따라 움직이는 밑줄 하나를 그린다.
 * 범위 밖 [selection]은 어느 칸도 선택 표시하지 않는다. 선택 상태는 들지 않는다.
 *
 * @param items 칸 글자(iOS `items(_:)`). 비어 있으면 아무것도 그리지 않는다.
 * @param indicatorColor 선택된 탭의 밑줄 색(iOS `indicatorColor(_:)`).
 */
@Composable
fun FCTab(
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    items: List<String>,
    modifier: Modifier = Modifier,
    indicatorColor: Color = FestiCoreSemanticColor.labelNormal.color,
) {
    if (items.isEmpty()) return
    val selectedIndex = items.indices.firstOrNull { FCSelection.isSelected(index = it, selection = selection, count = items.size) }
    Box(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                FCTabItem(
                    label = item,
                    isSelected = isSelected,
                    modifier =
                        Modifier
                            .weight(1f)
                            .selectable(
                                selected = isSelected,
                                interactionSource = null,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelectionChange(index) },
                            ),
                    indicatorColor = Color.Transparent,
                )
            }
        }
        if (selectedIndex != null) {
            // FCTabItem 밑줄과 같은 위치 · 크기(칸 폭에서 좌우 여백을 뺀 2dp 막대)로 선택 칸을 따라 움직인다.
            BoxWithConstraints(modifier = Modifier.matchParentSize().clearAndSetSemantics {}) {
                val cellWidth = maxWidth / items.size
                val reduceMotion = rememberReduceMotion()
                val offsetX by animateDpAsState(
                    targetValue = cellWidth * selectedIndex + FestiCoreSpacing.paddingP16,
                    animationSpec = if (reduceMotion) snap() else tween(INDICATOR_DURATION_MILLIS, easing = EaseOut),
                    label = "FCTabIndicator",
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = offsetX)
                            .width((cellWidth - FestiCoreSpacing.paddingP16 * 2).coerceAtLeast(0.dp))
                            .height(IndicatorHeight)
                            .background(indicatorColor, RoundedCornerShape(topStart = IndicatorRadius, topEnd = IndicatorRadius)),
                )
            }
        }
    }
}
