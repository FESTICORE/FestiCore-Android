package io.festicore.feature.timetable

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 타임테이블 탭 흐름의 화면. iOS `TimetableCoordinator` 경로. */
@Serializable
sealed interface TimetableRoute : NavKey {
    /** S02 타임테이블(iOS `timetable`). 탭의 첫 화면이다. */
    @Serializable
    data object Timetable : TimetableRoute

    /** 공연 검색 · 시간표 변경 목록(iOS `setList`). 날짜는 열 때 타임테이블이 가진 값을 넘긴다. */
    @Serializable
    data class SetList(val mode: TimetableSetListMode) : TimetableRoute

    /**
     * 내 일정 고르기(iOS `destination.schedulePick`, 설계 T8). 날짜는 화면이 공유 팩에서 다시 계산한다.
     * [selectedSetIds]는 지금 내 일정(최대 60), [previousClientAtEpochMillis]는 마지막 저장 요청 시각이다(없으면 null).
     * 준비 안내 1단계도 같은 경로를 쓴다.
     */
    @Serializable
    data class SchedulePick(
        val festivalId: String,
        val selectedSetIds: List<String>,
        val previousClientAtEpochMillis: Long?,
    ) : TimetableRoute
}

/** iOS `TimetableSetListFeature.Mode`. */
@Serializable
enum class TimetableSetListMode {
    Search,
    Changes,
}
