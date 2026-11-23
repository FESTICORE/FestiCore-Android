package io.festicore.domain.festival.pack
import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.entity.FestivalPackState
import io.festicore.domain.festival.entity.PackDownloadEvent
import io.festicore.domain.festival.entity.PackSyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 축제 팩 저장 · WebSocket 동기화. 흐름의 실패는 [FestivalPackError]. iOS `FestivalPackSyncRepositoryInterface`.
 * 받거나 확인한 팩은 [state]에 반영해 목록과 보드가 같은 값을 본다(설계 D1).
 */
interface FestivalPackRepository {
    val state: StateFlow<FestivalPackState>

    /** 기기에 저장한 팩을 다시 읽어 [state]의 팩 목록을 바꾼다. 네트워크에 연결하지 않는다. 손상 파일은 뺀다. */
    suspend fun reloadCachedPacks(): List<CachedFestivalPack>

    /** 축제 팩 변경을 계속 따라간다. 끊기면 `Reconnecting`을 알리고 다시 연결한다. 수집을 멈추면 연결을 닫는다. */
    fun packUpdates(festivalId: String): Flow<PackSyncResult>

    /** 단발 팩 받기. 진행과 저장 완료를 보내고, 받는 중 · 실패를 [state]에 남긴다. 수집을 멈추면 취소된다. */
    fun downloadPack(festivalId: String): Flow<PackDownloadEvent>

    /** 기기에 저장한 한 축제 팩. 없으면 null. 네트워크에 연결하지 않는다. */
    suspend fun cachedPack(festivalId: String): CachedFestivalPack?

    /** 연결 하나로 한 번 맞추고 닫는다. 받거나 확인한 팩은 [state]에 반영한다. */
    suspend fun syncPack(festivalId: String): PackSyncResult

    /** 기기에 저장한 팩을 지운다. 없으면 아무것도 하지 않는다. */
    suspend fun deletePack(festivalId: String)
}
