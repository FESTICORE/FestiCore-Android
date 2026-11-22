package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalSchedule
import io.festicore.domain.festival.FestivalScheduleRepository
import io.festicore.domain.festival.FestivalScheduleUseCase

/** iOS `FestivalScheduleUseCaseImpl.mySchedule`. */
internal class FestivalScheduleUseCaseImpl(private val repository: FestivalScheduleRepository) : FestivalScheduleUseCase {
    override suspend fun mySchedule(festivalId: String): FestivalSchedule = repository.mySchedule(festivalId)
}
