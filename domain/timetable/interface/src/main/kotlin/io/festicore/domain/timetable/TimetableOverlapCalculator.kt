package io.festicore.domain.timetable

/** 내 일정 겹침 계산. iOS `TimetableFeature.State.overlaps` · `overlaps(of:in:)` · `SchedulePickFeature.State.overlap(of:)`. */
interface TimetableOverlapCalculator {
    /** [day]의 공연이 낀 내 일정끼리의 겹침 구간([day] 0시부터의 분). 자정을 넘겨 다음 날 공연과 겹치는 구간도 포함한다. */
    fun overlaps(
        days: List<TimetableDay>,
        mySetIds: Set<String>,
        day: TimetableDay,
    ): List<TimetableOverlap>

    /** [day]의 [set]과 시간이 겹치는 다른 내 일정 공연. 시작 시각순이다. */
    fun setOverlaps(
        days: List<TimetableDay>,
        mySetIds: Set<String>,
        set: TimetableSet,
        day: TimetableDay,
    ): List<TimetableSetOverlap>

    /** 내 일정 고르기: [set]이 골라져 있고 같은 날 먼저(또는 같은 시각에) 시작하는 고른 공연과 겹치면 가장 많이 겹친 공연. */
    fun pickOverlap(
        day: TimetableDay,
        selectedSetIds: Set<String>,
        set: TimetableSet,
    ): TimetablePickOverlap?
}

/** 내 일정 고르기 행의 겹침 안내. */
data class TimetablePickOverlap(
    val artist: String,
    val minutes: Int,
)
