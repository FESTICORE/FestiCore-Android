package io.festicore.feature.festival.zonemap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.divider.FCDivider
import io.festicore.ui.designkit.component.list.FCActiveFlagRow
import io.festicore.ui.designkit.component.list.FCCrewRow
import io.festicore.ui.designkit.component.list.FCListRow
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

// iOS FestivalZoneMapCard.swift · FestivalZoneMapSkeletonView.swift Metric 값 그대로.
private val CardIcon = 20.dp
private val CardIconWrap = 36.dp
private val InfoIcon = 16.dp
private val TitleSpacing = 2.dp
private val StackSpacing = 4.dp
private val HeaderGap = 4.dp
private val InfoBottomGap = 8.dp
private val CardBottomPadding = 8.dp
private const val CARD_SHADOW_OPACITY = 0.12f
private val CardShadowRadius = 2.dp
private val CardShadowY = 1.dp
private val SkeletonHeaderHeight = 26.dp
private val SkeletonAreaRadius = 10.dp
private val SkeletonRowHeights = listOf(98.dp, 94.dp, 58.dp, 160.dp)

/** S06 B~E 존 카드(iOS `FestivalZoneMapCard`). 고른 존의 깃발 · 크루를 보여 준다. */
@Composable
internal fun ZoneMapCard(card: ZoneMapModel.Card, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(FestiCoreRadius.r20)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                // 그림자는 카드 배경에만 준다.
                .dropShadow(
                    shape,
                    Shadow(
                        radius = CardShadowRadius,
                        color = FestiCoreSemanticColor.staticBlack.color,
                        offset = DpOffset(0.dp, CardShadowY),
                        alpha = CARD_SHADOW_OPACITY,
                    ),
                ).background(FestiCoreSemanticColor.backgroundElevated.color, shape)
                .padding(
                    top = FestiCoreSpacing.paddingP16,
                    start = FestiCoreSpacing.paddingP16,
                    end = FestiCoreSpacing.paddingP16,
                    bottom = CardBottomPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(StackSpacing),
    ) {
        CardHeader(card)
        Spacer(Modifier.height(HeaderGap))
        if (card.isEmpty) {
            ZoneMapInfoLine("지금 이 존에 있는 깃발·크루가 없어요.")
        } else {
            card.flags.forEach { flag ->
                FCActiveFlagRow(name = flag.name, meta = flag.meta, time = flag.time, status = flag.status, onClick = {})
            }
            card.crew.forEach { member ->
                FCCrewRow(
                    name = member.nickname,
                    zone = member.zone,
                    status = member.status,
                    time = member.time,
                    avatarTint = member.tint,
                    isMe = member.isMe,
                    isStale = member.isStale,
                )
            }
            if (card.hasStaleCrew) ZoneMapInfoLine("30분 넘게 소식이 없어서 흐리게 보여요.")
        }
        if (card.isEmpty || card.hasStaleCrew) Spacer(Modifier.height(InfoBottomGap))
    }
}

@Composable
private fun CardHeader(card: ZoneMapModel.Card) {
    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(CardIconWrap).background(FestiCoreSemanticColor.fillNormal.color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_location_dot_20),
                contentDescription = null,
                modifier = Modifier.size(CardIcon),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNormal.color),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(TitleSpacing)) {
            BasicText(
                text = card.name,
                style = FestiCoreTextStyle.baseHeadlineBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = card.meta,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** `ic_circle_info_16` + 보조 문구(카드 정보 줄 · 존 목록 안내에 함께 쓴다). */
@Composable
internal fun ZoneMapInfoLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_circle_info_16),
            contentDescription = null,
            modifier = Modifier.size(InfoIcon),
            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
        )
        BasicText(
            text = text,
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
        )
    }
}

/** 존 목록 한 행. */
internal data class ZoneMapListRow(
    val id: String,
    val name: String,
    val value: String,
)

/** S06 I. 지도 영역이 없는 팩은 존 목록으로 보여 준다(iOS `FestivalZoneMapList`). 행을 누르면 선택만 바뀐다(iOS 그대로, 설계 E11). */
@Composable
internal fun ZoneMapList(
    rows: List<ZoneMapListRow>,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))) {
        rows.forEachIndexed { index, row ->
            if (index > 0) FCDivider(modifier = Modifier.padding(horizontal = FestiCoreSpacing.paddingP16))
            FCListRow(label = row.name, value = row.value, onClick = { onTap(row.id) })
        }
    }
}

/** 받아 둔 팩에서 존 지도를 읽는 동안 무대 머리글과 존 영역 자리를 보여 준다(iOS `FestivalZoneMapSkeletonView`). */
@Composable
internal fun ZoneMapSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(FestiCoreSpacing.paddingP16)
                .clearAndSetSemantics { contentDescription = "존 지도를 불러오는 중" },
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12)) {
            repeat(2) {
                FCSkeletonView(FCSkeletonShape.Round(FestiCoreRadius.r8), Modifier.weight(1f).height(SkeletonHeaderHeight))
            }
        }
        SkeletonRowHeights.forEach { height ->
            Row(modifier = Modifier.height(height), horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12)) {
                repeat(2) { FCSkeletonView(FCSkeletonShape.Round(SkeletonAreaRadius), Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}
