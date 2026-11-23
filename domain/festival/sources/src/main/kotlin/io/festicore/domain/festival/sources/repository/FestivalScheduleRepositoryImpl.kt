package io.festicore.domain.festival.sources.repository

import io.festicore.domain.festival.entity.FestivalSchedule
import io.festicore.domain.festival.schedule.FestivalScheduleRepository
import io.festicore.domain.festival.sources.model.mapper.toFestivalScheduleError
import io.festicore.domain.festival.sources.service.FestivalScheduleService
import java.time.Instant

internal class FestivalScheduleRepositoryImpl(private val service: FestivalScheduleService) : FestivalScheduleRepository {
    override suspend fun mySchedule(festivalId: String): FestivalSchedule = try {
        FestivalSchedule(service.mySchedule(festivalId).setIds)
    } catch (e: Exception) {
        throw e.toFestivalScheduleError()
    }

    override suspend fun replaceMySchedule(
        festivalId: String,
        setIds: List<String>,
        clientAt: Instant,
    ): FestivalSchedule = try {
        FestivalSchedule(service.replace(festivalId, setIds, clientAt).setIds)
    } catch (e: Exception) {
        throw e.toFestivalScheduleError()
    }
}
