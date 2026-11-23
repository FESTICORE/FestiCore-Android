package io.festicore.ui.designkit.component.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.label.rememberReduceMotion
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val Height = 40.dp
private const val SHADOW_OPACITY = 0.08f
private val ShadowRadius = 3.dp

// iOS spring(response: 0.3, dampingFraction: 0.85): 강성 = (2π / response)²
private const val SPRING_DAMPING_RATIO = 0.85f
private const val SPRING_STIFFNESS = 438.6f

/**
 * 같은 폭의 세그먼트. 선택 배경이 선택 칸으로 미끄러진다(iOS `matchedGeometryEffect` 대응).
 * 범위 밖 [selection]은 어느 칸도 선택 표시하지 않는다. 선택 상태는 들지 않는다.
 *
 * @param items 칸 글자(iOS `items(_:)`). 비어 있으면 아무것도 그리지 않는다.
 */
@Composable
fun FCSegmented(
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val selectedIndex = items.indices.firstOrNull { FCSelection.isSelected(index = it, selection = selection, count = items.size) }
    Box(
        modifier =
            modifier
                .height(Height)
                .background(FestiCoreSemanticColor.fillNormal.color, RoundedCornerShape(FestiCoreRadius.r12))
                .padding(FestiCoreSpacing.paddingP4),
    ) {
        if (selectedIndex != null) {
            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                val cellWidth = maxWidth / items.size
                val reduceMotion = rememberReduceMotion()
                val offsetX by animateDpAsState(
                    targetValue = cellWidth * selectedIndex,
                    animationSpec = if (reduceMotion) snap() else spring(dampingRatio = SPRING_DAMPING_RATIO, stiffness = SPRING_STIFFNESS),
                    label = "FCSegmentedSelection",
                )
                val shape = RoundedCornerShape(FestiCoreRadius.r8)
                Box(
                    modifier =
                        Modifier
                            .offset(x = offsetX)
                            .width(cellWidth)
                            .fillMaxHeight()
                            .dropShadow(
                                shape = shape,
                                shadow =
                                    Shadow(
                                        radius = ShadowRadius,
                                        color = FestiCoreSemanticColor.staticBlack.color,
                                        alpha = SHADOW_OPACITY,
                                    ),
                            ).background(FestiCoreSemanticColor.fillSelected.color, shape),
                )
            }
        }
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val textStyle = if (isSelected) FestiCoreTextStyle.baseSubheadlineBold else FestiCoreTextStyle.baseSubheadlineMedium
                val textColor = if (isSelected) FestiCoreSemanticColor.labelNormal else FestiCoreSemanticColor.labelAssistive
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = isSelected,
                                interactionSource = null,
                                indication = null,
                                role = Role.Button,
                                onClick = { onSelectionChange(index) },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = item,
                        style = textStyle.textStyleWithoutLineBox.copy(color = textColor.color),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
