package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalSchedule
import io.festicore.domain.festival.FestivalScheduleRepository
import io.festicore.service.festivalcatalog.FestivalScheduleService
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
