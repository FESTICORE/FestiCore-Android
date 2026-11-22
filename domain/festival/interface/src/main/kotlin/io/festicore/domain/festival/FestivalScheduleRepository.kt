package io.festicore.domain.festival

/** 내 일정 저장소. 실패는 [FestivalScheduleError]. iOS `FestivalScheduleService`(읽기). */
interface FestivalScheduleRepository {
    suspend fun mySchedule(festivalId: String): FestivalSchedule
}
