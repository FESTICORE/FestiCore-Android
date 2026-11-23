package io.festicore.feature.festival.flag

import androidx.lifecycle.ViewModel
import io.festicore.domain.festival.FestivalFlagFollowError
import io.festicore.domain.festival.FestivalFlagFollowResult
import io.festicore.domain.festival.FestivalFlagUseCase
import io.festicore.domain.festival.FestivalLiveBoardError
import io.festicore.domain.festival.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.FestivalLiveBoardUseCase
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S04 깃발 목록. iOS `FestivalFlagFeature` + 자식 `FestivalFlagDirectory`를 하나로 합치고, 정렬 · 필터 · reconcile은 [FlagDirectory] 순수 함수에 둔다.
 * 함수 이름은 iOS `Action.View`와 같고, 순서는 view → async → inner를 따른다.
 * 조회는 하나만 돈다(iOS `cancelInFlight`). 팔로우 요청은 깃발마다 하나, 토스트 만료는 하나만 돈다.
 * 팔로우는 낙관 갱신이 아니다. 응답이 오면 override로 반영한다. [sleep]은 테스트에서 기다림을 조절하려고 주입한다.
 */
class FlagViewModel(
    private val liveBoardUseCase: FestivalLiveBoardUseCase,
    private val flagUseCase: FestivalFlagUseCase,
    festivalId: String? = null,
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) : ViewModel(),
    OrbitContainerHost<FlagState, FlagState, FlagSideEffect> {
    override val container = orbitContainer<FlagState, FlagSideEffect>(FlagState(festivalId = festivalId))

    private val loadJob = AtomicReference<Job?>(null)
    private val toastJob = AtomicReference<Job?>(null)
    private val followJobs = ConcurrentHashMap<String, Job>()

    // MARK: - View

    fun onAppeared() = intent {
        reduce { state.copy(isVisible = true) }
        load()
    }

    fun onDisappeared() = intent {
        loadJob.getAndSet(null)?.cancel()
        cancelFollowsAndToast()
        reduce { state.copy(isVisible = false, directory = FlagDirectory.leaveTab(state.directory)) }
    }

    /** 당겨서 새로고침. */
    fun refresh() = intent { load() }

    /** 부모가 선택 축제가 바뀌었음을 알린다. 같은 값이어도 iOS처럼 진행 중인 팔로우 · 토스트는 비운다. */
    fun onFestivalChanged(festivalId: String?) = intent {
        cancelFollowsAndToast()
        if (state.festivalId == festivalId) {
            reduce { state.copy(directory = FlagDirectory.resetContext(state.directory)) }
            return@intent
        }
        reduce {
            state.copy(
                festivalId = festivalId,
                snapshot = null,
                viewState = FlagViewState.Idle,
                generation = state.generation + 1,
                directory = FlagDirectory.resetContext(state.directory),
            )
        }
        if (state.isVisible) load() else loadJob.getAndSet(null)?.cancel()
    }

    /** 부모가 로그인 계정이 바뀌었음을 알린다. 이전 계정의 깃발 목록 · 팔로우 값을 비운다. */
    fun onAccountChanged() = intent {
        cancelFollowsAndToast()
        reduce {
            state.copy(
                snapshot = null,
                viewState = FlagViewState.Idle,
                generation = state.generation + 1,
                directory = FlagDirectory.resetContext(state.directory),
            )
        }
        if (state.isVisible) load() else loadJob.getAndSet(null)?.cancel()
    }

    /** 부모가 선택 축제 팩의 구역 이름을 알린다. 카드의 구역 칩에 쓴다. */
    fun onZoneNamesChanged(zoneNames: Map<String, String>) = intent {
        reduce { state.copy(directory = state.directory.copy(zoneNames = zoneNames)) }
    }

    fun onTagSelected(tag: FlagTag?) = intent {
        reduce { state.copy(directory = state.directory.copy(tag = tag, isSortMenuPresented = false)) }
    }

    fun onSortTapped() = intent {
        reduce { state.copy(directory = state.directory.copy(isSortMenuPresented = !state.directory.isSortMenuPresented)) }
    }

    fun onSortSelected(sort: FlagSort) = intent {
        reduce { state.copy(directory = state.directory.copy(sort = sort, isSortMenuPresented = false)) }
    }

    fun onSortMenuDismissed() = intent {
        reduce { state.copy(directory = state.directory.copy(isSortMenuPresented = false)) }
    }

    /** 요청 중이면 무시한다. 빠르게 다시 누르면 스냅샷보다 직전 성공 응답의 팔로워 수가 기준이다. */
    fun onFollowTapped(
        id: String,
        follow: Boolean,
        baseCount: Int?,
    ) = intent {
        if (state.directory.isPending(id)) return@intent
        val base = state.directory.followOverrides[id]?.followerCount ?: baseCount
        reduce { state.copy(directory = state.directory.copy(pendingFollows = state.directory.pendingFollows + (id to base))) }
        setFollowing(id, follow)
    }

    /** 카드를 눌러 깃발 상세를 연다. */
    fun onCardTapped(id: String) = intent { postSideEffect(FlagSideEffect.DetailRequested(id)) }

    /** 깃발 상세에서 바꾼 팔로우 결과. 디렉토리 팔로우 성공과 같은 규칙으로 카드에 반영한다. */
    fun onFollowChangedElsewhere(result: FestivalFlagFollowResult, baseCount: Int?) = intent {
        val override = FlagFollowOverride(isFollowing = result.isFollowing, followerCount = result.followerCount, baseCount = baseCount)
        reduce {
            state.copy(directory = state.directory.copy(followOverrides = state.directory.followOverrides + (result.flagId to override)))
        }
    }

    // MARK: - Async

    private suspend fun load() = subIntent {
        val festivalId = state.festivalId
        if (festivalId == null) {
            loadJob.getAndSet(null)?.cancel()
            reduce { state.copy(viewState = FlagViewState.Idle) }
            return@subIntent
        }
        // 새 조회가 이전 조회를 취소하면 건너뛸 이유가 사라진다.
        reduce { state.copy(viewState = FlagViewState.Loading, directory = FlagDirectory.clearLoadSkips(state.directory)) }
        val generation = state.generation
        val job = intent { fetch(festivalId, generation) }
        loadJob.getAndSet(job)?.cancel()
    }

    private suspend fun fetch(festivalId: String, generation: Int) {
        val failure =
            try {
                onLoaded(festivalId, generation, liveBoardUseCase.liveBoard(festivalId))
                return
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (rateLimited: FestivalLiveBoardError.RateLimited) {
                // 라이브 보드와 같이 이전 목록을 유지한 채 기다렸다가 다시 불러온다.
                sleep(rateLimited.retryAfter)
                load()
                return
            } catch (_: FestivalLiveBoardError.InvalidFestivalId) {
                FlagLoadFailure.InvalidFestivalId
            } catch (_: FestivalLiveBoardError.InvalidResponse) {
                FlagLoadFailure.InvalidResponse
            } catch (_: Exception) {
                FlagLoadFailure.Unavailable
            }
        onFailed(festivalId, generation, failure)
    }

    private suspend fun setFollowing(id: String, follow: Boolean) = subIntent {
        val job =
            intent {
                val result =
                    try {
                        Result.success(flagUseCase.setFollowing(follow, id))
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: FestivalFlagFollowError) {
                        Result.failure(error)
                    } catch (_: Exception) {
                        Result.failure(FestivalFlagFollowError.Unavailable())
                    }
                onFollowResponse(id, result)
            }
        followJobs.put(id, job)?.cancel()
    }

    private suspend fun expireToast() = subIntent {
        val job =
            intent {
                sleep(TOAST_DURATION)
                reduce { state.copy(directory = state.directory.copy(toast = null)) }
            }
        toastJob.getAndSet(job)?.cancel()
    }

    // MARK: - Inner (테스트에서 늦은 응답을 보내려고 internal)

    /** 거부된 응답은 상태를 바꾸지 않는다. 진행 중이던 조회가 받아들여지면 팔로우 값을 서버 값에 넘긴다. */
    internal fun onLoaded(
        festivalId: String,
        generation: Int,
        snapshot: FestivalLiveBoardSnapshot,
    ) = intent {
        reduce {
            if (!state.accepts(festivalId, generation)) return@reduce state
            val wasLoading = state.viewState == FlagViewState.Loading
            val directory = if (wasLoading) FlagDirectory.reconcile(state.directory, snapshot) else state.directory
            state.copy(snapshot = snapshot, viewState = FlagViewState.Loaded, directory = directory)
        }
    }

    internal fun onFailed(
        festivalId: String,
        generation: Int,
        failure: FlagLoadFailure,
    ) = intent {
        reduce {
            if (!state.accepts(festivalId, generation)) return@reduce state
            val viewState =
                when (failure) {
                    FlagLoadFailure.DeviceNotRegistered -> FlagViewState.DeviceNotRegistered
                    FlagLoadFailure.InvalidFestivalId -> FlagViewState.InvalidFestivalId
                    FlagLoadFailure.InvalidResponse -> FlagViewState.InvalidResponse
                    FlagLoadFailure.Unavailable -> FlagViewState.Failed
                }
            state.copy(viewState = viewState)
        }
    }

    /** 탭 이탈 · 축제 · 계정 변경으로 비운 요청의 늦은 응답은 버린다. */
    private suspend fun onFollowResponse(id: String, result: Result<FestivalFlagFollowResult>) = subIntent {
        if (!state.directory.isPending(id)) return@subIntent
        val baseCount = state.directory.pendingFollows[id]
        val pending = state.directory.pendingFollows - id
        val response = result.getOrNull()
        if (response != null) {
            // 성공 시점에 진행 중이던 조회는 팔로우 전 상태일 수 있어 한 번 건너뛴다.
            val override =
                FlagFollowOverride(
                    isFollowing = response.isFollowing,
                    followerCount = response.followerCount,
                    baseCount = baseCount,
                    skipsNextLoad = state.viewState == FlagViewState.Loading,
                )
            reduce {
                state.copy(
                    directory = state.directory.copy(
                        pendingFollows = pending,
                        followOverrides =
                            state.directory.followOverrides + (id to override),
                    ),
                )
            }
            return@subIntent
        }
        val error = result.exceptionOrNull()
        val toast =
            when (error) {
                is FestivalFlagFollowError.LoginRequired, is FestivalFlagFollowError.TermsRequired -> null
                is FestivalFlagFollowError.LimitReached -> LIMIT_TOAST
                else -> FAILURE_TOAST
            }
        reduce { state.copy(directory = state.directory.copy(pendingFollows = pending, toast = toast ?: state.directory.toast)) }
        when {
            error is FestivalFlagFollowError.LoginRequired -> postSideEffect(FlagSideEffect.LoginRequested)
            error is FestivalFlagFollowError.TermsRequired -> postSideEffect(FlagSideEffect.TermsAcceptanceRequested)
            else -> expireToast()
        }
    }

    private fun cancelFollowsAndToast() {
        followJobs.keys.toList().forEach { followJobs.remove(it)?.cancel() }
        toastJob.getAndSet(null)?.cancel()
    }

    private fun FlagState.accepts(festivalId: String, generation: Int) =
        this.festivalId == festivalId && this.generation == generation && isVisible

    private companion object {
        val TOAST_DURATION = 2.seconds
        const val LIMIT_TOAST = "팔로우는 100개까지 할 수 있어요"
        const val FAILURE_TOAST = "팔로우를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요"
    }
}
