package io.festicore.ui.designkit.component.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.label.FCStatusChip
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.component.media.FCThumbnail
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val ThumbnailSize = 40.dp

/** 활성 깃발 전체 리스트 한 줄. 최근 업데이트 순으로 쌓는다. [image]가 null이면 빈 썸네일을 보인다(iOS `image(_:)`). */
@Composable
fun FCActiveFlagRow(
    name: String,
    meta: String,
    time: String,
    status: FCStatusChipStatus,
    modifier: Modifier = Modifier,
    image: Painter? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                .padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCThumbnail(image, side = ThumbnailSize, cornerRadius = FestiCoreRadius.r12)
        // Figma 행 높이 70은 이름(24) · 설명(18) 줄 상자 기준이다.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        ) {
            BasicText(
                text = name,
                style = FestiCoreTextStyle.baseCalloutBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = meta,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        ) {
            FCStatusChip(status)
            BasicText(
                text = time,
                style = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.labelAssistive.color,
                ),
            )
        }
    }
}
