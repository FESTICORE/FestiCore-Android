package io.festicore.ui.designkit.component.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.label.FCStatusChip
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val PlanIconSize = 20.dp
private val DividerWidth = 1.dp

// Figma S01 B(개장까지) 실측. iOS는 pointTitle 글꼴의 기본 줄 높이가 시안 줄 높이보다 커서 간격을 줄여 맞췄고, 같은 값을 쓴다.
private val CountdownLabelToTime = 5.dp
private val CountdownBottomInset = 22.dp

/** 기수의 오늘 계획이 비공개일 때 보이는 안내 카드. */
@Composable
fun FCPlanPrivate(
    modifier: Modifier = Modifier,
    title: String = "오늘 계획은 비공개예요",
    message: String = "기수가 계획을 공개하면 여기에서 볼 수 있어요",
) {
    Row(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(vertical = FestiCoreSpacing.paddingP20, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_circle_info_20),
            contentDescription = null,
            modifier = Modifier.size(PlanIconSize),
            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM2),
        ) {
            BasicText(
                text = title,
                style = FestiCoreTextStyle.baseCalloutMedium.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
            )
            BasicText(
                text = message,
                style = FestiCoreTextStyle.baseCaption1Regular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
            )
        }
    }
}

/** 개장까지 남은 시간 카드. */
@Composable
fun FCCountdownCard(
    label: String,
    time: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(start = FestiCoreSpacing.paddingP20, end = FestiCoreSpacing.paddingP20, top = FestiCoreSpacing.paddingP20)
                .padding(bottom = CountdownBottomInset),
    ) {
        BasicText(
            text = label,
            style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
        BasicText(
            text = time,
            modifier = Modifier.padding(top = CountdownLabelToTime),
            style = FestiCoreTextStyle.pointTitle.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        BasicText(
            text = sub,
            modifier = Modifier.padding(top = FestiCoreSpacing.marginM6),
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
        )
    }
}

/** 마지막 셔틀 · 막차 시간 카드. */
@Composable
fun FCLastRideCard(
    shuttle: String,
    train: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(FestiCoreSpacing.paddingP16)
                .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        LastRideColumn("마지막 셔틀", shuttle, Modifier.weight(1f))
        Box(modifier = Modifier.width(DividerWidth).fillMaxHeight().background(FestiCoreSemanticColor.lineNormal.color))
        LastRideColumn("막차", train, Modifier.weight(1f))
    }
}

@Composable
private fun LastRideColumn(
    label: String,
    time: String,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM2),
    ) {
        BasicText(
            text = label,
            style = FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
        )
        BasicText(
            text = time,
            // iOS monospacedDigit: 숫자 폭을 고르게 한다.
            style =
                FestiCoreTextStyle.baseTitle1Bold.textStyle.copy(
                    color = FestiCoreSemanticColor.labelNormal.color,
                    fontFeatureSettings = "tnum",
                ),
        )
    }
}

/** 기수 상세 상단 헤더. 이름은 접근성 제목이다. */
@Composable
fun FCBearerHeader(
    name: String,
    status: FCStatusChipStatus,
    statusText: String,
    followers: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.padding(
                top = FestiCoreSpacing.paddingP16,
                start = FestiCoreSpacing.paddingP16,
                end = FestiCoreSpacing.paddingP16,
                bottom = FestiCoreSpacing.paddingP8,
            ),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        BasicText(
            text = name,
            modifier = Modifier.semantics { heading() },
            style = FestiCoreTextStyle.baseTitle1Bold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FCStatusChip(status)
            BasicText(
                text = statusText,
                modifier = Modifier.weight(1f),
                style = FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicText(
            text = followers,
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
        )
    }
}
