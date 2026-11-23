package io.festicore.domain.festival.sources.pack

import io.festicore.domain.festival.catalog.FestivalCatalogRepository
import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalPackState
import io.festicore.domain.festival.entity.PackDownloadEvent
import io.festicore.domain.festival.entity.PackSyncResult
import io.festicore.domain.festival.pack.FestivalPackRepository
import io.festicore.domain.festival.pack.FestivalPackUseCase
import io.festicore.domain.festival.sources.model.mapper.expiresAt
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** iOS `FestivalPackUseCase`(30일 지난 팩 자동 삭제 포함) + `FestivalPackDetailUseCase.updatePack`. */
internal class FestivalPackUseCaseImpl(
    private val repository: FestivalPackRepository,
    private val catalogRepository: FestivalCatalogRepository,
    private val now: () -> Instant = Instant::now,
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

    override suspend fun deleteExpiredPacks(): List<String> {
        val now = now()
        val deleted = mutableListOf<String>()
        for (pack in repository.reloadCachedPacks()) {
            currentCoroutineContext().ensureActive()
            val expiresAt = pack.expiresAt ?: continue
            if (now < expiresAt) continue
            try {
                repository.deletePack(pack.festivalId)
                deleted += pack.festivalId
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 지우지 못한 팩은 다음 포그라운드 때 다시 확인한다.
            }
        }
        return deleted
    }
}
