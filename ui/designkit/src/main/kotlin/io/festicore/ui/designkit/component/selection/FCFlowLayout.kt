package io.festicore.ui.designkit.component.selection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

/**
 * 칩을 가로로 늘어놓고 폭이 모자라면 다음 줄로 넘기는 배치(iOS `FCFlowLayout`). 가로 · 세로 간격이 같다.
 * 각 항목은 줄 폭을 넘지 않게 재고, 줄 안에서는 위쪽에 맞춘다.
 *
 * @param spacing 항목 · 줄 간격(iOS `spacing(_:)`).
 */
@Composable
fun FCFlowLayout(
    modifier: Modifier = Modifier,
    spacing: Dp = FestiCoreSpacing.paddingP8,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val availableWidth = constraints.maxWidth
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val rows = arrangeFlowRows(placeables.map { it.width }, availableWidth, gap)
        val rowHeights = rows.map { row -> row.maxOf { placeables[it].height } }
        val height = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
        val width =
            if (constraints.hasBoundedWidth) {
                availableWidth
            } else {
                rows.maxOfOrNull { row -> row.sumOf { placeables[it].width } + gap * (row.size - 1) } ?: 0
            }
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                var x = 0
                row.forEach { index ->
                    placeables[index].placeRelative(x, y)
                    x += placeables[index].width + gap
                }
                y += rowHeights[rowIndex] + gap
            }
        }
    }
}

/** 항목 폭을 줄 단위 색인 묶음으로 나눈다. 줄의 첫 항목은 폭이 넘쳐도 그 줄에 둔다(iOS `FlowLayout.arrange`). */
internal fun arrangeFlowRows(
    widths: List<Int>,
    availableWidth: Int,
    spacing: Int,
): List<List<Int>> {
    if (widths.isEmpty()) return emptyList()
    val available = availableWidth.coerceAtLeast(0)
    val rows = mutableListOf(mutableListOf<Int>())
    var rowWidth = 0
    widths.forEachIndexed { index, width ->
        val itemWidth = minOf(width, available)
        val row = rows.last()
        if (row.isNotEmpty() && rowWidth + spacing + itemWidth > available) {
            rows += mutableListOf(index)
            rowWidth = itemWidth
        } else {
            rowWidth = if (row.isEmpty()) itemWidth else rowWidth + spacing + itemWidth
            row += index
        }
    }
    return rows
}
