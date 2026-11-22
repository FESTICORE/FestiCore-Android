package io.festicore.domain.festival

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 목록 · 보드가 쓰는 팩 저장 · 동기화. 규칙은 `service/pack/.spec/pack-sync.md`. iOS `FestivalPackUseCaseInterface`. */
interface FestivalPackUseCase {
    /** 받은 팩 · 받는 중 진행률 · 받기 실패. */
    val state: StateFlow<FestivalPackState>

    suspend fun reloadCachedPacks(): List<CachedFestivalPack>

    fun packUpdates(festivalId: String): Flow<PackSyncResult>

    fun downloadPack(festivalId: String): Flow<PackDownloadEvent>
}
