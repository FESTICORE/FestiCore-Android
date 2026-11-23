package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val CheckSize = 24.dp
private val IconSize = 16.dp

/**
 * 약관 · 동의 한 줄. 필수/선택 태그를 붙이고, 전문이 있으면 오른쪽 화살표로 전문을 연다.
 *
 * @param tag 라벨 앞 태그(필수 · 선택, iOS `tag(_:)`).
 * @param sub 라벨 아래 설명(iOS `sub(_:)`).
 * @param onDetail 전문 보기 동작(iOS `onDetail(_:)`). 값이 있으면 오른쪽 화살표를 표시한다.
 */
@Composable
fun FCConsentRow(
    label: String,
    isChecked: Boolean,
    modifier: Modifier = Modifier,
    tag: String? = null,
    sub: String? = null,
    onDetail: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier.padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .selectable(selected = isChecked, role = Role.Checkbox, onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
            verticalAlignment = Alignment.Top,
        ) {
            Box(modifier = Modifier.size(CheckSize), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    colorFilter =
                        ColorFilter.tint(
                            if (isChecked) FestiCoreSemanticColor.labelNormal.color else FestiCoreSemanticColor.lineStrong.color,
                        ),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (tag != null) {
                        FCTag(tag)
                    }
                    BasicText(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = FestiCoreTextStyle.baseCalloutRegular.textStyleWithoutLineBox.copy(
                            color = FestiCoreSemanticColor.labelNormal.color,
                        ),
                    )
                }
                if (sub != null) {
                    BasicText(
                        text = sub,
                        style = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(
                            color = FestiCoreSemanticColor.labelAssistive.color,
                        ),
                    )
                }
            }
        }
        if (onDetail != null) {
            Box(
                modifier =
                    Modifier
                        .clearAndSetSemantics {
                            contentDescription = "$label 전문 보기"
                            role = Role.Button
                        }.height(CheckSize)
                        .clickable(onClick = onDetail),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_chevron_right_16),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
                )
            }
        }
    }
}
