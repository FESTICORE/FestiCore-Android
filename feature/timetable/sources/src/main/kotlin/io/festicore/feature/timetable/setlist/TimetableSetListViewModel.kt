package io.festicore.feature.timetable.setlist

import androidx.lifecycle.ViewModel
import io.festicore.domain.timetable.TimetableDay
import io.festicore.feature.timetable.TimetableSetListMode
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** 공연 검색 · 시간표 변경 목록(iOS `TimetableSetListFeature`). 날짜는 열 때 타임테이블이 가진 값을 받는다(iOS `TimetableCoordinator`). */
class TimetableSetListViewModel(
    mode: TimetableSetListMode,
    days: List<TimetableDay>,
) : ViewModel(),
    OrbitContainerHost<TimetableSetListState, TimetableSetListState, TimetableSetListSideEffect> {
    override val container: OrbitContainer<TimetableSetListState, TimetableSetListState, TimetableSetListSideEffect> =
        orbitContainer(TimetableSetListState(mode = mode, days = days))

    fun onQueryChanged(query: String) = intent {
        reduce { state.copy(query = query) }
    }

    /** 결과에 있는 공연만 고른다(iOS `setTapped`). */
    fun onSetClicked(id: String) = intent {
        if (state.results.none { day -> day.sets.any { it.id == id } }) return@intent
        postSideEffect(TimetableSetListSideEffect.SetSelected(id))
    }

    fun onBackClicked() = intent {
        postSideEffect(TimetableSetListSideEffect.Close)
    }
}
