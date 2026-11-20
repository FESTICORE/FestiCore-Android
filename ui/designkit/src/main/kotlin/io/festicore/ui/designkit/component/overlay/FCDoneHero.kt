package io.festicore.ui.designkit.component.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

/** 완료 화면 상단의 라임 체크와 제목 · 설명 */
@Composable
fun FCDoneHero(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP20),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FCCheckBadge()
        // Figma Done Hero: 제목 줄 30 · 설명 줄 24
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = title,
                modifier = Modifier.semantics { heading() },
                style =
                    FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(
                        color = FestiCoreSemanticColor.labelNormal.color,
                        textAlign = TextAlign.Center,
                    ),
            )
            BasicText(
                text = message,
                style =
                    FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(
                        color = FestiCoreSemanticColor.labelNeutral.color,
                        textAlign = TextAlign.Center,
                    ),
            )
        }
    }
}
