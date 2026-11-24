package io.festicore.feature.bearer.board

import androidx.lifecycle.ViewModel
import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.entity.BearerActivity
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerOutboxEvent
import io.festicore.domain.bearer.entity.BearerStatusChange
import io.festicore.domain.bearer.entity.BearerStatusRequest
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * iOS `BearerBoardFeature`(Figma S09 A~J 기수 보드). 누르면 바로 화면에 반영하고, 보내지 못한 변경은 앱 범위 대기열이 순서대로 다시 보낸다.
 * 읽기 · 대기열 구독 · 확인 오버레이는 각각 하나만 돈다(iOS `cancelInFlight`).
 *
 * 최신 상태 다시 읽기(설계 B-3): 보드 진입 · "다시 시도" · `Rejected`(iOS와 같음) + 포그라운드 복귀([onForeground]) · 오프라인 중 첫 `Sent`(Android 추가).
 * 다시 읽어도 지금 보드를 유지하고, 결과 위에 남은 대기 건을 다시 얹는다. 주기 폴링 · 웹소켓은 두지 않는다.
 *
 * @param viewerUserId 로그인한 사용자. 없으면 상태를 바꾸지 않는다.
 */
class BearerBoardViewModel(
    festivalId: String,
    flagId: String,
    viewerUserId: String?,
    private val bearerUseCase: BearerUseCase,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel(),
    OrbitContainerHost<BearerBoardState, BearerBoardState, BearerBoardSideEffect> {
    override val container =
        orbitContainer<BearerBoardState, BearerBoardSideEffect>(
            BearerBoardState(festivalId = festivalId, flagId = flagId, viewerUserId = viewerUserId),
        )

    private val loadJob = AtomicReference<Job?>(null)
    private val outboxJob = AtomicReference<Job?>(null)
    private val checkJob = AtomicReference<Job?>(null)

    // MARK: - View

    /** 대기열을 먼저 구독하고 보내지 못한 변경을 받은 뒤, 보드가 없으면 상태를 읽는다. */
    fun onAppear() = intent { observeOutbox() }

    /** 구독 · 로딩 · 확인 표시만 끝낸다. 대기열 전송은 계속된다. */
    fun onDisappear() = intent {
        outboxJob.getAndSet(null)?.cancel()
        loadJob.getAndSet(null)?.cancel()
        checkJob.getAndSet(null)?.cancel()
        reduce { state.copy(isLoading = false, check = null) }
    }

    /** 보드가 보이는 동안 앱이 포그라운드로 돌아왔다(`LifecycleEventEffect(ON_START)`). Android 추가(설계 B-3). 첫 읽기 전에는 [onAppear]가 읽는다. */
    fun onForeground() = intent {
        if (state.board != null) load()
    }

    fun onRetryTapped() = intent { load() }

    /** 상단 닫기 · 시스템 뒤로. 못 보낸 변경이 있으면 한 번 알린다(닫아도 대기열은 이어 보낸다). */
    fun onCloseTapped() = intent {
        if (state.pending.isEmpty()) {
            postSideEffect(BearerBoardSideEffect.CloseRequested)
        } else {
            reduce { state.copy(destination = BearerBoardDestination.CloseConfirm(count = state.pending.size)) }
        }
    }

    fun onRetrySendTapped() = intent {
        reduce { state.copy(isSendPaused = false, errorMessage = null) }
        bearerUseCase.retryPendingStatus()
    }

    fun onZoneTapped() = intent {
        val current = state.current ?: return@intent
        if (!current.isLive) return@intent
        presentZonePicker(sheet = BearerBoardSheet.Move, selected = current.zoneId)
    }

    fun onActivityTapped() = intent {
        val current = state.current ?: return@intent
        if (!current.isLive) return@intent
        reduce {
            state.copy(
                sheet = BearerBoardSheet.ChangeActivity,
                destination = BearerBoardDestination.ActivityPicker(selected = current.activity ?: state.lastActivity),
            )
        }
    }

    fun onRestTapped() = intent {
        if (state.current?.lifecycle != BearerFlagLifecycle.Active) return@intent
        enqueue(BearerStatusChange.Rest)
    }

    fun onResumeTapped() = intent {
        if (state.current?.lifecycle != BearerFlagLifecycle.Resting) return@intent
        val activity = state.lastActivity
        if (activity != null) {
            enqueue(BearerStatusChange.Activity(activity))
        } else {
            reduce {
                state.copy(sheet = BearerBoardSheet.ChangeActivity, destination = BearerBoardDestination.ActivityPicker(selected = null))
            }
        }
    }

    fun onEndTapped() = intent {
        val lifecycle = state.current?.lifecycle ?: return@intent
        if (lifecycle == BearerFlagLifecycle.Ended) return@intent
        reduce { state.copy(destination = BearerBoardDestination.EndConfirm) }
    }

    fun onRaiseTapped() = intent {
        if (state.current?.lifecycle != BearerFlagLifecycle.Scheduled) return@intent
        presentZonePicker(sheet = BearerBoardSheet.StartZone, selected = state.board?.profile?.homeZoneId)
    }

    fun onRestartTapped() = intent {
        if (state.current?.lifecycle != BearerFlagLifecycle.Ended || state.restartUsedToday) return@intent
        presentZonePicker(sheet = BearerBoardSheet.RestartZone, selected = state.board?.profile?.homeZoneId)
    }

    // MARK: - Destination

    /** 존 시트에서 골랐다(iOS `zonePicker(.delegate(.picked))`). */
    fun onZonePicked(zoneId: String) = intent {
        when (state.sheet) {
            BearerBoardSheet.Move -> {
                reduce { state.copy(sheet = null, destination = null) }
                if (zoneId != state.current?.zoneId) enqueue(BearerStatusChange.Move(zoneId))
            }

            BearerBoardSheet.StartZone -> reduce {
                state.copy(
                    sheet = BearerBoardSheet.StartActivity(zoneId),
                    destination = BearerBoardDestination.ActivityPicker(selected = state.lastActivity),
                )
            }

            BearerBoardSheet.RestartZone -> reduce {
                state.copy(
                    sheet = BearerBoardSheet.RestartActivity(zoneId),
                    destination = BearerBoardDestination.ActivityPicker(selected = state.lastActivity),
                )
            }

            else -> reduce { state.copy(sheet = null, destination = null) }
        }
    }

    /** 활동 시트에서 골랐다(iOS `activityPicker(.delegate(.picked))`). */
    fun onActivityPicked(activity: BearerActivity) = intent {
        val sheet = state.sheet
        reduce { state.copy(sheet = null, destination = null) }
        when (sheet) {
            BearerBoardSheet.ChangeActivity -> {
                val current = state.current
                if (current?.lifecycle == BearerFlagLifecycle.Resting || activity != current?.activity) {
                    enqueue(BearerStatusChange.Activity(activity))
                }
            }

            is BearerBoardSheet.StartActivity -> enqueue(BearerStatusChange.Start(zoneId = sheet.zoneId, activity = activity))

            is BearerBoardSheet.RestartActivity -> enqueue(BearerStatusChange.Restart(zoneId = sheet.zoneId, activity = activity))

            else -> Unit
        }
    }

    /** 종료 팝업 "깃발 내리기" */
    fun onEndConfirmed() = intent {
        reduce { state.copy(destination = null) }
        enqueue(BearerStatusChange.End)
    }

    /** 닫기 팝업 "닫기" */
    fun onCloseConfirmed() = intent {
        reduce { state.copy(destination = null) }
        postSideEffect(BearerBoardSideEffect.CloseRequested)
    }

    /** 종료 팝업 "닫기" · 닫기 팝업 "계속 보기" */
    fun onDialogDismissed() = intent {
        reduce { state.copy(destination = null) }
    }

    /** 사용자가 시트를 내렸다(끌어내리기 · 바깥 누르기). 고른 뒤 다음 시트로 넘어갈 때는 부르지 않는다. iOS `.dismiss`. */
    fun onSheetDismissed() = intent {
        reduce { state.copy(sheet = null, destination = null) }
    }

    // MARK: - Async

    private suspend fun load() = subIntent {
        reduce { state.copy(isLoading = true, loadFailed = false) }
        val flagId = state.flagId
        val festivalId = state.festivalId
        val job =
            intent {
                coroutineScope {
                    // 팩(존 이름) 실패는 무시한다.
                    val festival = async { fetchFestivalOrNull(festivalId) }
                    val board =
                        try {
                            bearerUseCase.fetchBoard(flagId = flagId)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: BearerError) {
                            festival.await()
                            loadFailed(error)
                            return@coroutineScope
                        } catch (_: Exception) {
                            festival.await()
                            loadFailed(BearerError.Unavailable())
                            return@coroutineScope
                        }
                    loaded(board, festival.await())
                }
            }
        loadJob.getAndSet(job)?.cancel()
    }

    private suspend fun fetchFestivalOrNull(festivalId: String): BearerFestival? = try {
        bearerUseCase.fetchFestival(festivalId = festivalId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    /** 대기열 알림을 먼저 구독한 뒤 대기 건을 받고 전송을 다시 시작한다. iOS `observeOutbox`. */
    private suspend fun observeOutbox() = subIntent {
        val flagId = state.flagId
        val job =
            intent {
                coroutineScope {
                    val events = Channel<BearerOutboxEvent>(Channel.UNLIMITED)
                    // 대기 건을 읽기 전에 구독해 그 사이 알림을 놓치지 않는다.
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            bearerUseCase.statusEvents(flagId = flagId).collect { events.send(it) }
                        } finally {
                            events.close()
                        }
                    }
                    pendingLoaded(bearerUseCase.pendingStatus(flagId = flagId))
                    bearerUseCase.retryPendingStatus()
                    for (event in events) outboxEvent(event)
                }
            }
        outboxJob.getAndSet(job)?.cancel()
    }

    private suspend fun expireCheck(id: Int) = subIntent {
        val job =
            intent {
                delay(CHECK_DURATION)
                reduce { if (state.check?.id == id) state.copy(check = null) else state }
            }
        checkJob.getAndSet(job)?.cancel()
    }

    // MARK: - Inner

    /** 테스트가 서버 응답을 바로 넣을 때도 쓴다(iOS `.inner(.loaded)`). */
    internal fun onLoaded(board: BearerBoard, festival: BearerFestival?) = intent { loaded(board, festival) }

    /** 테스트가 대기열 알림을 바로 넣을 때도 쓴다(iOS `.inner(.outbox)`). */
    internal fun onOutboxEvent(event: BearerOutboxEvent) = intent { outboxEvent(event) }

    private suspend fun loaded(board: BearerBoard, festival: BearerFestival?) = subIntent {
        reduce {
            // 보내지 못한 변경이 있으면 서버 상태 위에 그 변경을 다시 얹어 보인다.
            state
                .copy(
                    isLoading = false,
                    board = board,
                    festival = festival ?: state.festival,
                    lastSyncedAt = clock.instant(),
                    isOffline = false,
                ).adopting(board.status)
                .applyingPending()
        }
    }

    private suspend fun loadFailed(error: BearerError) = subIntent {
        reduce {
            if (state.board == null) {
                state.copy(isLoading = false, loadFailed = true, errorMessage = loadMessage(error))
            } else {
                // 보드가 있으면 실패 화면 대신 오프라인 표시만 한다.
                state.copy(isLoading = false, isOffline = error is BearerError.Offline)
            }
        }
    }

    private suspend fun pendingLoaded(pending: List<BearerStatusRequest>) = subIntent {
        reduce { state.copy(pending = pending).applyingPending() }
        if (state.board == null) load()
    }

    private suspend fun outboxEvent(event: BearerOutboxEvent) = subIntent {
        when (event) {
            is BearerOutboxEvent.Pending -> reduce { state.copy(pending = event.requests) }

            is BearerOutboxEvent.Sent -> {
                val wasOffline = state.isOffline
                reduce {
                    val next = state.copy(isOffline = false, isSendPaused = false, errorMessage = null, lastSyncedAt = clock.instant())
                    if (next.pending.isEmpty()) next.adopting(event.status) else next
                }
                // 연결이 돌아왔다. 오프라인 동안 다른 기수 · 운영팀이 바꾼 상태를 다시 읽는다(Android 추가, 설계 B-3).
                if (wasOffline) load()
            }

            BearerOutboxEvent.Offline -> reduce { state.copy(isOffline = true) }

            BearerOutboxEvent.NeedsLogin -> reduce {
                state.copy(isSendPaused = true, errorMessage = "로그인이 풀렸어요. 다시 로그인하면 이어서 보내요")
            }

            is BearerOutboxEvent.GaveUp -> reduce {
                state.copy(isSendPaused = true, errorMessage = "서버에 보내지 못했어요. 잠시 뒤 다시 보내기를 눌러 주세요")
            }

            is BearerOutboxEvent.Rejected -> {
                reduce { state.copy(errorMessage = rejectMessage(event.error)) }
                load()
            }
        }
    }

    // MARK: - Helper

    private suspend fun presentZonePicker(sheet: BearerBoardSheet, selected: String?) = subIntent {
        reduce {
            state.copy(sheet = sheet, destination = BearerBoardDestination.ZonePicker(zones = state.zones, selectedZoneId = selected))
        }
    }

    /** 화면에 먼저 반영하고 1초 확인을 띄운 뒤 대기열에 넣는다. iOS `enqueue(_:_:)`. */
    private suspend fun enqueue(change: BearerStatusChange) = subIntent {
        // 누른 사용자를 모르면 다른 계정으로 보낼 수 있어 받지 않는다.
        val ownerUserId = state.viewerUserId
        if (ownerUserId == null) {
            reduce { state.copy(errorMessage = "로그인한 뒤에 바꿀 수 있어요") }
            return@subIntent
        }
        val request = bearerUseCase.makeStatusRequest(change)
        reduce {
            val sequence = state.checkSequence + 1
            val lastActivity =
                when (change) {
                    is BearerStatusChange.Activity -> change.activity
                    is BearerStatusChange.Start -> change.activity
                    is BearerStatusChange.Restart -> change.activity
                    else -> state.lastActivity
                }
            state.copy(
                pending = state.pending + request,
                errorMessage = null,
                current = state.current?.applying(change, request.clientAt),
                lastActivity = lastActivity,
                checkSequence = sequence,
                check =
                    BearerBoardCheck(
                        id = sequence,
                        title = state.checkTitle(change),
                        message = if (state.isOffline) "연결되면 팔로워에게 알려요" else "팔로워에게 알렸어요",
                    ),
            )
        }
        postSideEffect(BearerBoardSideEffect.CheckHaptic)
        expireCheck(state.checkSequence)
        val flagId = state.flagId
        intent { bearerUseCase.enqueueStatus(flagId = flagId, ownerUserId = ownerUserId, request = request) }
    }

    private fun BearerBoardState.checkTitle(change: BearerStatusChange): String = when (change) {
        is BearerStatusChange.Start -> "깃발을 올렸어요"
        is BearerStatusChange.Restart -> "깃발을 다시 올렸어요"
        is BearerStatusChange.Move -> "${(zoneName(change.zoneId) ?: "새 존").withEuro()} 바뀌었어요"
        is BearerStatusChange.Activity -> "${change.activity.title.withEuro()} 바뀌었어요"
        BearerStatusChange.Rest -> "쉬는 중으로 바뀌었어요"
        BearerStatusChange.End -> "오늘 깃발을 내렸어요"
    }

    private val BearerBoardCurrent.isLive: Boolean
        get() = lifecycle == BearerFlagLifecycle.Active || lifecycle == BearerFlagLifecycle.Resting

    companion object {
        /** 확인 오버레이를 보이는 시간(iOS `checkDuration`) */
        val CHECK_DURATION = 1.seconds

        fun loadMessage(error: BearerError): String = when (error) {
            is BearerError.Offline -> "인터넷에 연결되지 않아 기수 보드를 열지 못했어요"
            is BearerError.NotBearer -> "이 깃발의 기수가 아니에요"
            is BearerError.NotFound -> "깃발을 찾을 수 없어요"
            is BearerError.NotAuthenticated -> "로그인한 뒤에 열어 주세요"
            else -> "기수 보드를 불러오지 못했어요"
        }

        fun rejectMessage(error: BearerError): String = when (error) {
            is BearerError.Stale -> "다른 기기에서 먼저 바뀌어 최신 상태로 돌렸어요"
            is BearerError.TransitionRejected -> "지금 상태에서는 바꿀 수 없어 최신 상태로 돌렸어요"
            is BearerError.ZoneNotFound -> "존을 찾을 수 없어 최신 상태로 돌렸어요"
            is BearerError.NotBearer -> "이 깃발의 기수가 아니에요"
            is BearerError.TermsRequired -> "새 약관에 동의한 뒤에 바꿀 수 있어요"
            is BearerError.NotAuthenticated -> "로그인한 뒤에 바꿀 수 있어요"
            else -> "바꾸지 못해 최신 상태로 돌렸어요"
        }
    }
}
