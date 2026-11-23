package io.festicore.feature.timetable.main

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.domain.timetable.TimetableDay
import io.festicore.domain.timetable.TimetableOverlap
import io.festicore.domain.timetable.TimetableSet
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.timetable.FCNowLine
import io.festicore.ui.designkit.component.timetable.FCOverlapHighlight
import io.festicore.ui.designkit.component.timetable.FCSetBlock
import io.festicore.ui.designkit.component.timetable.FCSetBlockState
import io.festicore.ui.designkit.component.timetable.FCStageHeader
import io.festicore.ui.designkit.component.timetable.fcSetBlockHeight
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import kotlin.math.abs
import kotlin.math.ceil

/** 1분 높이(iOS `FCSetBlock.height(minutes: 1)`). */
private val PointsPerMinute = fcSetBlockHeight(1)
private const val MIN_ZOOM = 0.5
private const val MAX_ZOOM = 2.0
private val MinBlockHeight = 8.dp
private const val FILLING_STAGE_COUNT = 2
private val SwipeMinimumDistance = 30.dp
private const val SWIPE_THRESHOLD = 80f
private const val SWIPE_RATIO = 1.5f
private const val DEFAULT_START = 12 * 60
private const val DEFAULT_END = 24 * 60
private val LeadingInset = 56.dp
private val HourLineInset = 52.dp
private val OverlapBarInset = 46.dp
private val OverlapBarWidth = 3.dp
private val WideColumnWidth = 140.dp
private val BlockInset = 2.dp
private val LineWidth = 1.dp
private val HourLabelHalfHeight = 8.dp
private val NowLineHeight = 20.dp
private const val PAST_OPACITY = 0.4f
private const val HIDDEN_OPACITY = 0.2f
private val DotColors =
    listOf(FestiCoreSemanticColor.accentPointLime, FestiCoreSemanticColor.accentPointPurple, FestiCoreSemanticColor.accentPointBlue)

/**
 * 무대 머리글과 시간 격자(iOS `TimetableBoard`). 무대가 3개 이상이면 가로로도 스크롤한다(설계 T5).
 * 두 손가락으로 벌리거나 모으면 시간축을 확대 · 축소하고 손을 뗄 때만 [onZoomEnd]로 확정값을 보낸다(설계 T6).
 * 가로로 스크롤하지 않는 보드는 좌우로 밀어 전날 · 다음 날로 넘긴다([onDaySwipe]: 다음 날 1, 전날 -1).
 */
@Composable
internal fun TimetableBoard(
    day: TimetableDay,
    state: TimetableState,
    onSetClicked: (String) -> Unit,
    onZoomEnd: (Double) -> Unit,
    onDaySwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var gestureZoom by remember { mutableFloatStateOf(1f) }
    val currentZoom by rememberUpdatedState(state.zoom)
    val currentOnZoomEnd by rememberUpdatedState(onZoomEnd)
    val currentOnDaySwipe by rememberUpdatedState(onDaySwipe)
    val metrics = BoardMetrics(day, clampedZoom(state.zoom, gestureZoom))
    val isWide = day.stages.size > FILLING_STAGE_COUNT
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    val density = LocalDensity.current

    // 날짜가 바뀌면 현재 시각의 시 줄을 위로 맞춘다(iOS `task(id: day.id)` + `scrollTo(hour, anchor: .top)`).
    LaunchedEffect(day.id) {
        val now = state.nowMinute ?: return@LaunchedEffect
        vertical.scrollTo(with(density) { metrics.y(now / 60 * 60).roundToPx() })
    }

    Column(
        modifier =
            modifier
                .pointerInput(Unit) {
                    detectTwoFingerZoom(
                        onZoom = { gestureZoom = it },
                        onEnd = { total ->
                            gestureZoom = 1f
                            currentOnZoomEnd(clampedZoom(currentZoom, total))
                        },
                    )
                }.then(
                    if (isWide) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            detectDaySwipe(SwipeMinimumDistance.toPx()) { offset -> currentOnDaySwipe(offset) }
                        }
                    },
                ),
    ) {
        StageHeaders(day, isWide, horizontal)
        Box(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(vertical)) {
            Box(modifier = if (isWide) Modifier.horizontalScroll(horizontal) else Modifier.fillMaxWidth()) {
                Grid(day, state, metrics, isWide, onSetClicked)
            }
        }
    }
}

/**
 * 날짜를 넘길 스와이프인지 판정한다(iOS `TimetableBoard.dayOffset(forSwipe:)`). 가로로 80 넘게, 세로의 1.5배를 넘게 밀었을 때만 반응한다.
 * 왼쪽으로 밀면 다음 날(1), 오른쪽으로 밀면 전날(-1), 아니면 null.
 */
