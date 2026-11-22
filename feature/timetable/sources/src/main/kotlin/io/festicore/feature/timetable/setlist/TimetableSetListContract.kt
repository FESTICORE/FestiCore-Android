package io.festicore.feature.timetable.setlist

import io.festicore.domain.timetable.TimetableDay
import io.festicore.feature.timetable.TimetableSetListMode

/** 공연 검색 · 시간표 변경 목록 상태. iOS `TimetableSetListFeature.State`. */
data class TimetableSetListState(
    val mode: TimetableSetListMode,
    val days: List<TimetableDay>,
    val query: String = "",
) {
    /** 변경 목록은 시작 시각이 바뀐 공연만, 검색은 아티스트 · 무대 이름에 검색어가 든 공연만 남긴다. 공연이 없는 날은 뺀다. */
    val results: List<TimetableDay>
        get() {
            val keyword = query.trim()
            return days.mapNotNull { day ->
                val sets =
                    day.sets.filter { set ->
                        if (mode == TimetableSetListMode.Changes) return@filter set.previousStart != null
                        val stage = day.stages.firstOrNull { it.id == set.stageId }?.name.orEmpty()
                        keyword.isEmpty() || set.artist.contains(keyword, ignoreCase = true) || stage.contains(keyword, ignoreCase = true)
                    }
                if (sets.isEmpty()) null else day.copy(sets = sets)
            }
        }
}

/** iOS `TimetableSetListFeature.Delegate`. */
sealed interface TimetableSetListSideEffect {
    data class SetSelected(val setId: String) : TimetableSetListSideEffect

    data object Close : TimetableSetListSideEffect
}
