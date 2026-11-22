package io.festicore.feature.timetable.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.festicore.domain.timetable.TimetableDay
import io.festicore.feature.timetable.setdetail.TimetableSetDetailSheet
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.action.FCChip
import io.festicore.ui.designkit.component.card.FCCardAction
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.label.FCOfflineBadge
import io.festicore.ui.designkit.component.label.FCSourceLine
import io.festicore.ui.designkit.component.navigation.FCSegmented
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.overlay.FCBanner
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** `FCSegmented`가 잘리지 않고 담는 날짜 수(2~3칸용). 넘으면 가로로 넘기는 칩 줄이다(iOS `segmentedDayLimit`). */
private const val SEGMENTED_DAY_LIMIT = 3
private val SkeletonHeight = 120.dp
private const val SKELETON_COUNT = 3

/**
 * S02 타임테이블(iOS `TimetableView`). 화면이 보이는 동안(`STARTED`) 1초 시계를 돌린다(iOS `task` · `onDisappear`).
 * 상위 흐름이 처리할 일은 콜백으로 넘긴다(iOS `TimetableDelegate`). 공연 상세 시트도 이 화면이 띄운다(설계 T7).
 */
@Composable
fun TimetableScreen(
    onSearch: () -> Unit,
    onShowChanges: () -> Unit,
    onRequestNotification: () -> Unit,
    onSchedulePick: (festivalId: String, selectedSetIds: Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimetableViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            TimetableSideEffect.Search -> onSearch()
            TimetableSideEffect.ShowChanges -> onShowChanges()
            TimetableSideEffect.RequestNotification -> onRequestNotification()
            is TimetableSideEffect.SchedulePick -> onSchedulePick(sideEffect.festivalId, sideEffect.selectedSetIds)
        }
    }
    LifecycleStartEffect(viewModel) {
        viewModel.onClockStarted()
        onStopOrDispose { viewModel.onClockStopped() }
    }
    TimetableContent(
        state = state,
        modifier = modifier,
        onSearchClicked = viewModel::onSearchClicked,
        onDaySelected = viewModel::onDaySelected,
        onRetryClicked = viewModel::onRetryClicked,
        onNotifyClicked = viewModel::onNotifyClicked,
        onMineOnlyToggled = viewModel::onMineOnlyToggled,
        onSchedulePickClicked = viewModel::onSchedulePickClicked,
        onChangeBannerClicked = viewModel::onChangeBannerClicked,
        onSetClicked = viewModel::onSetClicked,
        onZoomChanged = viewModel::onZoomChanged,
        onScheduleToggled = viewModel::onScheduleToggled,
        onSetDetailDismissed = viewModel::onSetDetailDismissed,
    )
}

/** 타임테이블 순수 UI. 프리뷰는 이 함수를 쓴다. */
@Composable
fun TimetableContent(
    state: TimetableState,
    onSearchClicked: () -> Unit,
    onDaySelected: (Int) -> Unit,
    onRetryClicked: () -> Unit,
    onNotifyClicked: () -> Unit,
    onMineOnlyToggled: () -> Unit,
    onSchedulePickClicked: () -> Unit,
    onChangeBannerClicked: () -> Unit,
    onSetClicked: (String) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onScheduleToggled: () -> Unit,
    onSetDetailDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        FCTopBar(
            style = FCTopBarStyle.Large,
            title = "타임테이블",
            trailingIcon = R.drawable.ic_magnifying_glass,
            trailingAccessibilityLabel = "검색",
            onTrailingAction = onSearchClicked,
        )
        if (state.days.isNotEmpty()) DaySelector(state.days, state.selectedDayIndex, onDaySelected)
        state.offlineMessage?.let { FCOfflineBadge(it) }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.contentStatus) {
                TimetableContentStatus.Loading -> Skeleton()

                TimetableContentStatus.Failed ->
                    FCEmptyCard(
                        title = "시간표를 불러오지 못했어요",
                        message = "연결 상태를 확인하고 다시 시도해 주세요.",
                        modifier = Modifier.padding(FestiCoreSpacing.paddingP16),
                        icon = R.drawable.ic_bell,
                        action = FCCardAction("다시 시도", FCButtonStyle.Primary, onRetryClicked),
                    )

                TimetableContentStatus.NoFestival ->
                    FCEmptyCard(
                        title = "축제를 선택해 주세요",
                        message = "참여할 축제를 선택하면 시간표를 볼 수 있어요.",
                        modifier = Modifier.padding(FestiCoreSpacing.paddingP16),
                        icon = R.drawable.ic_magnifying_glass,
                    )

                TimetableContentStatus.Unpublished -> TimetableUnpublished(state.lineup, state.notificationStatus, onNotifyClicked)

                TimetableContentStatus.Published ->
                    Published(
                        state,
                        onMineOnlyToggled,
                        onSchedulePickClicked,
                        onChangeBannerClicked,
                        onSetClicked,
                        onZoomChanged,
                        onDaySelected,
                    )
            }
        }
        // Figma S02: 출처 줄은 내용 길이와 상관없이 탭 바 바로 위에 붙는다.
        if (state.source.isNotEmpty()) FCSourceLine(state.source)
    }
    TimetableSetDetailSheet(
        state = state.setDetail,
        now = state.clockAt,
        onScheduleToggled = onScheduleToggled,
        onDismissRequest = onSetDetailDismissed,
    )
}