internal fun dayOffsetForSwipe(dx: Float, dy: Float): Int? {
    if (abs(dx) <= SWIPE_THRESHOLD || abs(dx) <= abs(dy) * SWIPE_RATIO) return null
    return if (dx < 0) 1 else -1
}

private fun clampedZoom(zoom: Double, magnification: Float): Double = (zoom * magnification).coerceIn(MIN_ZOOM, MAX_ZOOM)

/** 그날의 시간 범위와 세로 좌표(iOS `startHour` · `endHour` · `y(_:)`). */
private class BoardMetrics(
    day: TimetableDay,
    private val zoom: Double,
) {
    val startHour: Int = (day.sets.minOfOrNull { it.start } ?: DEFAULT_START) / 60
    val endHour: Int = maxOf(startHour + 1, ceil((day.sets.maxOfOrNull { it.end } ?: DEFAULT_END) / 60.0).toInt())
    val gridHeight: Dp get() = y(endHour * 60)

    fun y(minute: Int): Dp = PointsPerMinute * ((minute - startHour * 60) * zoom).toFloat()

    fun wideWidth(stageCount: Int): Dp =
        LeadingInset + WideColumnWidth * stageCount + FestiCoreSpacing.paddingP8 * (stageCount - 1) + FestiCoreSpacing.paddingP16
}

/**
 * 무대 머리글. 세로 스크롤 밖에 두어 고정하고, 넓은 보드에서는 격자의 가로 스크롤 값만큼 같이 옮긴다(설계 T5).
 * 머리글은 입력을 받지 않아 머리글을 끌어 가로로 넘기지는 않는다(iOS는 스크롤 내용 안이라 끌린다).
 */
@Composable
private fun StageHeaders(
    day: TimetableDay,
    isWide: Boolean,
    horizontal: ScrollState,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .background(FestiCoreSemanticColor.backgroundNormal.color),
    ) {
        Row(
            modifier =
                if (isWide) {
                    Modifier
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .width(BoardMetrics(day, 1.0).wideWidth(day.stages.size))
                        .graphicsLayer { translationX = -horizontal.value.toFloat() }
                } else {
                    Modifier.fillMaxWidth()
                }.padding(start = LeadingInset, end = FestiCoreSpacing.paddingP16),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        ) {
            day.stages.forEachIndexed { index, stage ->
                FCStageHeader(
                    name = stage.name,
                    modifier = if (isWide) Modifier.width(WideColumnWidth) else Modifier.weight(1f),
                    dotColor = DotColors[index % DotColors.size],
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(LineWidth)
                    .background(FestiCoreSemanticColor.lineNormal.color),
        )
    }
}

@Composable
private fun Grid(
    day: TimetableDay,
    state: TimetableState,
    metrics: BoardMetrics,
    isWide: Boolean,
    onSetClicked: (String) -> Unit,
) {
    // 첫 시각 라벨은 선 위로 반만큼 올라가므로 머리글에 가리지 않게 띄운다. 끝까지 내려도 마지막 공연이 아래에서 20 떨어진다.
    Box(
        modifier =
            Modifier
                .then(if (isWide) Modifier.width(metrics.wideWidth(day.stages.size)) else Modifier.fillMaxWidth())
                .padding(top = HourLabelHalfHeight)
                .fcScrollBottomInset()
                .height(metrics.gridHeight),
    ) {
        HourLines(metrics)
        state.visibleOverlaps.forEach { OverlapBand(it, metrics) }
        Row(
            modifier = Modifier.fillMaxSize().padding(start = LeadingInset, end = FestiCoreSpacing.paddingP16),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        ) {
            day.stages.forEach { stage ->
                Box(modifier = (if (isWide) Modifier.width(WideColumnWidth) else Modifier.weight(1f)).fillMaxHeight()) {
                    day.sets.filter { it.stageId == stage.id }.forEach { set -> Block(set, state, metrics, onSetClicked) }
                }
            }
        }
        val now = state.nowMinute
        if (now != null && now in metrics.startHour * 60..metrics.endHour * 60) {
            FCNowLine(
                time = TimetableDay.clock(now),
                modifier = Modifier.fillMaxWidth().offset(y = metrics.y(now) - NowLineHeight / 2),
            )
        }
    }
}

@Composable
private fun HourLines(metrics: BoardMetrics) {
    for (hour in metrics.startHour until metrics.endHour) {
        Box(modifier = Modifier.fillMaxWidth().offset(y = metrics.y(hour * 60))) {
            Box(
                modifier =
                    Modifier
                        .padding(start = HourLineInset, end = FestiCoreSpacing.paddingP16)
                        .fillMaxWidth()
                        .height(LineWidth)
                        .background(FestiCoreSemanticColor.lineNormal.color),
            )
            BasicText(
                text = TimetableDay.clock(hour * 60),
                modifier = Modifier.padding(start = FestiCoreSpacing.paddingP12).offset(y = -HourLabelHalfHeight),
                style = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.labelAssistive.color,
                ),
            )
        }
    }
}

