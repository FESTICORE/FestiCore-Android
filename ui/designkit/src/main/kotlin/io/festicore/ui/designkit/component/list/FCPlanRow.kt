package io.festicore.ui.designkit.component.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.label.FCLivePulse
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCTagTone
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val IndicatorSize = 16.dp
private val TimeWidth = 48.dp
private val VerticalPadding = 14.dp
private const val PAST_OPACITY = 0.5f

/** 계획 줄 상태(iOS `FCPlanRow.State`). */
enum class FCPlanRowState {
    Upcoming,
    Now,
    Past,
}

/**
 * 깃발 오늘 계획 한 줄. now는 Live Pulse와 "공연 중"을 붙이고 past는 50%로 흐리게 보인다.
 *
 * @param isMine 내 일정 태그 표시(iOS `isMine(_:)`).
 * @param liveLabel 공연 중 표시 글자(iOS `liveLabel(_:)`).
 */
@Composable
fun FCPlanRow(
    time: String,
    artist: String,
    stage: String,
    state: FCPlanRowState,
    modifier: Modifier = Modifier,
    isMine: Boolean = false,
    liveLabel: String = "공연 중",
) {
    Row(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .padding(vertical = VerticalPadding, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(IndicatorSize), contentAlignment = Alignment.Center) {
            if (state == FCPlanRowState.Now) {
                FCLivePulse()
            }
        }
        Row(
            modifier = Modifier.weight(1f).alpha(if (state == FCPlanRowState.Past) PAST_OPACITY else 1f),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = time,
                modifier = Modifier.width(TimeWidth),
                style =
                    FestiCoreTextStyle.baseCalloutBold.textStyle.copy(
                        color = FestiCoreSemanticColor.labelNormal.color,
                        fontFeatureSettings = "tnum",
                    ),
                maxLines = 1,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM2),
            ) {
                BasicText(
                    text = artist,
                    style = FestiCoreTextStyle.baseCalloutMedium.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = stage,
                        modifier = Modifier.weight(1f, fill = false),
                        style = FestiCoreTextStyle.baseCaption1Regular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state == FCPlanRowState.Now) {
                        BasicText(
                            text = "· $liveLabel",
                            style = FestiCoreTextStyle.baseCaption1Bold.textStyle.copy(
                                color = FestiCoreSemanticColor.secondaryNormal.color,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (isMine) {
                FCTag("내 일정", tone = FCTagTone.Lime)
            }
        }
    }
}
