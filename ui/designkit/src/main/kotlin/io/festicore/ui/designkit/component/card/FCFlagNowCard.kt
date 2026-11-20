package io.festicore.ui.designkit.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import io.festicore.ui.designkit.component.label.FCZoneChip
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private const val MUTED_OPACITY = 0.5f

/** iOS `FCFlagNowCard.State`: 지금 활동 중 · 흐리게(최근 활동) · 오늘 활동 끝. */
sealed interface FCFlagNowCardState {
    data class Active(
        val zone: String,
        val activity: String,
        val source: String,
    ) : FCFlagNowCardState

    data class Muted(
        val zone: String,
        val activity: String,
        val source: String,
    ) : FCFlagNowCardState

    data class Ended(val text: String = "오늘 활동을 마쳤어요") : FCFlagNowCardState
}

/** 깃발의 지금 활동 카드. [label]은 위 라벨(iOS `label(_:)`), [onZoneTap]은 존 칩 동작이다. */
@Composable
fun FCFlagNowCard(
    state: FCFlagNowCardState,
    modifier: Modifier = Modifier,
    label: String = "지금",
    onZoneTap: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        BasicText(
            text = label,
            style = FestiCoreTextStyle.baseCaption1Medium.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
        )
        when (state) {
            is FCFlagNowCardState.Active -> NowContent(state.zone, state.activity, state.source, isMuted = false, onZoneTap = onZoneTap)

            is FCFlagNowCardState.Muted -> NowContent(state.zone, state.activity, state.source, isMuted = true, onZoneTap = onZoneTap)

            is FCFlagNowCardState.Ended ->
                BasicText(
                    text = state.text,
                    style = FestiCoreTextStyle.baseCalloutMedium.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
                )
        }
    }
}

@Composable
private fun NowContent(
    zone: String,
    activity: String,
    source: String,
    isMuted: Boolean,
    onZoneTap: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCZoneChip(zone, modifier = Modifier.alpha(if (isMuted) MUTED_OPACITY else 1f), onClick = onZoneTap)
        BasicText(
            text = activity,
            style =
                FestiCoreTextStyle.baseHeadlineBold.textStyle.copy(
                    color = if (isMuted) FestiCoreSemanticColor.labelAssistive.color else FestiCoreSemanticColor.labelNormal.color,
                ),
        )
    }
    BasicText(
        text = source,
        style = FestiCoreTextStyle.baseCaption1Regular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
    )
}
