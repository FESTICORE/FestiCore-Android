package io.festicore.ui.designkit.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

private val DotSize = 8.dp

/**
 * 온보딩 페이지 점. 현재 페이지는 라임, 나머지는 흰색 20%. 다크 배경 전용.
 *
 * @param current 현재 페이지. 1부터 센다.
 * @param count 점 개수. 1보다 작으면 아무것도 그리지 않는다.
 */
@Composable
fun FCPagination(
    current: Int,
    modifier: Modifier = Modifier,
    count: Int = 4,
) {
    val accessibilityText = fcPaginationAccessibilityText(count = count, current = current) ?: return
    Row(
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = "페이지"
                stateDescription = accessibilityText
            },
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        fcPaginationDotStates(count = count, current = current).forEach { isCurrent ->
            val color = if (isCurrent) FestiCoreSemanticColor.accentPointLime else FestiCoreSemanticColor.staticalphaWhite20
            Box(
                modifier =
                    Modifier
                        .size(DotSize)
                        .background(color.color, CircleShape),
            )
        }
    }
}

/** `1..count`로 보정한 현재 페이지(iOS `resolvedCurrent`). */
internal fun fcPaginationResolvedCurrent(count: Int, current: Int): Int = minOf(maxOf(1, current), count)

/** 점마다 현재 페이지인지(iOS `dotStates`). 개수가 1보다 작으면 비어 있다. */
internal fun fcPaginationDotStates(count: Int, current: Int): List<Boolean> {
    if (count < 1) return emptyList()
    val resolved = fcPaginationResolvedCurrent(count = count, current = current)
    return (1..count).map { it == resolved }
}

/** 보정한 페이지로 만든 접근성 값(iOS `accessibilityText`). 개수가 1보다 작으면 `null`이고 요소를 만들지 않는다. */
internal fun fcPaginationAccessibilityText(count: Int, current: Int): String? {
    if (count < 1) return null
    return "페이지 ${fcPaginationResolvedCurrent(count = count, current = current)}/$count"
}
