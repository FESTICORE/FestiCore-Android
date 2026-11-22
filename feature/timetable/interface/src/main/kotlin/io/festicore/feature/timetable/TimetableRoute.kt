package io.festicore.feature.timetable

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 타임테이블 탭 흐름의 화면. iOS `TimetableCoordinator` 경로. 내 일정 고르기(`SchedulePick`)는 고르기 PR에서 더한다. */
@Serializable
sealed interface TimetableRoute : NavKey {
    /** S02 타임테이블(iOS `timetable`). 탭의 첫 화면이다. */
    @Serializable
    data object Timetable : TimetableRoute

    /** 공연 검색 · 시간표 변경 목록(iOS `setList`). 날짜는 열 때 타임테이블이 가진 값을 넘긴다. */
    @Serializable
    data class SetList(val mode: TimetableSetListMode) : TimetableRoute
}

/** iOS `TimetableSetListFeature.Mode`. */
@Serializable
enum class TimetableSetListMode {
    Search,
    Changes,
}
