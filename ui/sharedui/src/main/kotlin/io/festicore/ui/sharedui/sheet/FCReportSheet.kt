package io.festicore.ui.sharedui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.selection.FCCheckCircle
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

/**
 * 신고 사유 시트 본문(iOS `FCReportSheet`). 시트 틀은 화면이 `FCSheet`로 감싼다.
 * 제목은 heading, 선택지는 selected 의미를 가진다. 사유를 고르기 전에는 [submitTitle] 버튼이 꺼진다.
 *
 * @param selection 고른 선택지 위치. `null`이면 아직 고르지 않았다.
 * @param onSelect 선택지를 눌렀을 때 그 위치.
 * @param onSubmit 고른 위치로 제출한다. 고르지 않았으면 부르지 않는다.
 */
@Composable
fun FCReportSheet(
    selection: Int?,
    options: List<String>,
    onSelect: (Int) -> Unit,
    onSubmit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "어떤 문제가 있나요?",
    message: String = "신고는 익명으로 처리돼요",
    submitTitle: String = "신고하기",
) {
    Column(
        // Figma 11-12 Report Sheet: 본문 아래 16을 두고 안전 영역은 시트가 따로 붙인다.
        modifier = modifier.fillMaxWidth().padding(
            start = FestiCoreSpacing.paddingP20,
            end = FestiCoreSpacing.paddingP20,
            bottom = FestiCoreSpacing.paddingP16,
        ),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            BasicText(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            )
            BasicText(
                text = message,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
            )
        }
        Column {
            options.forEachIndexed { index, option ->
                val isSelected = selection == index
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(index) })
                            .padding(vertical = FestiCoreSpacing.paddingP12),
                    horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FCCheckCircle(isChecked = isSelected)
                    BasicText(
                        text = option,
                        modifier = Modifier.weight(1f),
                        style = FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                    )
                }
            }
        }
        FCButton(
            title = submitTitle,
            isEnabled = selection != null,
            isFullWidth = true,
            onClick = { selection?.let(onSubmit) },
        )
    }
}