@Composable
private fun Block(
    set: TimetableSet,
    state: TimetableState,
    metrics: BoardMetrics,
    onSetClicked: (String) -> Unit,
) {
    // 축소해도 아주 짧은 공연이 사라지지 않게 최소 높이를 둔다.
    val height = maxOf(MinBlockHeight, metrics.y(set.end) - metrics.y(set.start) - BlockInset * 2)
    FCSetBlock(
        artist = set.artist,
        time = set.timeRange,
        modifier =
            Modifier
                .offset(y = metrics.y(set.start) + BlockInset)
                .fillMaxWidth()
                .height(height)
                .alpha(blockOpacity(set, state))
                .clickable { onSetClicked(set.id) },
        state = blockState(set, state),
    )
}

@Composable
private fun OverlapBand(overlap: TimetableOverlap, metrics: BoardMetrics) {
    Box(
        modifier =
            Modifier
                .offset(y = metrics.y(overlap.start))
                .fillMaxWidth()
                .height(metrics.y(overlap.end) - metrics.y(overlap.start))
                .clearAndSetSemantics { contentDescription = "내 일정 ${overlap.minutes}분 겹침" },
        contentAlignment = Alignment.CenterStart,
    ) {
        FCOverlapHighlight(
            modifier =
                Modifier
                    .padding(start = HourLineInset)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = FestiCoreRadius.r4, bottomStart = FestiCoreRadius.r4)),
        )
        Box(
            modifier =
                Modifier
                    .padding(start = OverlapBarInset)
                    .width(OverlapBarWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(OverlapBarWidth))
                    .background(FestiCoreSemanticColor.accentPointOrange.color),
        )
        BasicText(
            text = "${overlap.minutes}분\n겹침",
            modifier = Modifier.padding(start = FestiCoreSpacing.paddingP12),
            style = FestiCoreTextStyle.baseCaption2Bold.textStyleWithoutLineBox.copy(
                color = FestiCoreSemanticColor.accentPointOrange.color,
            ),
        )
    }
}

/** 변경 → (경고 중) 겹침 → 내 일정 → 기본(iOS `blockState`). */
internal fun blockState(set: TimetableSet, state: TimetableState): FCSetBlockState {
    val previousTime = set.previousTime
    val change = set.startChange
    if (previousTime != null && change != null) return FCSetBlockState.Changed(previousTime, change)
    if (set.id in state.overlappingSetIds) return FCSetBlockState.Overlap()
    return if (set.id in state.mySetIds) FCSetBlockState.Mine else FCSetBlockState.Default
}

/** "내 일정만"인데 내 공연이 아니면 0.2, 끝난 공연은 0.4(iOS `opacity`). */
internal fun blockOpacity(set: TimetableSet, state: TimetableState): Float {
    if (state.showsMineOnly && set.id !in state.mySetIds) return HIDDEN_OPACITY
    val now = state.nowMinute
    if (now != null && set.end <= now) return PAST_OPACITY
    return 1f
}

/**
 * 손가락이 2개 이상일 때만 배율을 곱해 [onZoom]으로 알리고 그 이벤트를 소비한다. 손을 모두 떼면 누적 배율로 [onEnd]를 부른다.
 * 손가락 1개 이벤트는 소비하지 않아 스크롤이 그대로 동작한다(설계 T6). 자식 스크롤보다 먼저 보도록 Initial 단계에서 읽는다.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTwoFingerZoom(
    onZoom: (Float) -> Unit,
    onEnd: (Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var total = 1f
        var isZooming = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    total *= zoom
                    isZooming = true
                    onZoom(total)
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (isZooming) onEnd(total)
    }
}

/**
 * 세로 전용 보드의 좌우 스와이프(iOS `simultaneousGesture(DragGesture(minimumDistance: 30))`). 이벤트를 소비하지 않고 한 손가락의 누적 이동만 본다.
 * 손가락이 둘 이상 닿은 제스처(확대)는 판정하지 않는다.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectDaySwipe(minimumDistance: Float, onSwipe: (Int) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var last: Offset = down.position
        var isMultiTouch = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) isMultiTouch = true
            event.changes.firstOrNull { it.id == down.id }?.let { last = it.position }
        } while (event.changes.any { it.pressed })
        val delta = last - down.position
        if (isMultiTouch || delta.getDistance() < minimumDistance) return@awaitEachGesture
        dayOffsetForSwipe(delta.x / density, delta.y / density)?.let(onSwipe)
    }
}
