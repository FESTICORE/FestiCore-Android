package io.festicore.feature.timetable

/** 내 일정 고르기 결과. iOS `SchedulePickDelegate`. */
sealed interface SchedulePickResult {
    /** 고른 공연을 서버 내 일정에 저장했다. 다음 단계로 넘어간다. */
    data class Saved(
        val festivalId: String,
        val setIds: Set<String>,
    ) : SchedulePickResult

    /** 이 단계를 저장하지 않고 넘긴다. */
    data object Skipped : SchedulePickResult

    /** 준비 안내 전체를 나중에 한다. */
    data object Later : SchedulePickResult
}
