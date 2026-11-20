package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

/** 약관 전체 동의. 누르면 아래 [FCConsentRow]를 전부 체크하거나 해제하는 처리는 화면이 맡는다. */
@Composable
fun FCConsentAll(
    isChecked: Boolean,
    modifier: Modifier = Modifier,
    label: String = "전체 동의",
    onClick: () -> Unit,
) {
    // Figma Consent All 57 중 구분선 1은 화면이 그린다(행 자체 56 = 위아래 16 + Base/Headline/Bold 줄 높이 24)
    Row(
        modifier =
            modifier
                .selectable(selected = isChecked, role = Role.Checkbox, onClick = onClick)
                .padding(FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCCheckCircle(isChecked = isChecked)
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = FestiCoreTextStyle.baseHeadlineBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
    }
}
