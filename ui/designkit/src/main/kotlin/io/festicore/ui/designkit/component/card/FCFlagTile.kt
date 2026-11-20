package io.festicore.ui.designkit.component.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.label.FCStatusChip
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.component.label.FCZoneChip
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val ThumbnailHeight = 88.dp
private val IconSize = 20.dp

/** 깃발 타일. 전체가 하나의 버튼이며, [image]가 없으면 깃발 아이콘 대체 이미지를 보인다(iOS `image(_:)`). */
@Composable
fun FCFlagTile(
    name: String,
    status: FCStatusChipStatus,
    zone: String,
    tags: String,
    time: String,
    modifier: Modifier = Modifier,
    image: Painter? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(FestiCoreRadius.r16)
    Column(
        modifier =
            modifier
                .clip(shape)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .padding(FestiCoreSpacing.paddingP12),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        Box(
            modifier =
                Modifier
                    .clearAndSetSemantics {}
                    .fillMaxWidth()
                    .height(ThumbnailHeight)
                    .clip(RoundedCornerShape(FestiCoreRadius.r12)),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(painter = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(FestiCoreSemanticColor.fillStrong.color),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_flag_20),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize),
                        colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = name,
                modifier = Modifier.weight(1f),
                style = FestiCoreTextStyle.baseCalloutBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FCStatusChip(status)
        }
        // iOS는 존 칩의 터치를 끄고 타일 버튼으로 넘긴다. 같은 결과가 되도록 타일 동작을 그대로 건다.
        FCZoneChip(zone, onClick = onClick)
        Row(
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = tags,
                modifier = Modifier.weight(1f),
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = time,
                style = FestiCoreTextStyle.baseCaption1Regular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
                maxLines = 1,
            )
        }
    }
}
