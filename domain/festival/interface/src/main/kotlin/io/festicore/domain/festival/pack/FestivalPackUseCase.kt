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

    /**
     * 마지막 날 종료 후 [FestivalPackRetention.DAYS]일이 지난 팩만 지우고 지운 축제 ID를 돌려준다. 종료 시각을 모르면 지우지 않는다.
     * 한 팩을 지우지 못해도 나머지는 계속 확인한다. 내 일정 · 준비물은 남는다. iOS `deleteExpiredPacks`.
     */
    suspend fun deleteExpiredPacks(): List<String>
}

/** 받은 팩 보관 기간. iOS `FestivalPackRetention`. */
object FestivalPackRetention {
    const val DAYS = 30
}
