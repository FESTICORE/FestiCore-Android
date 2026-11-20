package io.festicore.ui.designkit.component.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Height = 28.dp

/**
 * 섹션 제목과 오른쪽 보조 글 · 전체보기.
 *
 * @param actionTitle 오른쪽 글(iOS `action(_:perform:)`의 첫 인자). [onAction]이 없으면 보조 글로만 표시한다.
 * @param isCompact 검색 목록처럼 작은 머리글(Figma S00 검색: Footnote/Bold 제목 · Footnote/Regular 보조 글, 높이 18, iOS `compact(_:)`).
 * @param onAction 오른쪽 글을 눌렀을 때 동작(iOS `action(_:perform:)`의 `perform`).
 */
@Composable
fun FCSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionTitle: String? = null,
    isCompact: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = if (isCompact) modifier else modifier.heightIn(min = Height),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Figma Section Header 제목: Base/Headline/Bold, 작은 머리글은 Base/Footnote/Bold
        BasicText(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style =
                (if (isCompact) FestiCoreTextStyle.baseFootnoteBold else FestiCoreTextStyle.baseHeadlineBold)
                    .textStyle
                    .copy(color = FestiCoreSemanticColor.labelNormal.color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionTitle != null) {
            BasicText(
                text = actionTitle,
                modifier =
                    if (onAction != null) {
                        Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onAction)
                    } else {
                        Modifier
                    },
                style =
                    (if (isCompact) FestiCoreTextStyle.baseFootnoteRegular else FestiCoreTextStyle.baseFootnoteMedium)
                        .textStyle
                        .copy(
                            color =
                                if (isCompact) FestiCoreSemanticColor.labelAssistive.color else FestiCoreSemanticColor.labelNeutral.color,
                        ),
                maxLines = 1,
            )
        }
    }
}
