package io.festicore.feature.festival.flagdetail

import androidx.lifecycle.ViewModel
import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.festival.entity.FestivalFlagDetail
import io.festicore.domain.festival.entity.FestivalFlagDetailError
import io.festicore.domain.festival.entity.FestivalFlagFollowError
import io.festicore.domain.festival.entity.FestivalFlagFollowResult
import io.festicore.domain.festival.entity.FestivalFlagReportError
import io.festicore.domain.festival.entity.FestivalFlagReportReason
import io.festicore.domain.festival.flagdetail.FestivalFlagDetailUseCase
import io.festicore.domain.festival.flagdetail.FestivalFlagUseCase
import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.FlagFollowData
import io.festicore.service.analytics.usecase.AnalyticsUseCase
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S05 깃발 상세(iOS `FestivalFlagDetailFeature`). 함수 이름은 iOS `Action.View`와 같고, 순서는 view → async → inner를 따른다.
 * 조회 · 시계 · 팔로우 · 신고 · 토스트 만료는 각각 하나만 돈다(iOS `cancelInFlight`). 잠금화면 대표 깃발(pin)은 라이브 액티비티 단계로 뺐다.
 * [clock] · [sleep]은 테스트에서 시각과 기다림을 조절하려고 주입한다.
 */
class FlagDetailViewModel(
    flagId: String,
    context: FlagDetailContext,
    private val detailUseCase: FestivalFlagDetailUseCase,
    private val flagUseCase: FestivalFlagUseCase,
    private val analyticsUseCase: AnalyticsUseCase,
    private val bearerUseCase: BearerUseCase,
    private val clock: Clock = Clock.systemUTC(),
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) : ViewModel(),
    OrbitContainerHost<FlagDetailState, FlagDetailState, FlagDetailSideEffect> {
    override val container =
        orbitContainer<FlagDetailState, FlagDetailSideEffect>(FlagDetailState(flagId = flagId, context = context, now = clock.instant()))

    private val loadJob = AtomicReference<Job?>(null)
    private val clockJob = AtomicReference<Job?>(null)
    private val followJob = AtomicReference<Job?>(null)
    private val reportJob = AtomicReference<Job?>(null)
    private val toastJob = AtomicReference<Job?>(null)

    // MARK: - View

    /** 받은 상세가 있으면 그대로 두고 새로 불러온다. */
    fun onAppeared() = intent {
        reduce {
            state.copy(
                loadGeneration = state.loadGeneration + 1,
                now = clock.instant(),
                content = if (state.content is FlagDetailContent.Failed) FlagDetailContent.Loading else state.content,
            )
        }
        load(state.loadGeneration)
        startClock()
    }

    /** 닫은 화면에 늦은 조회 · 팔로우 · 신고 결과나 로그인 요청이 오지 않게 모두 멈춘다. */
    fun onDisappeared() = intent {
        listOf(loadJob, clockJob, followJob, reportJob).forEach { it.getAndSet(null)?.cancel() }
        reduce { state.copy(loadGeneration = state.loadGeneration + 1, isFollowPending = false, isReportPending = false) }
    }

    fun onRetryTapped() = intent {
        reduce {
            state.copy(
                loadGeneration = state.loadGeneration + 1,
                content = if (state.content is FlagDetailContent.Failed) FlagDetailContent.Loading else state.content,
            )
        }
        load(state.loadGeneration)
    }

    fun onFollowTapped() = intent {
        if (state.detail == null || state.isFollowPending || state.isOffline) return@intent
        if (!state.context.hasSession) {
            postSideEffect(FlagDetailSideEffect.LoginRequested)
            return@intent
        }
        reduce { state.copy(isFollowPending = true) }
        follow(!state.isFollowing)
    }

    /** 상세를 받은 뒤에만 공유한다. */
    fun onShareTapped() = intent {
        val detail = state.detail ?: return@intent
        postSideEffect(FlagDetailSideEffect.ShareRequested(url = state.shareUrl, title = detail.name))
    }

    fun onReportTapped() = intent {
        if (state.detail == null || state.isReportPending) return@intent
        if (!state.context.hasSession) {
            postSideEffect(FlagDetailSideEffect.ReportLoginRequested)
            return@intent
        }
        reduce { state.copy(reportSheet = FlagReportSheetState()) }
    }

    /** 부모가 축제 팩 · 내 일정 · 계정이 바뀌었을 때 보내는 새 표시 입력. 계정이 바뀔 때만 다시 불러온다. */
    fun onContextChanged(context: FlagDetailContext) = intent {
        val isAccountChanged = context.hasSession != state.context.hasSession || context.accountId != state.context.accountId
        if (!isAccountChanged) {
            reduce { state.copy(context = context) }
            return@intent
        }
        // 다른 계정의 팔로우 값이 남지 않게 비우고 새 계정으로 다시 불러온다.
        followJob.getAndSet(null)?.cancel()
        reportJob.getAndSet(null)?.cancel()
        reduce {
            state.copy(
                context = context,
                followOverride = null,
                isFollowPending = false,
                isReportPending = false,
                reportSheet = null,
                canTransferFestival = false,
                isMenuPresented = false,
                content = FlagDetailContent.Loading,
                isOffline = false,
                loadGeneration = state.loadGeneration + 1,
            )
        }
        load(state.loadGeneration)
    }

    /** 깃발 관리 메뉴. 옮길 수 있는 내 깃발이 아니면 열지 않는다. */
    fun onMenuTapped() = intent {
        if (!state.canTransferFestival) return@intent
        reduce { state.copy(isMenuPresented = true) }
    }

    fun onMenuDismissed() = intent {
        reduce { state.copy(isMenuPresented = false) }
    }

    fun onMenuShareTapped() = intent {
        if (!state.isMenuPresented) return@intent
        reduce { state.copy(isMenuPresented = false) }
        onShareTapped()
    }

    fun onMenuTransferTapped() = intent {
        if (!state.isMenuPresented) return@intent
        reduce { state.copy(isMenuPresented = false) }
        postSideEffect(FlagDetailSideEffect.FestivalTransferRequested(flagId = state.flagId))
    }

    /** 이동 화면에서 옮기기를 마쳤다. 계획 · 존 · 현장 상태가 바뀌었으니 다시 불러온다. */
    fun onFestivalTransferred() = intent {
        reduce { state.copy(loadGeneration = state.loadGeneration + 1) }
        load(state.loadGeneration)
        showToast(FlagDetailToast.FestivalTransferred)
    }

    // MARK: - Scope (신고 시트, iOS `FestivalFlagReportFeature`)

    fun onReportReasonSelected(reason: FestivalFlagReportReason) = intent {
        val sheet = state.reportSheet ?: return@intent
        reduce { state.copy(reportSheet = sheet.copy(selectedReason = reason)) }
    }

    /** 사유를 고르기 전에는 버튼이 꺼져 있지만 접근성 동작으로 들어와도 보내지 않는다. */
    fun onReportSubmitTapped() = intent {
        val reason = state.reportSheet?.selectedReason ?: return@intent
        reduce { state.copy(reportSheet = null, isReportPending = true) }
        report(reason)
    }

    fun onReportSheetDismissed() = intent {
        reduce { state.copy(reportSheet = null) }
    }

    // MARK: - Async

    private suspend fun load(generation: Int) = subIntent {
        val flagId = state.flagId
        val job =
            intent {
                val failure =
                    try {
                        val detail = detailUseCase.detail(flagId)
                        onLoaded(generation, detail, clock.instant())
                        if (detail.isBearer == true && isTransferable(flagId)) onTransferable(generation)
                        return@intent
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: FestivalFlagDetailError) {
                        error
                    } catch (_: Exception) {
                        FestivalFlagDetailError.Unavailable()
                    }
                onLoadFailed(generation, failure)
            }
        loadJob.getAndSet(job)?.cancel()
    }

    private suspend fun follow(following: Boolean) = subIntent {
        val flagId = state.flagId
        val job =
            intent {
                val result =
                    try {
                        Result.success(flagUseCase.setFollowing(following, flagId))
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: FestivalFlagFollowError) {
                        Result.failure(error)
                    } catch (_: Exception) {
                        Result.failure(FestivalFlagFollowError.Unavailable())
                    }
                onFollowFinished(result)
            }
        followJob.getAndSet(job)?.cancel()
    }

    private suspend fun report(reason: FestivalFlagReportReason) = subIntent {
        val flagId = state.flagId
        val job =
            intent {
                val failure =
                    try {
                        detailUseCase.report(flagId, reason)
                        null
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: FestivalFlagReportError) {
                        error
                    } catch (_: Exception) {
                        FestivalFlagReportError.Unavailable()
                    }
                if (failure == null) onReported() else onReportFailed(failure)
            }
        reportJob.getAndSet(job)?.cancel()
    }

    /** 원래 기수이고 공동 기수가 없으면 옮길 수 있다(서버도 403 `FLAG_NOT_OWNER` · 409 `FLAG_HAS_CO_BEARERS`로 거절). 읽지 못하면 메뉴를 두지 않는다. */
    private suspend fun isTransferable(flagId: String): Boolean = try {
        val owned = bearerUseCase.fetchBearerFlags(festivalId = null).firstOrNull { it.id == flagId }
        // 기수 보드의 coBearers에는 원래 기수도 들어 있다.
        owned?.isOwner == true && bearerUseCase.fetchBoard(flagId = flagId).coBearers.size <= 1
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }

    /** 1분마다 지금 시각을 바꿔 경과 시간 문구 · 계획 행 상태를 다시 그린다(iOS `clock.timer(60)`). */
    private suspend fun startClock() = subIntent {
        val job =
            intent {
                while (true) {
                    sleep(CLOCK_INTERVAL)
                    reduce { state.copy(now = clock.instant()) }
                }
            }
        clockJob.getAndSet(job)?.cancel()
    }

    private suspend fun showToast(toast: FlagDetailToast) = subIntent {
        reduce { state.copy(toast = toast) }
        val job =
            intent {
                sleep(TOAST_DURATION)
                reduce { state.copy(toast = null) }
            }
        toastJob.getAndSet(job)?.cancel()
    }

    // MARK: - Inner (테스트에서 늦은 응답을 보내려고 internal)

    internal fun onLoaded(
        generation: Int,
        detail: FestivalFlagDetail,
        at: Instant,
    ) = intent {
        reduce {
            if (generation != state.loadGeneration) return@reduce state
            state.copy(content = FlagDetailContent.Loaded(detail), lastLoadedAt = at, now = at, isOffline = false, followOverride = null)
        }
    }

    private fun onTransferable(generation: Int) = intent {
        reduce { if (generation != state.loadGeneration) state else state.copy(canTransferFestival = true) }
    }

    internal fun onLoadFailed(generation: Int, error: FestivalFlagDetailError) = intent {
        reduce {
            when {
                generation != state.loadGeneration -> state

                error is FestivalFlagDetailError.NotFound ->
                    state.copy(content = FlagDetailContent.Failed(FlagDetailFailure.NotFound), isOffline = false)

                // 받은 상세는 남기고 오프라인으로 알린다.
                state.detail != null -> state.copy(isOffline = true)

                else -> state.copy(content = FlagDetailContent.Failed(FlagDetailFailure.Unavailable))
            }
        }
    }

    /** 화면을 떠나며 취소한 요청의 늦은 응답은 버린다. */
    internal fun onFollowFinished(result: Result<FestivalFlagFollowResult>) = intent {
        if (!state.isFollowPending) return@intent
        val response = result.getOrNull()
        if (response == null) {
            reduce { state.copy(isFollowPending = false) }
            when (result.exceptionOrNull()) {
                is FestivalFlagFollowError.LoginRequired -> postSideEffect(FlagDetailSideEffect.LoginRequested)
                is FestivalFlagFollowError.TermsRequired -> postSideEffect(FlagDetailSideEffect.TermsAcceptanceRequested)
                is FestivalFlagFollowError.LimitReached -> showToast(FlagDetailToast.FollowLimit)
                else -> showToast(FlagDetailToast.FollowFailed)
            }
            return@intent
        }
        reduce {
            state.copy(
                isFollowPending = false,
                followOverride = FlagDetailFollowOverride(isFollowing = response.isFollowing, followerCount = response.followerCount),
                loadGeneration = state.loadGeneration + 1,
            )
        }
        analyticsUseCase.track(AnalyticsEvent.FlagFollow(FlagFollowData(flagId = response.flagId, isFollowing = response.isFollowing)))
        postSideEffect(
            FlagDetailSideEffect.FollowChanged(
                flagId = response.flagId,
                isFollowing = response.isFollowing,
                followerCount = response.followerCount,
            ),
        )
        // 서버 값을 다시 받는다.
        load(state.loadGeneration)
        val name = state.detail?.name
        if (response.isFollowing && name != null) showToast(FlagDetailToast.Followed(name))
    }

    private suspend fun onReported() = subIntent {
        if (!state.isReportPending) return@subIntent
        reduce { state.copy(isReportPending = false) }
        showToast(FlagDetailToast.Reported)
    }

    internal fun onReportFailed(error: FestivalFlagReportError) = intent {
        if (!state.isReportPending) return@intent
        reduce { state.copy(isReportPending = false) }
        when (error) {
            is FestivalFlagReportError.LoginRequired -> postSideEffect(FlagDetailSideEffect.ReportLoginRequested)
            is FestivalFlagReportError.TermsRequired -> postSideEffect(FlagDetailSideEffect.TermsAcceptanceRequested)
            is FestivalFlagReportError.AlreadyReported -> showToast(FlagDetailToast.AlreadyReported)
            else -> showToast(FlagDetailToast.ReportFailed)
        }
    }

    private companion object {
        val CLOCK_INTERVAL = 60.seconds
        val TOAST_DURATION = 2.seconds
    }
}
