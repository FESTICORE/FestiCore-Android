package io.festicore.feature.festival.zonemap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.festicore.domain.festival.FestivalZoneMap
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCTagTone
import io.festicore.ui.designkit.component.media.FCAvatar
import io.festicore.ui.designkit.component.media.FCAvatarSize
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

// iOS FestivalZoneMapCanvas.swift Metric 값 그대로.
private val HeaderHeight = 34.dp
private val HeaderTrailingPadding = 10.dp
private const val HEADER_NAME_MIN_SCALE = 0.85f
private val AreaCornerRadius = 10.dp
private val AreaSelectedBorder = 1.5.dp
private val LabelRadius = 6.dp
private val LabelBorder = 1.dp
private val LabelVerticalPadding = 3.dp
private val LandmarkHeight = 20.dp
private val AreaHeaderInset = 4.dp
private val MarkerInset = 12.dp
private val MarkerSpacing = 12.dp

/** 이름표 줄(32) + 마커 원(40)보다 낮으면 마커를 이름표 줄에 둔다. */
internal val CompactAreaHeight = 72.dp
private val MarkerBox = 40.dp
private val MarkerLabeledWidth = 52.dp
private val FollowedSize = 36.dp
private val FollowedIcon = 16.dp
private val OtherSize = 24.dp
private val OtherIcon = 12.dp
private val MarkerBorder = 2.dp
private val MarkerLabelSpacing = 8.dp
private val MarkerLabelHeight = 22.dp
private val MarkerLabelHorizontalPadding = 6.dp
private val MarkerLabelShadowRadius = 2.dp

// Figma Map Marker 그림자 blur 6. iOS radius 3(= blur ÷ 2)을 designkit 선례(FCSortMenu)처럼 Compose radius에 그대로 쓴다.
private val MarkerShadowRadius = 3.dp
private val MarkerShadowY = 2.dp
private const val STALE_OPACITY = 0.4f
private val ClusterSize = 28.dp
private val LegendHeight = 28.dp
private val LegendHorizontalPadding = 10.dp
private val LegendDot = 8.dp
private val LegendDotBorder = 1.dp
private val LegendShadowRadius = 4.dp
private val LegendShadowY = 2.dp

/** S06 E: 고른 존 아래에 카드 바깥 여백(16)과 합쳐 50을 남긴다. */
internal val RevealMargin = 34.dp

/** 지도 높이 = 너비 / 비율(비율이 없으면 1). */
internal fun zoneMapHeight(map: FestivalZoneMap, width: Dp): Dp = width / (map.aspect ?: 1.0).toFloat()

/**
 * S06 지도 본문(iOS `FestivalZoneMapCanvas`). 존 영역(0~1 비율)을 지도 너비에 맞춰 그리고 영역 안에 마커를 놓는다.
 * 빈 곳을 누르면 [onBackgroundTap]. 존 · 머리글은 머리글 → 존 순서로 쌓는다.
 */
@Composable
internal fun ZoneMapCanvas(
    map: FestivalZoneMap,
    model: ZoneMapModel,
    selectedZoneId: String?,
    width: Dp,
    onZoneTap: (String) -> Unit,
    onBackgroundTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = zoneMapHeight(map, width)
    // 존이 먼저 탭을 소비하므로 빈 곳 탭만 여기에 닿는다.
    Box(modifier = modifier.size(width, height).pointerInput(onBackgroundTap) { detectTapGestures { onBackgroundTap() } }) {
        map.stageHeaders.forEach { header ->
            ZoneMapStageHeader(
                header = header,
                modifier = Modifier.offset(width * header.x.toFloat(), height * header.y.toFloat()).width(width * header.width.toFloat()),
            )
        }
        map.zones.forEach { zone ->
            val area = zone.area ?: return@forEach
            val areaHeight = height * area.height.toFloat()
            ZoneMapArea(
                zone = zone,
                markers = model.markers[zone.id].orEmpty(),
                isSelected = zone.id == selectedZoneId,
                isCompact = areaHeight < CompactAreaHeight,
                onClick = { onZoneTap(zone.id) },
                modifier = Modifier.offset(
                    width * area.x.toFloat(),
                    height * area.y.toFloat(),
                ).size(width * area.width.toFloat(), areaHeight),
            )
        }
    }
}

