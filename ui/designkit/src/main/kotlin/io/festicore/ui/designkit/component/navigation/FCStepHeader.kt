package io.festicore.ui.designkit.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Height = 44.dp
private val BarWidth = 28.dp
private val BarHeight = 4.dp
private val BarRadius = 2.dp

/**
 * 온보딩 단계 막대. 지난 단계까지 채우고, [onLater]를 주면 오른쪽에 "나중에 할게요" 버튼을 둔다.
 *
 * @param step 현재 단계(iOS `step(_:)`). `0...total`로 보정한다.
 * @param total 전체 단계. 최소 1.
 * @param laterTitle 나중에 버튼 글자(iOS `onLater(title:_:)`의 `title`).
 * @param onLater 나중에 버튼 동작(iOS `onLater(title:_:)`). `null`이면 버튼을 그리지 않는다.
 */
@Composable
fun FCStepHeader(
    step: Int,
    modifier: Modifier = Modifier,
    total: Int = 3,
    laterTitle: String = "나중에 할게요",
    onLater: (() -> Unit)? = null,
) {
    val resolvedTotal = maxOf(1, total)
    val resolvedStep = minOf(maxOf(0, step), resolvedTotal)
    Row(
        modifier =
            modifier
                .height(Height)
                .padding(horizontal = FestiCoreSpacing.paddingP16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = "$resolvedStep/${resolvedTotal}단계" },
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        ) {
            repeat(resolvedTotal) { index ->
                Box(
                    modifier =
                        Modifier
                            .size(BarWidth, BarHeight)
                            .background(
                                (if (index < resolvedStep) FestiCoreSemanticColor.labelNormal else FestiCoreSemanticColor.fillStrong).color,
                                RoundedCornerShape(BarRadius),
                            ),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (onLater != null) {
            BasicText(
                text = laterTitle,
                modifier = Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onLater),
                style = FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
            )
        }
    }
}