@Composable
private fun Published(
    state: TimetableState,
    onMineOnlyToggled: () -> Unit,
    onSchedulePickClicked: () -> Unit,
    onChangeBannerClicked: () -> Unit,
    onSetClicked: (String) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onDaySelected: (Int) -> Unit,
) {
    val day = state.selectedDay
    if (day == null) {
        FCEmptyCard(
            title = "등록된 공연이 없어요",
            message = "공연 정보가 등록되면 시간표에서 확인할 수 있어요.",
            modifier = Modifier.padding(FestiCoreSpacing.paddingP16),
            icon = R.drawable.ic_bell,
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        FilterRow(state, onMineOnlyToggled, onSchedulePickClicked)
        if (day.changedCount > 0) {
            FCBanner(
                text = "시간표가 바뀌었어요 · ${day.changedCount}개 변경",
                modifier = Modifier.padding(
                    start = FestiCoreSpacing.paddingP16,
                    end = FestiCoreSpacing.paddingP16,
                    bottom = FestiCoreSpacing.paddingP16,
                ),
                onClick = onChangeBannerClicked,
            )
        }
        TimetableBoard(
            day = day,
            state = state,
            onSetClicked = onSetClicked,
            onZoomEnd = onZoomChanged,
            onDaySwipe = { onDaySelected(state.selectedDayIndex + it) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FilterRow(
    state: TimetableState,
    onMineOnlyToggled: () -> Unit,
    onSchedulePickClicked: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(FestiCoreSpacing.paddingP16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCChip(label = "내 일정만", isSelected = state.showsMineOnly, onClick = onMineOnlyToggled)
        Spacer(modifier = Modifier.weight(1f))
        val overlaps = state.visibleOverlaps
        val isEnabled = state.canPickSchedule
        val labelColor = FestiCoreSemanticColor.labelAssistive
        BasicText(
            text = if (overlaps.isEmpty()) "내 일정 ${state.mySetCount}개" else "겹치는 일정 ${overlaps.size}개",
            modifier =
                Modifier
                    .clickable(enabled = isEnabled, onClick = onSchedulePickClicked)
                    .clearAndSetSemantics {
                        contentDescription = "내 일정 고르기"
                        role = Role.Button
                        if (isEnabled) {
                            onClick {
                                onSchedulePickClicked()
                                true
                            }
                        } else {
                            disabled()
                        }
                    },
            style =
                FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(
                    color = if (overlaps.isEmpty()) labelColor.color else FestiCoreSemanticColor.accentPointOrange.color,
                ),
        )
    }
}

/** 3일 이하는 세그먼트, 4일 이상은 가로 칩 줄. 칩 줄은 처음과 바뀔 때 고른 칩을 가운데로 옮긴다(iOS `daySelector`). */
@Composable
private fun DaySelector(
    days: List<TimetableDay>,
    selectedIndex: Int,
    onDaySelected: (Int) -> Unit,
) {
    if (days.size <= SEGMENTED_DAY_LIMIT) {
        FCSegmented(
            selection = selectedIndex,
            onSelectionChange = onDaySelected,
            items = days.map { it.title },
            modifier = Modifier.padding(horizontal = FestiCoreSpacing.paddingP16),
        )
        return
    }
    val listState = rememberLazyListState()
    var hasScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex) {
        centerItem(listState, selectedIndex, animated = hasScrolled)
        hasScrolled = true
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        itemsIndexed(days, key = { _, day -> day.id }) { index, day ->
            FCChip(label = day.title, isSelected = index == selectedIndex, onClick = { onDaySelected(index) })
        }
    }
}

/** 항목을 화면 가운데로 옮긴다(iOS `scrollTo(index, anchor: .center)`). 크기를 모르면 먼저 그 항목으로 옮겨 잰다. */
private suspend fun centerItem(
    listState: LazyListState,
    index: Int,
    animated: Boolean,
) {
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) listState.scrollToItem(index)
    val size = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: return
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    val offset = -(viewport - size) / 2
    if (animated) listState.animateScrollToItem(index, offset) else listState.scrollToItem(index, offset)
}

@Composable
private fun Skeleton() {
    Column(
        modifier = Modifier.padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        repeat(SKELETON_COUNT) {
            FCSkeletonView(shape = FCSkeletonShape.Round(FestiCoreRadius.r16), modifier = Modifier.fillMaxWidth().height(SkeletonHeight))
        }
    }
}
