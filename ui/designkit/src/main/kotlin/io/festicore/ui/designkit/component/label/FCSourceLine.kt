package io.festicore.ui.designkit.component.label

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val IconSize = 16.dp

/**
 * 정보 출처와 확인 시각 한 줄.
 *
 * @param isInset 카드처럼 부모가 여백과 배경을 가진 곳에서는 false로 두어 띠와 안쪽 여백 없이 아이콘과 글자만 그린다(한 줄이면 높이 16, iOS `inset(_:)`).
 */
@Composable
fun FCSourceLine(
    source: String,
    modifier: Modifier = Modifier,
    isInset: Boolean = true,
) {
    Row(
        modifier =
            modifier
                // Figma Source Line 띠: Fill/Normal
                .then(if (isInset) Modifier.background(FestiCoreSemanticColor.fillNormal.color) else Modifier)
                .then(
                    if (isInset) {
                        Modifier.padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16)
                    } else {
                        Modifier
                    },
                ),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_circle_check_16),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
        )
        BasicText(
            text = source,
            modifier = Modifier.weight(1f),
            style = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
    }
}