/** 무대 이름과 지금 공연 중인 아티스트 줄(iOS `FestivalZoneMapStageHeader`). */
@Composable
private fun ZoneMapStageHeader(header: FestivalZoneMap.StageHeader, modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .height(HeaderHeight)
                .background(FestiCoreSemanticColor.fillNormal.color, RoundedCornerShape(FestiCoreRadius.r8))
                .padding(start = FestiCoreSpacing.paddingP12, end = HeaderTrailingPadding),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Figma 무대 이름(10pt)은 토큰보다 작아서 좁을 때만 0.85배까지 줄여 맞춘다.
        val style = FestiCoreTextStyle.baseCaption2Bold.textStyleWithoutLineBox
        BasicText(
            text = header.name.uppercase(),
            modifier = Modifier.weight(1f),
            style = style.copy(color = FestiCoreSemanticColor.labelAssistive.color),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * HEADER_NAME_MIN_SCALE, maxFontSize = style.fontSize),
        )
        // 지금 공연 아티스트는 자르지 않고, 좁으면 무대 이름을 줄인다.
        header.nowPlaying?.let { FCTag(label = "지금 $it", tone = FCTagTone.Inverse) }
    }
}

/** 존 영역 하나(iOS `FestivalZoneMapArea`). 이름표 · 표지 · 마커를 그리고 누르면 존 카드를 연다. 넘친 이름표 · 마커는 영역 밖으로 그린다. */
@Composable
private fun ZoneMapArea(
    zone: FestivalZoneMap.Zone,
    markers: List<ZoneMapModel.Marker>,
    isSelected: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AreaCornerRadius)
    val fill = if (isSelected) FestiCoreSemanticColor.accentBackgroundLime.color else FestiCoreSemanticColor.backgroundElevated.color
    Box(
        modifier =
            modifier
                .background(fill, shape)
                .then(if (isSelected) Modifier.border(AreaSelectedBorder, FestiCoreSemanticColor.staticLime.color, shape) else Modifier)
                .semantics(mergeDescendants = true) { selected = isSelected }
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(AreaHeaderInset),
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM4),
                verticalAlignment = Alignment.Top,
            ) {
                ZoneNameLabel(zone.name, isSelected)
                if (isCompact && markers.isNotEmpty()) {
                    zone.landmark?.let { LandmarkBadge(it, Modifier.padding(start = FestiCoreSpacing.marginM4)) }
                    Spacer(Modifier.weight(1f))
                    MarkerStack(markers, Modifier.padding(end = MarkerInset - AreaHeaderInset))
                } else {
                    Spacer(Modifier.weight(1f))
                    zone.landmark?.let { LandmarkBadge(it) }
                }
            }
            if (!isCompact) MarkerRow(markers)
        }
    }
}

@Composable
private fun ZoneNameLabel(name: String, isSelected: Boolean) {
    val shape = RoundedCornerShape(LabelRadius)
    // Figma Zone Label: 선택은 Lime 위 Static/Ink, 기본은 Fill/Normal 위 Label/Normal. 좁은 영역 밖으로 넘쳐도 자르지 않는다.
    BasicText(
        text = name,
        modifier =
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .background(if (isSelected) FestiCoreSemanticColor.staticLime.color else FestiCoreSemanticColor.fillNormal.color, shape)
                .then(if (isSelected) Modifier.border(LabelBorder, FestiCoreSemanticColor.staticInk.color, shape) else Modifier)
                .padding(horizontal = FestiCoreSpacing.paddingP8, vertical = LabelVerticalPadding),
        style =
            FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(
                color = if (isSelected) FestiCoreSemanticColor.staticInk.color else FestiCoreSemanticColor.labelNormal.color,
            ),
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun LandmarkBadge(landmark: String, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .height(LandmarkHeight)
                .border(LabelBorder, FestiCoreSemanticColor.lineStrong.color, CircleShape)
                .padding(horizontal = FestiCoreSpacing.paddingP8),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = landmark,
            style = FestiCoreTextStyle.baseCaption2Bold.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelAssistive.color),
            maxLines = 1,
        )
    }
}

