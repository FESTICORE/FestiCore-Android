package io.festicore.ui.designkit.component.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCZoneChip
import io.festicore.ui.designkit.component.media.FCAvatar
import io.festicore.ui.designkit.component.media.FCAvatarTint
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private const val STALE_OPACITY = 0.6f

/**
 * 크루원 한 줄. 존 · 상태 · N분 전을 표시하고, 60분이 넘은 정보([isStale])는 회색으로 흐리게 보인다.
 *
 * @param avatarTint 이니셜 색(iOS `avatarTint(_:)`).
 * @param isMe 이름 옆에 "나" 태그를 붙인다(iOS `isMe(_:)`).
 * @param isStale 오래된 정보 여부(iOS `isStale(_:)`).
 * @param onZoneTap 존 칩을 눌렀을 때 동작(iOS `onZoneTap(_:)`).
 */
@Composable
fun FCCrewRow(
    name: String,
    zone: String,
    status: String,
    time: String,
    modifier: Modifier = Modifier,
    avatarTint: FCAvatarTint = FCAvatarTint.Gray,
    isMe: Boolean = false,
    isStale: Boolean = false,
    onZoneTap: () -> Unit = {},
) {
    val primaryColor = if (isStale) FestiCoreSemanticColor.labelAssistive.color else FestiCoreSemanticColor.labelNormal.color
    Row(
        modifier =
            modifier
                .alpha(if (isStale) STALE_OPACITY else 1f)
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCAvatar(name, tint = avatarTint)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = name,
                    modifier = Modifier.weight(1f, fill = false),
                    style = FestiCoreTextStyle.baseCalloutBold.textStyle.copy(color = primaryColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isMe) {
                    FCTag("나")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 상태 글자보다 존 이름을 먼저 다 보여 준다(가중치 없는 칩을 먼저 잰다).
                FCZoneChip(zone, onClick = onZoneTap)
                BasicText(
                    text = status,
                    modifier = Modifier.weight(1f),
                    style = FestiCoreTextStyle.baseFootnoteBold.textStyleWithoutLineBox.copy(color = primaryColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BasicText(
            text = time,
            style =
                FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(
                    color = if (isStale) FestiCoreSemanticColor.labelDisable.color else FestiCoreSemanticColor.labelAssistive.color,
                ),
            maxLines = 1,
        )
    }
}
