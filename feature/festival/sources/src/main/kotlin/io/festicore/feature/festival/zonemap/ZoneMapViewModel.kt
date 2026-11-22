package io.festicore.feature.festival.zonemap

import androidx.lifecycle.ViewModel
import io.festicore.domain.auth.CurrentUserProvider
import io.festicore.domain.festival.FestivalLiveBoardError
import io.festicore.domain.festival.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.FestivalLiveBoardUseCase
import io.festicore.domain.festival.FestivalPackUseCase
import io.festicore.domain.festival.FestivalZoneMap
import io.festicore.domain.festival.FestivalZoneMapUseCase
import io.festicore.domain.festival.PackSyncResult
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S06 존 지도(iOS `FestivalZoneMapFeature`). 함수 이름은 iOS `Action.View`와 같고 순서는 view → async → inner를 따른다.
 * 지도 읽기 · 라이브 감시(35초) · 지도 버전 확인 · 팩 업데이트는 각각 하나만 돈다(iOS `cancelInFlight`).
 * 라이브 감시는 화면이 보이고 포그라운드일 때만 돈다(보드와 같은 수명 주기). [sleep]은 테스트에서 기다림을 조절하려고 주입한다.
 */
class ZoneMapViewModel(
    festivalId: String,
    selectedZoneId: String?,
    private val zoneMapUseCase: FestivalZoneMapUseCase,
    private val liveBoardUseCase: FestivalLiveBoardUseCase,
    private val packUseCase: FestivalPackUseCase,
    currentUserProvider: CurrentUserProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) : ViewModel(),
    OrbitContainerHost<ZoneMapState, ZoneMapState, ZoneMapSideEffect> {
    override val container =
        orbitContainer<ZoneMapState, ZoneMapSideEffect>(
            ZoneMapState(
                festivalId = festivalId,
                viewerUserId = currentUserProvider.currentUserId.value,
                selectedZoneId = selectedZoneId,
            ),
        )

    private val mapJob = AtomicReference<Job?>(null)
    private val liveJob = AtomicReference<Job?>(null)
    private val versionJob = AtomicReference<Job?>(null)
    private val updateJob = AtomicReference<Job?>(null)

    // MARK: - View

    /** iOS `onAppear`. 처음 한 번 보드의 마지막 라이브 상태를 먼저 그리고(설계 E3) 지도를 읽는다. 라이브 감시는 보일 때마다 다시 시작한다. */
    fun onAppear() = intent {
        reduce { state.copy(isVisible = true) }
        if (!state.hasStarted) {
            reduce { state.copy(hasStarted = true) }
            showCachedLiveBoard()
            loadMap()
        }
        watchLive()
    }

    /** 화면에서 빠졌다(iOS는 화면 해제 시 `cancellable` 취소). 라이브 감시를 멈춘다. */
    fun onDisappear() = intent {
        reduce { state.copy(isVisible = false) }
        liveJob.getAndSet(null)?.cancel()
    }

    /** 앱이 포그라운드를 얻거나 잃었다. 배경에서는 라이브 감시를 멈춘다. */
    fun onForegroundChanged(isForeground: Boolean) = intent {
        if (state.isForeground == isForeground) return@intent
        if (!isForeground) liveJob.getAndSet(null)?.cancel()
        reduce { state.copy(isForeground = isForeground) }
        if (isForeground) watchLive()
    }

    fun onBackTapped() = intent { postSideEffect(ZoneMapSideEffect.BackRequested) }

    /** 같은 존이면 카드를 닫고, 아니면 그 존을 고른다. */
    fun onZoneTapped(zoneId: String) = intent {
        reduce { state.copy(selectedZoneId = if (state.selectedZoneId == zoneId) null else zoneId) }
    }

    fun onMapBackgroundTapped() = intent { reduce { state.copy(selectedZoneId = null) } }

    /** "지도가 바뀌었어요" 배너. 진행 중이면 무시한다. */
    fun onUpdatePackTapped() = intent {
        if (state.isUpdatingPack) return@intent
        reduce { state.copy(isUpdatingPack = true) }
        updatePack()
    }

    fun onFollowGuideTapped() = intent { postSideEffect(ZoneMapSideEffect.FlagDirectoryRequested) }

    fun onRetryTapped() = intent {
        reduce { state.copy(map = ZoneMapLoad.Loading) }
        loadMap()
    }

    // MARK: - Async

    /** 보드를 거쳐 들어왔으면 그 마지막 라이브 상태가 있다. 없거나 읽지 못하면 감시 결과를 기다린다. */
    private suspend fun showCachedLiveBoard() = subIntent {
        val cached =
            try {
                liveBoardUseCase.cachedLiveBoard(state.festivalId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return@subIntent
        reduce { if (state.snapshot != null) state else state.copy(snapshot = cached, receivedAt = cached.receivedAt) }
    }

    private suspend fun loadMap() = subIntent {
        val festivalId = state.festivalId
        replace(mapJob) {
            val map =
                try {
                    zoneMapUseCase.cachedMap(festivalId, clock.instant())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    onMapFailed()
                    return@replace
                }
            onMapLoaded(map)
        }
    }

    /** 보드와 같은 주기로 깃발 · 크루 위치를 다시 받는다. 서버가 늦추라고 하면 그만큼 기다린다. */
    private suspend fun watchLive() = subIntent {
        if (!state.isVisible || !state.isForeground) return@subIntent
        val festivalId = state.festivalId
        replace(liveJob) {
            while (true) {
                val failure = fetchLive(festivalId)
                sleep(FestivalLiveBoardUseCase.nextPollDelay(failure))
            }
        }
    }

    /** 한 번 조회해 반영하고, 다음 조회 간격을 정할 실패(429)를 돌려준다. */
    private suspend fun fetchLive(festivalId: String): Throwable? = try {
        onLiveLoaded(liveBoardUseCase.liveBoard(festivalId))
        null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (rateLimited: FestivalLiveBoardError.RateLimited) {
        // 조회 제한은 장애가 아니다. 보이던 지도를 그대로 두고 허용 시각 뒤에 다시 불러온다.
        rateLimited
    } catch (_: Exception) {
        onLiveFailed()
        null
    }

    private suspend fun checkMapVersion(map: FestivalZoneMap) = subIntent {
        replace(versionJob) {
            val changed =
                try {
                    zoneMapUseCase.isMapChanged(map)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
            reduce { state.copy(isMapChanged = changed) }
        }
    }

    /** iOS `packDetailUseCase.updatePack`: `Updated` · `NotModified`만 성공이다(설계 E5). */
    private suspend fun updatePack() = subIntent {
        val festivalId = state.festivalId
        replace(updateJob) {
            val updated =
                try {
                    when (packUseCase.syncPack(festivalId)) {
                        is PackSyncResult.Updated, is PackSyncResult.NotModified -> true
                        else -> false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
            if (updated) onPackUpdated() else onPackUpdateFailed()
        }
    }

    // MARK: - Inner

    private suspend fun onMapLoaded(map: FestivalZoneMap?) = subIntent {
        if (map == null) {
            reduce { state.copy(map = ZoneMapLoad.Missing) }
            return@subIntent
        }
        reduce {
            val selected = state.selectedZoneId?.takeIf { map.zone(it) != null }
            state.copy(map = ZoneMapLoad.Loaded(map), selectedZoneId = selected)
        }
        checkMapVersion(map)
    }

    private suspend fun onMapFailed() = subIntent { reduce { state.copy(map = ZoneMapLoad.Failed) } }

    private suspend fun onLiveLoaded(snapshot: FestivalLiveBoardSnapshot) = subIntent {
        reduce { state.copy(snapshot = snapshot, receivedAt = snapshot.receivedAt ?: clock.instant(), isOffline = false) }
    }

    private suspend fun onLiveFailed() = subIntent { reduce { state.copy(isOffline = true) } }

    private suspend fun onPackUpdated() = subIntent {
        reduce { state.copy(isUpdatingPack = false, isMapChanged = false) }
        loadMap()
    }

    private suspend fun onPackUpdateFailed() = subIntent { reduce { state.copy(isUpdatingPack = false) } }

    private fun replace(slot: AtomicReference<Job?>, block: suspend () -> Unit) {
        val job = intent { block() }
        slot.getAndSet(job)?.cancel()
    }
}
