package io.festicore.domain.festival.sources.service

import io.festicore.domain.festival.sources.model.dto.FestivalScheduleDto
import java.time.Instant

/** 로그인 사용자의 내 일정 읽기 · 저장. iOS `FestivalScheduleService`. */
interface FestivalScheduleService {
    suspend fun mySchedule(festivalId: String): FestivalScheduleDto

    /** 내 일정 전체를 [setIds]로 바꾼다. 저장 충돌은 [FestivalScheduleServiceError.Stale]. */
    suspend fun replace(
        festivalId: String,
        setIds: List<String>,
        clientAt: Instant,
    ): FestivalScheduleDto
}
