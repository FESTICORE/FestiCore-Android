package io.festicore.domain.festival

/** 내 일정 읽기. 실패는 [FestivalScheduleError]. iOS `FestivalScheduleUseCaseInterface.mySchedule`(저장은 Timetable 문서). */
interface FestivalScheduleUseCase {
    suspend fun mySchedule(festivalId: String): FestivalSchedule
}
