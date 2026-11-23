package io.festicore.ui.designkit.component.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.label.FCLiveBadge
import io.festicore.ui.designkit.component.label.FCLiveBadgeKind
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCTagTone
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val IconSize = 16.dp

/** 무대 한쪽(지금 또는 다음)의 아티스트와 시간(iOS `FCStageRow.Slot`). */
data class FCStageRowSlot(
    val artist: String,
    val time: String,
)

/**
 * 지금 · 다음 무대 한 줄. [isMine]이면 라임 바탕과 내 일정 태그를 표시한다(iOS `isMine(_:)`).
 */
@Composable
fun FCStageRow(
    stage: String,
    now: FCStageRowSlot,
    next: FCStageRowSlot,
    modifier: Modifier = Modifier,
    isMine: Boolean = false,
) {
    val base = modifier.semantics(mergeDescendants = true) {}
    Column(
        modifier =
            (if (isMine) base.background(FestiCoreSemanticColor.accentBackgroundLime.color) else base)
                .padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = stage,
                modifier = Modifier.weight(1f),
                style = FestiCoreTextStyle.baseFootnoteBold.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNeutral.color),
            )
            if (isMine) {
                FCTag("내 일정", tone = FCTagTone.Lime)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FCStageRowSlotView(now, FCLiveBadgeKind.Now, Modifier.weight(1f))
            Image(
                painter = painterResource(R.drawable.ic_chevron_right_16),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
            )
            FCStageRowSlotView(next, FCLiveBadgeKind.Next, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FCStageRowSlotView(
    slot: FCStageRowSlot,
    kind: FCLiveBadgeKind,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.Top,
    ) {
        FCLiveBadge(kind)
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
            BasicText(
                text = slot.artist,
                style =
                    (if (kind == FCLiveBadgeKind.Now) FestiCoreTextStyle.baseHeadlineBold else FestiCoreTextStyle.baseCalloutMedium)
                        .textStyleWithoutLineBox
                        .copy(color = FestiCoreSemanticColor.labelNormal.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = slot.time,
                style = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.labelAssistive.color,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
