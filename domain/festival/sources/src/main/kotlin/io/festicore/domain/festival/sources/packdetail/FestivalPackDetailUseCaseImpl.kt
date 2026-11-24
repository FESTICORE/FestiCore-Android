package io.festicore.domain.festival.sources.packdetail

import io.festicore.core.storage.shared.FestivalPackLocalStore
import io.festicore.domain.festival.catalog.FestivalCatalogRepository
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.entity.PackSyncResult
import io.festicore.domain.festival.pack.FestivalPackRepository
import io.festicore.domain.festival.packdetail.FestivalPackChangesRepository
import io.festicore.domain.festival.packdetail.FestivalPackDetailUseCase
import io.festicore.domain.festival.sources.model.dto.FestivalChecklistDto
import io.festicore.domain.festival.sources.model.mapper.toDomain
import io.festicore.domain.festival.sources.model.mapper.toDto
import io.festicore.domain.festival.sources.model.mapper.toReceivedPack
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json

/** 로컬 팩과 저장된 축제 목록을 합쳐 상세를 만든다. 준비물 · 자동 받기 설정은 팩을 지워도 남는다. iOS `FestivalPackDetailUseCaseImpl`. */
internal class FestivalPackDetailUseCaseImpl(
    private val packRepository: FestivalPackRepository,
    private val catalogRepository: FestivalCatalogRepository,
    private val changesRepository: FestivalPackChangesRepository,
    private val localStore: FestivalPackLocalStore,
    private val json: Json,
) : FestivalPackDetailUseCase {
    override suspend fun packDetail(festivalId: String): FestivalPackDetail? = detail(festivalId, cachedCatalog())

    override suspend fun packChanges(festivalId: String, fromVersion: Int): FestivalPackChanges =
        changesRepository.packChanges(festivalId, fromVersion)

    override suspend fun updatePack(festivalId: String): FestivalPackDetail? {
        // 전송 실패는 저장소가 이미 Offline으로 바꾼다.
        when (packRepository.syncPack(festivalId)) {
            is PackSyncResult.Updated, is PackSyncResult.NotModified -> Unit
            is PackSyncResult.RetryAfter, PackSyncResult.Reconnecting -> throw FestivalPackError.RetryLater()
        }
        // 받은 버전이 최신인지 다시 판단하도록 서버 목록도 새로 받는다. 실패하면 저장된 목록을 쓴다.
        val catalog = try {
            catalogRepository.refreshFestivals()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            cachedCatalog()
        }
        return detail(festivalId, catalog)
    }

    override suspend fun deletePack(festivalId: String) = packRepository.deletePack(festivalId)

    override suspend fun checklist(festivalId: String): FestivalChecklist {
        val saved = localStore.checklistJson(festivalId) ?: return FestivalChecklist()
        return try {
            json.decodeFromString(FestivalChecklistDto.serializer(), saved).toDomain()
        } catch (_: IllegalArgumentException) { // SerializationException 포함
            FestivalChecklist()
        }
    }

    override suspend fun saveChecklist(checklist: FestivalChecklist, festivalId: String) {
        localStore.saveChecklistJson(festivalId, json.encodeToString(FestivalChecklistDto.serializer(), checklist.toDto()))
    }

    override suspend fun autoDownloadOnWiFi(): Boolean = localStore.autoDownloadOnWiFi()

    override suspend fun setAutoDownloadOnWiFi(isOn: Boolean) = localStore.setAutoDownloadOnWiFi(isOn)

    private suspend fun detail(festivalId: String, catalog: List<FestivalCatalogItem>): FestivalPackDetail? =
        packRepository.packDetail(festivalId) { cached ->
            cached.toReceivedPack(catalog.firstOrNull { it.id == cached.festivalId }, catalogRepository::imageUrl)
        }

    private suspend fun cachedCatalog(): List<FestivalCatalogItem> = try {
        catalogRepository.cachedFestivals()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
}
