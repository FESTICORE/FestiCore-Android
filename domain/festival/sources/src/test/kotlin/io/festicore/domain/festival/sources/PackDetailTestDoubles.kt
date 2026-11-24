package io.festicore.domain.festival.sources

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.storage.shared.FestivalPackLocalStore
import io.festicore.domain.festival.catalog.FestivalCatalogRepository
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalCatalogPage
import io.festicore.domain.festival.sources.repository.FestivalPackRepositoryImpl
import io.festicore.service.pack.CachedFestivalPack as ServicePack
import io.festicore.service.pack.PackDownloadEvent
import io.festicore.service.pack.PackSyncResult
import io.festicore.service.pack.PackSyncService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 받은 팩 · 팩 상세 테스트가 함께 쓰는 가짜들. iOS 테스트의 `MutablePackService` · `*CatalogService`. */
internal class MutablePackService(packs: List<ServicePack>) : PackSyncService {
    var packs: List<ServicePack> = packs
    val synced = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var onSync: ((String) -> List<ServicePack>)? = null
    var syncResult: PackSyncResult? = null
    var syncError: Throwable? = null

    /** 목록에는 있지만 개별 조회 전에 지워진 팩. */
    var deletedBeforeSync: Set<String> = emptySet()

    /** 지우기에 실패하는 팩. */
    var failingDeletes: Set<String> = emptySet()

    override suspend fun cachedPack(festivalId: String): ServicePack? =
        if (festivalId in deletedBeforeSync) null else packs.firstOrNull { it.festivalId == festivalId }

    override suspend fun cachedPacks(): List<ServicePack> = packs

    override suspend fun sync(festivalId: String): PackSyncResult {
        synced += festivalId
        syncError?.let { throw it }
        onSync?.let { packs = it(festivalId) }
        syncResult?.let { return it }
        val pack = packs.firstOrNull { it.festivalId == festivalId } ?: return PackSyncResult.Reconnecting
        return PackSyncResult.Updated(pack)
    }

    override fun download(festivalId: String): Flow<PackDownloadEvent> = emptyFlow()

    override suspend fun deletePack(festivalId: String) {
        if (festivalId in failingDeletes) throw java.io.IOException("지우기 실패")
        deleted += festivalId
        packs = packs.filterNot { it.festivalId == festivalId }
    }

    override fun updates(festivalId: String): Flow<PackSyncResult> = emptyFlow()

    fun repository() = FestivalPackRepositoryImpl(this, FestiCoreJson)
}

/** [cached]가 null이면 저장 목록 읽기가 실패한다. */
internal class FakeCatalogRepository(
    var cached: List<FestivalCatalogItem>? = emptyList(),
    var refreshed: List<FestivalCatalogItem> = emptyList(),
) : FestivalCatalogRepository {
    override suspend fun cachedFestivals(): List<FestivalCatalogItem> = cached ?: throw IllegalStateException("깨진 목록")

    override suspend fun refreshFestivals(): List<FestivalCatalogItem> = refreshed

    override suspend fun festivalPage(after: String?, limit: Int): FestivalCatalogPage = error("사용하지 않음")

    override fun imageUrl(value: String?): String? = value?.let { "https://img.test$it" }
}

internal class FakePackLocalStore(var autoDownload: Boolean = true) : FestivalPackLocalStore {
    val checklists = mutableMapOf<String, String>()

    override suspend fun checklistJson(festivalId: String): String? = checklists[festivalId]

    override suspend fun saveChecklistJson(festivalId: String, json: String) {
        checklists[festivalId] = json
    }

    override suspend fun autoDownloadOnWiFi(): Boolean = autoDownload

    override suspend fun setAutoDownloadOnWiFi(isOn: Boolean) {
        autoDownload = isOn
    }
}

internal fun catalogItem(
    id: String,
    name: String,
    packVersion: Int,
) = FestivalCatalogItem(id = id, name = name, packVersion = packVersion)

/** `{"id":…,"packVersion":…,"name":…}` 팩. */
internal fun namedPack(
    id: String,
    version: Int,
    name: String?,
    checkedAtMillis: Long? = null,
): ServicePack {
    val fields = buildMap {
        put("id", JsonPrimitive(id))
        put("packVersion", JsonPrimitive(version))
        name?.let { put("name", JsonPrimitive(it)) }
    }
    return ServicePack(id, version, "e", JsonObject(fields).toString().encodeToByteArray(), checkedAtMillis)
}
