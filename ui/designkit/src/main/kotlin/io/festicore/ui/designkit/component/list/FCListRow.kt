package io.festicore.ui.designkit.component.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.selection.FCToggle
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val Height = 56.dp
private val IconSize = 16.dp

/** Figma List Row type=select의 Check 20 */
private val CheckSize = 20.dp

/**
 * 누르면 이동하는 설정형 한 줄. 오른쪽에 화살표를 표시한다.
 *
 * @param value 화살표 앞에 보이는 값(iOS `value(_:)`).
 * @param isSelected null이 아니면 여러 값 중 하나를 고르는 줄로 바꿔, 화살표 · 값 대신 고른 줄 오른쪽에 체크를 표시한다
 * (iOS `isSelected(_:)`, Figma S13-J 화면 모드).
 */
@Composable
fun FCListRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    isSelected: Boolean? = null,
    onClick: () -> Unit,
) {
    val clickModifier =
        modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
    if (isSelected == null) {
        FCListRowContent(label, clickModifier) {
            if (value != null) {
                BasicText(
                    text = value,
                    style = FestiCoreTextStyle.baseBodyRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_chevron_right_16),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
            )
        }
    } else {
        FCListRowContent(label, clickModifier.semantics { selected = isSelected }) {
            if (isSelected) {
                Image(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(CheckSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNormal.color),
                )
            }
        }
    }
}

/** 켜기/끄기 설정형 한 줄. 오른쪽에 토글을 표시한다(iOS `init(_:isOn:)`). */
@Composable
fun FCListRow(
    label: String,
    isOn: Boolean,
    modifier: Modifier = Modifier,
    onIsOnChange: (Boolean) -> Unit,
) {
    FCListRowContent(label, modifier) {
        FCToggle(isOn = isOn, onIsOnChange = onIsOnChange, accessibilityLabel = label)
    }
}

@Composable
private fun FCListRowContent(
    label: String,
    modifier: Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .height(Height)
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .padding(horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = FestiCoreTextStyle.baseBodyRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}
