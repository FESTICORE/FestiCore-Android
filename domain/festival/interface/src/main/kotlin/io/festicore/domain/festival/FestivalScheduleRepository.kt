package io.festicore.domain.festival

import java.time.Instant

/** 내 일정 저장소. 실패는 [FestivalScheduleError]. iOS `FestivalScheduleService`. */
interface FestivalScheduleRepository {
    suspend fun mySchedule(festivalId: String): FestivalSchedule

    /** 내 일정 전체를 바꾼다. 충돌이면 [FestivalScheduleError.Stale]. */
    suspend fun replaceMySchedule(
        festivalId: String,
        setIds: List<String>,
        clientAt: Instant,
    ): FestivalSchedule
}
