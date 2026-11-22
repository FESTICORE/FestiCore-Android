package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalSchedule
import io.festicore.domain.festival.FestivalScheduleRepository
import io.festicore.domain.festival.FestivalScheduleSaved
import io.festicore.domain.festival.FestivalScheduleUseCase
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** iOS `FestivalScheduleUseCaseImpl`. 저장 알림은 [events](앱 범위 하나)로 보낸다. */
internal class FestivalScheduleUseCaseImpl(
    private val repository: FestivalScheduleRepository,
    private val events: FestivalScheduleSavedEvents,
) : FestivalScheduleUseCase {
    override suspend fun mySchedule(festivalId: String): FestivalSchedule = repository.mySchedule(festivalId)

    override suspend fun saveMySchedule(
        festivalId: String,
        setIds: List<String>,
        clientAt: Instant,
    ): FestivalSchedule = repository.replaceMySchedule(festivalId, setIds, clientAt)

    override val savedSchedules: Flow<FestivalScheduleSaved> get() = events.flow

    override suspend fun notifyMyScheduleSaved(saved: FestivalScheduleSaved) = events.emit(saved)
}

/** 저장 알림 통로(Koin `single`). 구독자가 없으면 버린다. */
internal class FestivalScheduleSavedEvents {
    private val events = MutableSharedFlow<FestivalScheduleSaved>(extraBufferCapacity = EVENT_BUFFER)

    val flow: Flow<FestivalScheduleSaved> = events.asSharedFlow()

    suspend fun emit(saved: FestivalScheduleSaved) = events.emit(saved)

    private companion object {
        const val EVENT_BUFFER = 16
    }
}