@Composable
private fun MarkerStack(markers: List<ZoneMapModel.Marker>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(MarkerSpacing), verticalAlignment = Alignment.Top) {
        markers.forEach { ZoneMapMarker(it) }
    }
}

/** 이름표 아래 마커 줄. 한 줄에 다 놓을 수 없으면 `+N` 묶음으로 바꾼다. */
@Composable
private fun MarkerRow(markers: List<ZoneMapModel.Marker>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (markerTotalWidth(markers) <= maxWidth - MarkerInset * 2) {
            MarkerStack(markers, Modifier.padding(start = MarkerInset))
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { ZoneMapCluster(markers.size) }
        }
    }
}

/** 마커를 한 줄에 놓을 때 필요한 너비. 팔로우 깃발 52, 그 밖 40, 사이 12. iOS `FestivalZoneMapMarker.totalWidth`. */
internal fun markerTotalWidth(markers: List<ZoneMapModel.Marker>): Dp {
    if (markers.isEmpty()) return 0.dp
    val widths = markers.map { if (it.kind == ZoneMapModel.MarkerKind.FollowedFlag) MarkerLabeledWidth else MarkerBox }
    return widths.fold(0.dp) { sum, width -> sum + width } + MarkerSpacing * (markers.size - 1)
}

@Composable
private fun markerShadow(radius: Dp = MarkerShadowRadius) =
    Shadow(radius = radius, color = FestiCoreSemanticColor.staticalphaBlack20.color, offset = DpOffset(0.dp, MarkerShadowY))

