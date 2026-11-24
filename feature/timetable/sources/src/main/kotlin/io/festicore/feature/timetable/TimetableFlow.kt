package io.festicore.feature.timetable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.festicore.feature.timetable.main.TimetableScreen
import io.festicore.feature.timetable.main.TimetableViewModel
import io.festicore.feature.timetable.schedulepick.SchedulePickScreen
import io.festicore.feature.timetable.setlist.TimetableSetListScreen
import io.festicore.feature.timetable.setlist.TimetableSetListViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState

/**
 * 타임테이블 탭 루트(iOS `TimetableCoordinatorView`). 타임테이블 위에 검색 · 변경 목록과 내 일정 고르기를 쌓는다.
 * [coordinator]와 [timetableViewModel]은 메인 탭 범위에 두어 다른 탭에 다녀와도 스택 · 날짜 · 내 일정을 유지한다(설계 "탭 바 규칙").
 * 탭을 고를 때마다 이 흐름이 다시 그려지므로 그때 `onTimetableAppeared`를 부른다(iOS `MTC:337-341` `appearing: true`).
 * 탭 바 숨김(쌓인 화면 · 공연 시트)은 [onHidesTabBarChanged]로 알린다(iOS `MTC:101-104`).
 * 스택 항목([entries])은 탭을 바꿔도 남는 범위에서 [rememberTimetableFlowEntries]로 만들어 받는다.
 * 공개 전 "알림 받기"는 팩 알림 설정(A1, 푸시 P0 뒤)이 들어올 때 잇는다. 그 전에는 [TimetablePushGate]로 버튼을 숨긴다.
 */
@Composable
fun TimetableFlow(
    entries: List<NavEntry<NavKey>>,
    onHidesTabBarChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    coordinator: TimetableCoordinator = koinViewModel(),
    timetableViewModel: TimetableViewModel = koinViewModel(),
) {
    val state by coordinator.collectAsState()
    val timetableState by timetableViewModel.collectAsState()

    LaunchedEffect(Unit) { timetableViewModel.onTimetableAppeared() }
    val hidesTabBar = state.hidesTabBar(isSetDetailPresented = timetableState.setDetail != null)
    LaunchedEffect(hidesTabBar) { onHidesTabBarChanged(hidesTabBar) }
    val pendingSet = state.pendingSet
    LaunchedEffect(pendingSet) {
        if (pendingSet == null) return@LaunchedEffect
        // 목록을 연 축제가 지금 축제와 다르면 버린다.
        state.pendingSetId(timetableState.festivalId)?.let(timetableViewModel::onSetClicked)
        coordinator.onPendingSetConsumed()
    }

    NavDisplay(
        entries = entries,
        modifier = modifier,
        onBack = coordinator::goBack,
        transitionSpec = { TimetableTransitions.push() },
        popTransitionSpec = { TimetableTransitions.pop() },
        predictivePopTransitionSpec = { TimetableTransitions.pop() },
    )
}

/**
 * 타임테이블 스택 항목. 탭을 바꿔도 남는 범위(메인 탭 항목)에서 부른다. 항목 범위(검색 · 변경 목록 ViewModel · 저장 상태)는 탭을 바꿔도 남고
 * 스택에서 빠질 때만 정리된다(Navigation 3 `rememberDecoratedNavEntries` 여러 백스택 방식).
 */
@Composable
fun rememberTimetableFlowEntries(
    coordinator: TimetableCoordinator = koinViewModel(),
    timetableViewModel: TimetableViewModel = koinViewModel(),
): List<NavEntry<NavKey>> {
    val state by coordinator.collectAsState()
    val timetableState by timetableViewModel.collectAsState()
    // 타임테이블 탭이 안 보여도 축제가 바뀌면 옛 축제 검색 · 변경 목록을 바로 닫는다(iOS `TabView`의 숨은 탭도 동기화를 받는다).
    LaunchedEffect(timetableState.festivalId) { coordinator.onFestivalChanged(timetableState.festivalId) }
    return rememberDecoratedNavEntries(
        backStack = state.routes,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<TimetableRoute.Timetable> {
                    TimetableScreen(
                        onSearch = { coordinator.onSearch(timetableState.festivalId) },
                        onShowChanges = { coordinator.onShowChanges(timetableState.festivalId) },
                        onRequestNotification = {},
                        onSchedulePick = coordinator::onSchedulePick,
                        onCloseSchedulePick = coordinator::onCloseSchedulePick,
                        schedulePickResult = state.pendingPickResult,
                        onSchedulePickResultConsumed = coordinator::onPendingPickResultConsumed,
                        viewModel = timetableViewModel,
                    )
                }
                entry<TimetableRoute.SetList> { key ->
                    // 열 때 타임테이블이 가진 날짜를 넘기고, 같은 축제 팩이 바뀌면 날짜만 바꾼다(iOS `syncTimetable`).
                    val listViewModel: TimetableSetListViewModel = koinViewModel { parametersOf(key.mode, timetableState.days) }
                    // 목록을 연 축제와 지금 축제가 같을 때만 날짜를 바꾼다. 축제가 바뀌면 목록은 곧 닫히고, 그 사이 새 축제 날짜를 옛 목록에 보이지 않는다.
                    val isSameFestival = key.festivalId == timetableState.festivalId
                    LaunchedEffect(listViewModel, timetableState.days, isSameFestival) {
                        if (isSameFestival) listViewModel.onDaysChanged(timetableState.days)
                    }
                    TimetableSetListScreen(
                        viewModel = listViewModel,
                        onSetSelected = coordinator::onSetListSetSelected,
                        onClose = coordinator::onSetListClosed,
                    )
                }
                entry<TimetableRoute.SchedulePick> { key ->
                    SchedulePickScreen(route = key, onResult = coordinator::onSchedulePickFinished)
                }
            },
    )
}
