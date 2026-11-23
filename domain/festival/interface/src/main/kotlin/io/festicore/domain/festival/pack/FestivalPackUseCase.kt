package io.festicore.domain.festival.pack
import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.entity.FestivalPackState
import io.festicore.domain.festival.entity.PackDownloadEvent
import io.festicore.domain.festival.entity.PackSyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 목록 · 보드가 쓰는 팩 저장 · 동기화. 규칙은 `service/pack/.spec/pack-sync.md`. iOS `FestivalPackUseCaseInterface`. */
interface FestivalPackUseCase {
    /** 받은 팩 · 받는 중 진행률 · 받기 실패. */
    val state: StateFlow<FestivalPackState>

    suspend fun reloadCachedPacks(): List<CachedFestivalPack>

    fun packUpdates(festivalId: String): Flow<PackSyncResult>

    fun downloadPack(festivalId: String): Flow<PackDownloadEvent>

    /**
     * 팩을 한 번 맞춘 뒤 목록도 새로 받는다(목록 실패는 무시). iOS `FestivalPackDetailUseCase.updatePack`(설계 E5).
     * `Updated` · `NotModified`만 성공이고 `RetryAfter` · `Reconnecting`은 호출한 쪽이 실패로 본다. 실패는 [FestivalPackError].
     */
    suspend fun syncPack(festivalId: String): PackSyncResult
}