/** 존 안 마커(iOS `FestivalZoneMapMarker`). 팔로우 깃발 · 크루는 이름표를, 다른 깃발은 원만 그린다. 30분 넘게 소식이 없으면 흐리게. */
@Composable
private fun ZoneMapMarker(marker: ZoneMapModel.Marker) {
    Column(
        modifier = Modifier.alpha(if (marker.isStale) STALE_OPACITY else 1f).clearAndSetSemantics { contentDescription = marker.name },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MarkerLabelSpacing),
    ) {
        Box(modifier = Modifier.size(MarkerBox), contentAlignment = Alignment.Center) {
            when (val kind = marker.kind) {
                ZoneMapModel.MarkerKind.FollowedFlag ->
                    MarkerBubble(
                        size = FollowedSize,
                        fill = FestiCoreSemanticColor.staticLime.color,
                        stroke = FestiCoreSemanticColor.staticWhite.color,
                        iconSize = FollowedIcon,
                        iconTint = FestiCoreSemanticColor.staticInk.color,
                    )

                // Figma Map Marker flag-small: Label/Assistive 원, Background/Normal 테두리 · 아이콘
                ZoneMapModel.MarkerKind.OtherFlag ->
                    MarkerBubble(
                        size = OtherSize,
                        fill = FestiCoreSemanticColor.labelAssistive.color,
                        stroke = FestiCoreSemanticColor.backgroundNormal.color,
                        iconSize = OtherIcon,
                        iconTint = FestiCoreSemanticColor.backgroundNormal.color,
                    )

                is ZoneMapModel.MarkerKind.Crew ->
                    FCAvatar(
                        initial = kind.initial,
                        modifier =
                            Modifier
                                .dropShadow(CircleShape, markerShadow())
                                .background(FestiCoreSemanticColor.staticWhite.color, CircleShape)
                                .border(MarkerBorder, FestiCoreSemanticColor.staticWhite.color, CircleShape)
                                .padding(MarkerBorder),
                        tint = kind.tint,
                        size = FCAvatarSize.Small,
                    )
            }
        }
        if (marker.kind != ZoneMapModel.MarkerKind.OtherFlag) {
            val labelShape: Shape = RoundedCornerShape(FestiCoreRadius.r4)
            // Figma Map Marker 이름표: 깃발 Primary/Normal, 크루 Label/Normal, 배경 Background/Elevated
            val labelColor =
                if (marker.kind == ZoneMapModel.MarkerKind.FollowedFlag) {
                    FestiCoreSemanticColor.primaryNormal.color
                } else {
                    FestiCoreSemanticColor.labelNormal.color
                }
            Box(
                modifier =
                    Modifier
                        .height(MarkerLabelHeight)
                        .dropShadow(labelShape, markerShadow(MarkerLabelShadowRadius))
                        .background(FestiCoreSemanticColor.backgroundElevated.color, labelShape)
                        .padding(horizontal = MarkerLabelHorizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = marker.name,
                    style = FestiCoreTextStyle.baseCaption2Bold.textStyleWithoutLineBox.copy(color = labelColor),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MarkerBubble(
    size: Dp,
    fill: Color,
    stroke: Color,
    iconSize: Dp,
    iconTint: Color,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .dropShadow(CircleShape, markerShadow())
                .background(fill, CircleShape)
                .border(MarkerBorder, stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_flag_16),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(iconTint),
        )
    }
}

/** 영역에 다 놓을 수 없는 마커 묶음(S06 C `+3`, iOS `FestivalZoneMapCluster`). */
@Composable
private fun ZoneMapCluster(count: Int) {
    Box(
        modifier = Modifier.height(MarkerBox).clearAndSetSemantics {
            contentDescription = "${count}개"
        },
        contentAlignment = Alignment.Center,
    ) {
        // Figma Map Marker count: Inverse/Background 원, Inverse/Label 글자, Background/Elevated 테두리
        Box(
            modifier =
                Modifier
                    .size(ClusterSize)
                    .dropShadow(CircleShape, markerShadow())
                    .background(FestiCoreSemanticColor.inverseBackground.color, CircleShape)
                    .border(MarkerBorder, FestiCoreSemanticColor.backgroundElevated.color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "+$count",
                style = FestiCoreTextStyle.baseCaption1Bold.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.inverseLabel.color),
                maxLines = 1,
            )
        }
    }
}

/** 지도 마커 범례(iOS `FestivalZoneMapLegend`). 상황에 따라 항목을 끈다. */
@Composable
internal fun ZoneMapLegend(
    showsFollowed: Boolean,
    showsCrew: Boolean,
    showsOther: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(LegendHeight)
                .dropShadow(
                    CircleShape,
                    Shadow(
                        radius = LegendShadowRadius,
                        color = FestiCoreSemanticColor.staticalphaBlack20.color,
                        offset = DpOffset(0.dp, LegendShadowY),
                    ),
                ).background(FestiCoreSemanticColor.backgroundElevated.color, CircleShape)
                .padding(horizontal = LegendHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsFollowed) LegendItem("팔로우 깃발", FestiCoreSemanticColor.staticLime.color, bordered = true)
        if (showsCrew) LegendItem("크루", FestiCoreSemanticColor.accentPointBlue.color, bordered = false)
        if (showsOther) LegendItem("다른 깃발", FestiCoreSemanticColor.labelAssistive.color, bordered = false)
    }
}

@Composable
private fun LegendItem(
    title: String,
    color: Color,
    bordered: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM4), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(LegendDot)
                    .background(color, CircleShape)
                    .then(
                        if (bordered) Modifier.border(LegendDotBorder, FestiCoreSemanticColor.staticInk.color, CircleShape) else Modifier,
                    ),
        )
        BasicText(
            text = title,
            style = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
    }
}
