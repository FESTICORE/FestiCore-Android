package io.festicore.domain.festival.widget

import io.festicore.domain.festival.entity.FestivalWidgetSnapshot
import kotlinx.coroutines.flow.Flow

data class FestivalWidgetRead(
    val snapshot: FestivalWidgetSnapshot?,
    val revision: Long,
    val unavailable: Boolean = false,
)
interface FestivalWidgetUseCase {
    suspend fun read(): FestivalWidgetRead
    val changes: Flow<Unit>
    suspend fun invalidatePersonal() = Unit
    suspend fun observeSessions()
}
