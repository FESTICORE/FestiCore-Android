package io.festicore.ui.designkit.component.label

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val IconSize = 16.dp

/**
 * 기수 보드 맨 아래 안내 줄. queued는 오프라인에서 누른 변경이 전송 대기 중인 상태다.
 *
 * @param queuedCount 0이면 기본 안내, 1 이상이면 전송 대기 건수를 표시한다.
 * @param message 안내 문구. `null`이면 상태별 기본 문구를 쓴다(iOS `message(_:)`).
 */
@Composable
fun FCBearerFooter(
    modifier: Modifier = Modifier,
    queuedCount: Int = 0,
    message: String? = null,
) {
    val isQueued = queuedCount > 0
    val color = if (isQueued) FestiCoreSemanticColor.labelNormal.color else FestiCoreSemanticColor.labelAssistive.color
    Row(
        modifier =
            modifier.padding(
                vertical = FestiCoreSpacing.paddingP12,
                horizontal = FestiCoreSpacing.paddingP16,
            ),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(if (isQueued) R.drawable.ic_clock_16 else R.drawable.ic_circle_info_16),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            colorFilter = ColorFilter.tint(color),
        )
        // Figma Bearer Footer(42)는 줄 높이 박스(18) 기준이다.
        BasicText(
            text =
                message
                    ?: if (isQueued) "보내기 대기 ${queuedCount}건 · 연결되면 자동으로 보내요" else "오프라인이어도 눌러져요 · 연결되면 자동으로 보내요",
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = color),
        )
    }
}
