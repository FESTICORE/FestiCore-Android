package io.festicore.ui.designkit.component.list

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.selection.FCCheckCircle
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val IconWrapSize = 40.dp
private val IconSize = 16.dp

/**
 * 알림함 한 줄. 읽음 · 안 읽음은 줄 바탕으로 구분하고, 편집 모드에서는 선택 체크를 보인다.
 *
 * @param icon 왼쪽 아이콘(iOS `icon(_:)`).
 * @param isEditing 편집 모드(iOS `editing(_:isSelected:)`의 첫 인자).
 * @param isSelected 편집 모드의 선택 여부(iOS `editing(_:isSelected:)`의 `isSelected`).
 */
@Composable
fun FCNotificationRow(
    title: String,
    message: String,
    time: String,
    isRead: Boolean,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_bell_16,
    isEditing: Boolean = false,
    isSelected: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {
                    stateDescription = if (isRead) "읽음" else "안 읽음"
                    if (isEditing && isSelected) selected = true
                }.background(
                    if (isRead) FestiCoreSemanticColor.backgroundElevated.color else FestiCoreSemanticColor.accentBackgroundLime.color,
                ).padding(FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isEditing) {
            FCCheckCircle(isChecked = isSelected)
        }
        Box(
            modifier =
                Modifier
                    .clearAndSetSemantics {}
                    .size(IconWrapSize)
                    .background(FestiCoreSemanticColor.fillNormal.color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNormal.color),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Figma S01-1 알림 행은 줄 높이 박스 기준이다.
                BasicText(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = FestiCoreTextStyle.baseCalloutBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    text = time,
                    style = FestiCoreTextStyle.baseCaption1Regular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
                    maxLines = 1,
                )
            }
            BasicText(
                text = message,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
