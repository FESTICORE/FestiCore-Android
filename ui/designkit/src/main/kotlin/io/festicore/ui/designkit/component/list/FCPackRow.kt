package io.festicore.ui.designkit.component.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCTagTone
import io.festicore.ui.designkit.component.media.FCThumbnail
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val ThumbnailSize = 44.dp
private val IconSize = 16.dp

/**
 * "내가 받은 팩" 한 줄. 새 버전이 있으면 업데이트 태그를 붙이고 누르면 팩 상세로 이동한다.
 *
 * @param image 썸네일 이미지(iOS `image(_:)`).
 * @param hasUpdate 새 버전 여부(iOS `hasUpdate(_:label:)`의 첫 인자).
 * @param updateLabel 업데이트 태그 글자(iOS `hasUpdate(_:label:)`의 `label`).
 */
@Composable
fun FCPackRow(
    name: String,
    meta: String,
    modifier: Modifier = Modifier,
    image: Painter? = null,
    hasUpdate: Boolean = false,
    updateLabel: String = "업데이트",
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
        FCThumbnail(image, side = ThumbnailSize)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM2),
        ) {
            BasicText(
                text = name,
                style = FestiCoreTextStyle.baseCalloutMedium.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNormal.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Figma Pack Row 메타는 한 줄 폭 맞춤(잘림 없음)이라 글자 열(181)보다 조금 긴 문구(182)가
            // 오른쪽 간격 12 안으로 넘친다. 같은 간격만큼만 넘치게 두고 그보다 길면 끝을 줄인다.
            BasicText(
                text = meta,
                modifier =
                    Modifier.layout { measurable, constraints ->
                        val overflow = FestiCoreSpacing.paddingP12.roundToPx()
                        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + overflow else constraints.maxWidth
                        val placeable = measurable.measure(constraints.copy(maxWidth = maxWidth))
                        layout(minOf(placeable.width, constraints.maxWidth), placeable.height) { placeable.place(0, 0) }
                    },
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.labelAssistive.color,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hasUpdate) {
            FCTag(updateLabel, tone = FCTagTone.Warning)
        }
        Image(
            painter = painterResource(R.drawable.ic_chevron_right_16),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
        )
    }
}
