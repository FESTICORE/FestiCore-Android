package io.festicore.domain.festival.sources

import io.festicore.domain.festival.CachedFestivalPack
import io.festicore.domain.festival.FestivalCatalogRepository
import io.festicore.domain.festival.FestivalPackRepository
import io.festicore.domain.festival.FestivalPackState
import io.festicore.domain.festival.FestivalPackUseCase
import io.festicore.domain.festival.PackDownloadEvent
import io.festicore.domain.festival.PackSyncResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** iOS `FestivalPackUseCase`(30일 지난 팩 자동 삭제는 아직 옮기지 않았다) + `FestivalPackDetailUseCase.updatePack`. */
internal class FestivalPackUseCaseImpl(
    private val repository: FestivalPackRepository,
    private val catalogRepository: FestivalCatalogRepository,
) : FestivalPackUseCase {
    override val state: StateFlow<FestivalPackState> get() = repository.state

    override suspend fun reloadCachedPacks(): List<CachedFestivalPack> = repository.reloadCachedPacks()

    override fun packUpdates(festivalId: String): Flow<PackSyncResult> = repository.packUpdates(festivalId)

    override fun downloadPack(festivalId: String): Flow<PackDownloadEvent> = repository.downloadPack(festivalId)

    override suspend fun syncPack(festivalId: String): PackSyncResult {
        val result = repository.syncPack(festivalId)
        if (result is PackSyncResult.Updated || result is PackSyncResult.NotModified) {
            // 받은 버전이 최신인지 다시 판단하도록 서버 목록도 새로 받는다. 실패해도 팩 맞추기는 성공이다(iOS `try?`).
            try {
                catalogRepository.refreshFestivals()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        return result
    }
}
