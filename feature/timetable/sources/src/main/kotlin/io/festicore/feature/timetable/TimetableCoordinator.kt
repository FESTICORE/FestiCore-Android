package io.festicore.feature.timetable

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * 타임테이블 탭 스택. [pendingSetId]는 검색 결과에서 고른 공연(목록을 닫은 뒤 타임테이블이 시트를 연다),
 * [pendingPickResult]는 닫힌 내 일정 고르기의 결과(타임테이블 ViewModel이 받는다, 설계 T8 대기 값)다.
 */
data class TimetableCoordinatorState(
    val routes: List<NavKey> = listOf(TimetableRoute.Timetable),
    val pendingSetId: String? = null,
    val pendingPickResult: SchedulePickResult? = null,
) {
    /** iOS `MTC:101-104`: 고르기 · 검색/변경 목록이 쌓였거나 공연 시트가 떠 있으면 탭 바를 숨긴다. */
    fun hidesTabBar(isSetDetailPresented: Boolean): Boolean = routes.size > 1 || isSetDetailPresented
}

/**
 * 타임테이블 탭 흐름(iOS `TimetableCoordinator`). 첫 화면은 타임테이블이고 검색 · 변경 목록과 내 일정 고르기를 위에 쌓는다.
 * 고르기가 열려 있으면 목록을 열지 않는다(iOS `guard destination == nil`). 시스템 뒤로로 고르기를 닫으면 결과 없음(`Skipped`)이다.
 */
class TimetableCoordinator :
    ViewModel(),
    OrbitContainerHost<TimetableCoordinatorState, TimetableCoordinatorState, Nothing> {
    override val container = orbitContainer<TimetableCoordinatorState, Nothing>(TimetableCoordinatorState())

    /** 상단 뒤로 · 시스템 뒤로. 고르기를 pop하면 [SchedulePickResult.Skipped]를 남긴다(iOS `updateRoutes` → `destination.dismiss`). */
    fun goBack() = intent {
        val top = state.routes.lastOrNull()
        if (state.routes.size <= 1) return@intent
        reduce {
            state.copy(
                routes = state.routes.dropLast(1),
                pendingPickResult = if (top is TimetableRoute.SchedulePick) SchedulePickResult.Skipped else state.pendingPickResult,
            )
        }
    }

    /** 타임테이블 `search`(iOS `delegate(.search)`). */
    fun onSearch() = openSetList(TimetableSetListMode.Search)

    /** 타임테이블 `showChanges`(iOS `delegate(.showChanges)`). */
    fun onShowChanges() = openSetList(TimetableSetListMode.Changes)

    private fun openSetList(mode: TimetableSetListMode) = intent {
        if (state.routes.any { it is TimetableRoute.SchedulePick || it is TimetableRoute.SetList }) return@intent
        reduce { state.copy(routes = state.routes + TimetableRoute.SetList(mode)) }
    }

    /** 목록에서 공연을 골랐다(iOS `setList(.delegate(.setSelected))`). 목록을 닫고 타임테이블이 그 공연 시트를 연다. */
    fun onSetListSetSelected(setId: String) = intent {
        reduce { state.copy(routes = state.routes.filterNot { it is TimetableRoute.SetList }, pendingSetId = setId) }
    }

    /** 목록 뒤로(iOS `setList(.delegate(.close))`). */
    fun onSetListClosed() = intent {
        reduce { state.copy(routes = state.routes.filterNot { it is TimetableRoute.SetList }) }
    }

    /** 타임테이블이 고르기를 열었다(iOS `destination = .schedulePick` → push). */
    fun onSchedulePick(route: TimetableRoute.SchedulePick) = intent {
        reduce { state.copy(routes = state.routes.filterNot { it is TimetableRoute.SchedulePick } + route) }
    }

    /** 타임테이블이 고르기를 닫았다(축제 · 계정 · 팩이 바뀜). 결과는 남기지 않는다. */
    fun onCloseSchedulePick() = intent {
        reduce { state.copy(routes = state.routes.filterNot { it is TimetableRoute.SchedulePick }) }
    }

    /** 고르기 화면이 끝났다(저장 · 건너뛰기 · 나중에). 닫고 결과를 타임테이블에 넘긴다. */
    fun onSchedulePickFinished(result: SchedulePickResult) = intent {
        if (state.routes.none { it is TimetableRoute.SchedulePick }) return@intent
        reduce { state.copy(routes = state.routes.filterNot { it is TimetableRoute.SchedulePick }, pendingPickResult = result) }
    }

    /** 마지막으로 받은 타임테이블 축제. 처음 받은 값은 기준으로만 둔다. */
    private var lastFestival: Festival? = null

    /**
     * 타임테이블이 보는 축제를 알린다(탭을 고를 때마다 다시 알린다). 이전에 받은 축제와 다르면 다른 축제의 검색 · 변경 목록을 닫는다
     * (iOS `syncTimetable`의 `sameFestival ? … : nil`). 고르기는 타임테이블 ViewModel이 닫는다.
     */
    fun onFestivalChanged(festivalId: String?) = intent {
        val previous = lastFestival
        lastFestival = Festival(festivalId)
        if (previous == null || previous.id == festivalId) return@intent
        reduce { state.copy(routes = state.routes.filterNot { it is TimetableRoute.SetList }) }
    }

    fun onPendingSetConsumed() = intent { reduce { state.copy(pendingSetId = null) } }

    fun onPendingPickResultConsumed() = intent { reduce { state.copy(pendingPickResult = null) } }

    private data class Festival(val id: String?)
}
