package io.festicore.domain.festival.sources

import io.festicore.domain.festival.CachedFestivalPack
import io.festicore.domain.festival.FestivalPackRepository
import io.festicore.domain.festival.FestivalPackState
import io.festicore.domain.festival.FestivalPackUseCase
import io.festicore.domain.festival.PackDownloadEvent
import io.festicore.domain.festival.PackSyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** iOS `FestivalPackUseCase`(30일 지난 팩 자동 삭제는 아직 옮기지 않았다). */
internal class FestivalPackUseCaseImpl(private val repository: FestivalPackRepository) : FestivalPackUseCase {
    override val state: StateFlow<FestivalPackState> get() = repository.state

    override suspend fun reloadCachedPacks(): List<CachedFestivalPack> = repository.reloadCachedPacks()

    override fun packUpdates(festivalId: String): Flow<PackSyncResult> = repository.packUpdates(festivalId)

    override fun downloadPack(festivalId: String): Flow<PackDownloadEvent> = repository.downloadPack(festivalId)
}
