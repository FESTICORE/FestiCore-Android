package io.festicore.domain.festival.sources.repository

import io.festicore.domain.festival.entity.FestivalSchedule
import io.festicore.domain.festival.schedule.FestivalScheduleRepository
import io.festicore.domain.festival.sources.model.mapper.toFestivalScheduleError
import io.festicore.domain.festival.sources.service.FestivalScheduleService
import io.festicore.domain.festival.sources.widget.FestivalWidgetRepository
import io.festicore.domain.festival.sources.widget.widgetAttempt
import java.time.Instant

internal class FestivalScheduleRepositoryImpl(
    private val service: FestivalScheduleService,
    private val widget: FestivalWidgetRepository? = null,
) : FestivalScheduleRepository {
    override suspend fun mySchedule(festivalId: String): FestivalSchedule = try {
        val request = widgetAttempt { widget?.capture(festivalId) }.value
        FestivalSchedule(service.mySchedule(festivalId).setIds).also { result ->
            if (request != null) widgetAttempt { widget?.schedule(request, result.setIds) }
        }
    } catch (e: Exception) {
        throw e.toFestivalScheduleError()
    }

    override suspend fun replaceMySchedule(
        festivalId: String,
        setIds: List<String>,
        clientAt: Instant,
    ): FestivalSchedule = try {
        val request = widgetAttempt { widget?.capture(festivalId) }.value
        FestivalSchedule(service.replace(festivalId, setIds, clientAt).setIds).also { result ->
            if (request != null) widgetAttempt { widget?.schedule(request, result.setIds) }
        }
    } catch (e: Exception) {
        throw e.toFestivalScheduleError()
    }
}
