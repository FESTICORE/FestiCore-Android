package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

/**
 * 칩 · 타일 같은 선택 묶음 위 라벨. [FCTextField] 라벨 줄과 같은 규격이다.
 *
 * @param counter 오른쪽 카운터 글자(iOS `counter(_:)`). `null`이면 숨긴다.
 */
@Composable
fun FCFieldLabel(
    label: String,
    modifier: Modifier = Modifier,
    counter: String? = null,
) {
    // Figma Text Field의 Label Row 높이 18(13/18)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (counter != null) {
            BasicText(
                text = counter,
                style =
                    FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(
                        color = FestiCoreSemanticColor.labelAssistive.color,
                        fontFeatureSettings = TABULAR_NUMBERS,
                    ),
                maxLines = 1,
            )
        }
    }
}

/** iOS `monospacedDigit()`과 같은 고정폭 숫자. */
internal const val TABULAR_NUMBERS = "tnum"
