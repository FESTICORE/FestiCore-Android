package io.festicore.domain.festival.sources.receivedpack

import io.festicore.core.storage.shared.FestivalPackLocalStore
import io.festicore.domain.festival.catalog.FestivalCatalogRepository
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.festival.pack.FestivalPackRepository
import io.festicore.domain.festival.receivedpack.FestivalReceivedPackUseCase
import io.festicore.domain.festival.receivedpack.FestivalWiFiStatus
import io.festicore.domain.festival.sources.model.mapper.toReceivedPacks
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 받은 팩과 축제 목록을 합쳐 새 버전 상태를 계산한다. 팩 읽기 · 받기는 공유 팩 상태에 반영된다. iOS `FestivalReceivedPackUseCaseImpl`. */
internal class FestivalReceivedPackUseCaseImpl(
    private val packRepository: FestivalPackRepository,
    private val catalogRepository: FestivalCatalogRepository,
    private val localStore: FestivalPackLocalStore,
    private val wiFiStatus: FestivalWiFiStatus,
) : FestivalReceivedPackUseCase {
    override suspend fun receivedPacks(): List<FestivalReceivedPack> {
        val packs = packRepository.reloadCachedPacks()
        // 저장된 축제 목록이 없거나 깨져도 받은 팩은 보여 준다.
        val catalog = try {
            catalogRepository.cachedFestivals()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        return packs.toReceivedPacks(catalog, catalogRepository::imageUrl)
    }

    override suspend fun refreshReceivedPacks(): List<FestivalReceivedPack> {
        val packs = packRepository.reloadCachedPacks()
        val catalog = catalogRepository.refreshFestivals()
        val received = packs.toReceivedPacks(catalog, catalogRepository::imageUrl)
        val outdated = received.filter { it.hasUpdate }
        // 와이파이에서 자동으로 받기가 켜져 있으면 새 버전을 바로 받는다. 실패한 팩은 표시하고 다음 확인 때 다시 받는다.
        if (outdated.isEmpty() || !localStore.autoDownloadOnWiFi() || !wiFiStatus.isOnWiFi()) return received
        for (pack in outdated) {
            currentCoroutineContext().ensureActive()
            // 목록을 읽은 뒤 사용자가 지운 팩은 다시 받지 않는다.
            if (cachedPackOrNull(pack.id) == null) continue
            try {
                packRepository.syncPack(pack.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 받지 못한 팩은 아래에서 새 버전이 남아 있는지로 실패를 표시한다.
            }
        }
        currentCoroutineContext().ensureActive()
        val attempted = outdated.mapTo(mutableSetOf()) { it.id }
        return packRepository.reloadCachedPacks().toReceivedPacks(catalog, catalogRepository::imageUrl).map { pack ->
            pack.copy(autoUpdateFailed = pack.id in attempted && pack.hasUpdate)
        }
    }

    private suspend fun cachedPackOrNull(festivalId: String) = try {
        packRepository.cachedPack(festivalId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
