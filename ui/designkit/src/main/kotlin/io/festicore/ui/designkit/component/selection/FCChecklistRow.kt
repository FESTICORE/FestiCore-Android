package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val CheckIconSize = 14.dp

/**
 * 준비물 체크리스트 한 줄. 줄 전체를 눌러 체크한다.
 *
 * @param isRequired 운영팀 필수 항목 여부(iOS `isRequired(_:)`).
 */
@Composable
fun FCChecklistRow(
    label: String,
    isChecked: Boolean,
    modifier: Modifier = Modifier,
    isRequired: Boolean = false,
    onClick: () -> Unit,
) {
    // Figma Checklist Row 49 중 구분선 1은 화면이 그린다(행 자체 48 = 위아래 12 + 줄 높이 24)
    Row(
        modifier =
            modifier
                .selectable(selected = isChecked, role = Role.Checkbox, onClick = onClick)
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCCheckCircle(isChecked = isChecked, iconSize = CheckIconSize)
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style =
                FestiCoreTextStyle.baseCalloutMedium.textStyle.copy(
                    color = if (isChecked) FestiCoreSemanticColor.labelAssistive.color else FestiCoreSemanticColor.labelNormal.color,
                ),
        )
        if (isRequired) {
            FCTag("필수")
        }
    }
}
