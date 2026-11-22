package io.festicore.feature.timetable.main

import androidx.lifecycle.ViewModel
import io.festicore.domain.auth.CurrentUserProvider
import io.festicore.domain.festival.FestivalBoardSelection
import io.festicore.domain.festival.FestivalBoardSelectionUseCase
import io.festicore.domain.festival.FestivalSchedule
import io.festicore.domain.festival.FestivalScheduleError
import io.festicore.domain.festival.FestivalScheduleSaved
import io.festicore.domain.festival.FestivalScheduleUseCase
import io.festicore.domain.timetable.FestivalPackTimetableMapper
import io.festicore.domain.timetable.TimetableDay
import io.festicore.domain.timetable.TimetableLineupDay
import io.festicore.domain.timetable.TimetableOverlapCalculator
import io.festicore.domain.timetable.TimetableSetDetail
import io.festicore.feature.timetable.SchedulePickResult
import io.festicore.feature.timetable.TimetableRoute
import io.festicore.feature.timetable.setdetail.TimetableSetDetailContent
import io.festicore.feature.timetable.setdetail.TimetableSetDetailState
import java.time.Clock
import java.time.Duration as JavaDuration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S02 타임테이블(iOS `TimetableFeature`). 라이브 보드가 공유 상태에 쓴 선택 축제 · 표시 문서(설계 T1)를 읽어
 * 날짜별 격자 · 공개 전 라인업 · 출처 · 오프라인 안내로 옮기고, 공연 상세 시트 상태까지 가진다(설계 T7).
 * 내 일정은 서버와 맞춘다(iOS `TimetableFeature+Schedule`): 저장은 한 번에 하나만 보내고, 그동안 바뀐 목록은 끝난 뒤
 * 마지막 목록으로 한 번 더 저장한다. 충돌하면 다른 기기의 변경을 지키기 위해 서버 일정을 따른다.
 * 규칙은 `feature/timetable/.spec/timetable-screen.md`.
 */
class TimetableViewModel(
    private val boardSelectionUseCase: FestivalBoardSelectionUseCase,
    private val scheduleUseCase: FestivalScheduleUseCase,
    private val currentUserProvider: CurrentUserProvider,
    private val mapper: FestivalPackTimetableMapper,
    private val overlapCalculator: TimetableOverlapCalculator,
    private val clock: Clock = Clock.systemUTC(),
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) : ViewModel(),
    OrbitContainerHost<TimetableState, TimetableState, TimetableSideEffect> {
    override val container: OrbitContainer<TimetableState, TimetableState, TimetableSideEffect> =
        orbitContainer(TimetableState()) {
            coroutineScope {
                launch { currentUserProvider.currentUserId.collect { userChanged(it) } }
                boardSelectionUseCase.selection.collect { selectionChanged(it) }
            }
        }

    /** 이 화면이 보고 있는 계정. 여기서 바뀔 때만 계정 변경으로 본다(만들 때 읽어 구독이 늦어도 놓치지 않는다). */
    private var lastUserId = currentUserProvider.currentUserId.value

    private val clockJob = AtomicReference<Job?>(null)
    private val toastJob = AtomicReference<Job?>(null)
    private val loadJob = AtomicReference<Job?>(null)
    private val saveJob = AtomicReference<Job?>(null)

    // MARK: - View

    /** 화면이 보이기 시작했다(iOS `clockStarted`). 현재 시각을 반영하고 1초마다 다시 읽는다. */
    fun onClockStarted() = intent {
        update { it.withClock(clock.instant()) }
        val job =
            intent {
                while (true) {
                    sleep(CLOCK_INTERVAL)
                    onClockUpdated(clock.instant())
                }
            }
        clockJob.getAndSet(job)?.cancel()
    }

    /** 화면이 보이지 않는다(iOS `clockStopped`). */
    fun onClockStopped() = intent {
        clockJob.getAndSet(null)?.cancel()
    }

    /** "다시 시도"(iOS `retryTapped` → 상위가 보드 온라인 상세를 다시 읽는다). */
    fun onRetryClicked() = intent {
        val festivalId = boardSelectionUseCase.selection.value?.festivalId ?: return@intent
        boardSelectionUseCase.requestRetry(festivalId)
    }

    /** 날짜 세그먼트 · 칩 · 좌우 스와이프(iOS `daySelected`). 범위 밖은 무시한다. */
    fun onDaySelected(index: Int) = intent {
        if (index !in state.days.indices) return@intent
        update { current ->
            val warning = if (index != current.selectedDayIndex) false else current.shouldShowOverlapWarning
            val selected = current.copy(selectedDayIndex = index, shouldShowOverlapWarning = warning)
            selected.clockAt?.let { selected.withClock(it) } ?: selected
        }
    }

    fun onMineOnlyToggled() = intent {
        update { it.copy(showsMineOnly = !it.showsMineOnly) }
    }

    /** 블록을 눌렀다(iOS `setTapped`). 없는 공연이면 시트를 닫는다. 다른 공연을 열면 토스트를 멈춘다. */
    fun onSetClicked(id: String) = intent {
        update { current ->
            current.copy(setDetail = current.setDetailContent(id, clock.instant())?.let { TimetableSetDetailState(id, it) })
        }
    }

    /** 두 손가락 확대가 끝났다(iOS `zoomChanged`). */
    fun onZoomChanged(zoom: Double) = intent {
        update { it.copy(zoom = zoom) }
    }

    fun onChangeBannerClicked() = intent {
        postSideEffect(TimetableSideEffect.ShowChanges)
    }

    fun onSearchClicked() = intent {
        postSideEffect(TimetableSideEffect.Search)
    }

    fun onNotifyClicked() = intent {
        postSideEffect(TimetableSideEffect.RequestNotification)
    }

    /** "내 일정 N개"(iOS `schedulePickTapped`). 이미 열려 있거나 서버와 맞춰져 있지 않으면 열지 않는다. */
    fun onSchedulePickClicked() = intent {
        plan {
            val festivalId = state.festivalId
            if (state.isPickOpen || !state.canPickSchedule || festivalId == null) return@plan
            state = state.copy(isPickOpen = true)
            val route =
                TimetableRoute.SchedulePick(
                    festivalId = festivalId,
                    selectedSetIds = state.mySetIds.sorted(),
                    previousClientAtEpochMillis = state.lastClientAt?.toEpochMilli(),
                )
            effects += ScheduleEffect.OpenPick(route)
        }
    }

    /**
     * 내 일정 고르기 결과(iOS `handleSchedulePickDelegate`). Coordinator가 고르기를 닫고 넘긴다. 시스템 뒤로는 [SchedulePickResult.Skipped].
     * 저장했으면 서버에 맞춰진 목록으로 내 일정을 바꾸고 보드에 알린다. 이미 닫힌(축제 · 계정 · 팩이 바뀐) 고르기의 결과는 버린다.
     */
    fun onSchedulePickResult(result: SchedulePickResult) = intent {
        plan {
            if (!state.isPickOpen) return@plan
            state = state.copy(isPickOpen = false)
            if (result !is SchedulePickResult.Saved || result.festivalId != state.festivalId) return@plan
            val previousSetIds = state.mySetIds
            state = state.copy(mySetIds = result.setIds.intersect(state.allSetIds))
            if (previousSetIds != state.mySetIds) state = state.withOverlapWarning()
            state =
                state.copy(
                    scheduleSync = TimetableScheduleSync.Ready,
                    isScheduleLimitReached = false,
                    uncertainSave = null,
                    // 이전 저장의 늦은 응답이 서버 목록을 되돌리거나 알림을 한 번 더 보내지 않게 한다.
                    isSaving = false,
                    needsSave = false,
                    // 다음 저장이 방금 저장한 요청보다 늦은 요청이 되게 한다.
                    lastClientAt = nextClientAt(state.lastClientAt),
                )
            effects += ScheduleEffect.CancelSave
            effects += ScheduleEffect.Notify(FestivalScheduleSaved(result.festivalId, state.mySetIds))
        }
    }

    // MARK: - Scope (공연 상세 시트)

    /**
     * 시트의 "내 일정에 넣기" 토글(iOS `toggleSchedule`). 서버 일정을 읽는 중이면 무시한다.
     * 빼면 한도 안내를 끄고, 넣을 때는 끝난 공연을 거절하고 60개면 한도 안내를 띄운다. 넣으면 토스트를 2초 보인다.
     */
    fun onScheduleToggled() = intent {
        plan {
            val id = state.setDetail?.setId ?: return@plan
            if (state.scheduleSync == TimetableScheduleSync.Loading) return@plan
            if (id in state.mySetIds) {
                state = state.copy(mySetIds = state.mySetIds - id, isScheduleLimitReached = false)
            } else {
                if (state.setDetailContent(id, now)?.detail?.status == TimetableSetDetail.Status.Ended) return@plan
                if (state.mySetIds.size >= TimetableState.SCHEDULE_LIMIT) {
                    state = state.copy(isScheduleLimitReached = true)
                    return@plan
                }
                state = state.copy(mySetIds = state.mySetIds + id)
                effects += ScheduleEffect.ShowToast(id)
            }
            state = state.withOverlapWarning()
            if (state.scheduleSync == TimetableScheduleSync.Ready) saveSchedule()
        }
    }

    /** 시트를 닫았다(iOS `setDetail.dismiss`). */
    fun onSetDetailDismissed() = intent {
        update { it.copy(setDetail = null, isScheduleLimitReached = false) }
    }

    // MARK: - Async

    /** 토스트를 2초 보인다. 다시 넣으면 처음부터 센다(iOS `showScheduleToast`). */
    private suspend fun showScheduleToast(setId: String): Unit = subIntent {
        update { current -> current.copy(setDetail = current.setDetail?.copy(isScheduleToastVisible = true)) }
        val job =
            intent {
                sleep(TOAST_DURATION)
                onScheduleToastExpired(setId)
            }
        toastJob.getAndSet(job)?.cancel()
    }

    /** 서버 일정을 읽는다. 새로 읽으면 이전 읽기를 취소한다(iOS `fetchSchedule`, `cancelInFlight`). */
    private fun fetchSchedule(festivalId: String, requestId: Int) = replace(loadJob) {
        onScheduleLoaded(festivalId, requestId, attempt { scheduleUseCase.mySchedule(festivalId) })
    }

    /** 내 일정 목록을 서버에 저장한다(iOS `replaceSchedule`). */
    private fun replaceSchedule(festivalId: String, save: TimetableScheduleSave) = replace(saveJob) {
        onScheduleSaved(festivalId, save, attempt { scheduleUseCase.saveMySchedule(festivalId, save.setIds, save.clientAt) })
    }

    // MARK: - Inner

    private suspend fun onClockUpdated(now: Instant): Unit = subIntent {
        update { it.withClock(now) }
    }

    /** iOS `applyLoadedSchedule`. 같은 축제 · 같은 요청 · 읽는 중일 때만 받는다. */
    private suspend fun onScheduleLoaded(
        festivalId: String,
        requestId: Int,
        result: Result<FestivalSchedule>,
    ): Unit = subIntent {
        plan {
            if (festivalId != state.festivalId || requestId != state.loadRequestId || state.scheduleSync != TimetableScheduleSync.Loading) {
                return@plan
            }
            val schedule = result.getOrNull()
            state =
                if (schedule == null) {
                    state.copy(scheduleSync = TimetableScheduleSync.LocalOnly)
                } else {
                    state.copy(
                        mySetIds = schedule.setIds.toSet().intersect(state.allSetIds),
                        shouldShowOverlapWarning = false,
                        scheduleSync = TimetableScheduleSync.Ready,
                    )
                }
        }
    }

    /** iOS `applySavedSchedule`. 같은 축제 · 같은 계정 세대 · 저장 중일 때만 받는다. */
    private suspend fun onScheduleSaved(
        festivalId: String,
        save: TimetableScheduleSave,
        result: Result<FestivalSchedule>,
    ): Unit = subIntent {
        plan {
            if (festivalId != state.festivalId || save.generation != state.accountGeneration || !state.isSaving) return@plan
            val needsSave = state.needsSave
            state = state.copy(isSaving = false, needsSave = false)
            when (val error = result.exceptionOrNull()) {
                null -> {
                    effects += ScheduleEffect.Notify(FestivalScheduleSaved(festivalId, save.setIds.toSet()))
                    if (needsSave) saveSchedule()
                }

                is FestivalScheduleError.Stale -> {
                    state =
                        state.copy(mySetIds = error.schedule.setIds.toSet().intersect(state.allSetIds), shouldShowOverlapWarning = false)
                    effects += ScheduleEffect.Notify(FestivalScheduleSaved(festivalId, state.mySetIds))
                }

                is FestivalScheduleError.Unauthorized -> state = state.copy(scheduleSync = TimetableScheduleSync.LocalOnly)

                is FestivalScheduleError.Rejected -> loadSchedule()

                else -> state = state.copy(uncertainSave = save)
            }
        }
    }

    private suspend fun onScheduleToastExpired(setId: String): Unit = subIntent {
        update { current ->
            val detail = current.setDetail?.takeIf { it.setId == setId } ?: return@update current
            current.copy(setDetail = detail.copy(isScheduleToastVisible = false))
        }
    }

    // MARK: - 입력 (iOS `MainTabCoordinator+Sync`가 보내던 값)

    /** 보드 공유 상태가 바뀌었다(iOS `syncTimetable`). 팩 값을 옮기고 축제가 바뀌었으면 비우고, 같은 축제의 공연이 바뀌었으면 정리한다. */
    internal fun onSelectionChanged(selection: FestivalBoardSelection?) = intent { selectionChanged(selection) }

    /** iOS `packSynced`. 고른 날은 같은 날짜 ID로 유지하고, 없으면 첫날이다. */
    internal fun onPackSynced(
        days: List<TimetableDay>,
        lineup: List<TimetableLineupDay>,
        source: String,
        contentStatus: TimetableContentStatus,
        offlineMessage: String?,
    ) = intent { packSynced(days, lineup, source, contentStatus, offlineMessage) }

    /** iOS `festivalChanged`. 같으면 무시하고, 바뀌면 시트 · 고르기를 닫고 내 일정을 비운 뒤 서버 일정을 읽는다. */
    internal fun onFestivalChanged(festivalId: String?) = intent { festivalChanged(festivalId) }

    /** iOS `packUpdated`. 시각을 다시 계산하고 고르기를 닫고, 팩에서 사라진 공연을 내 일정 · 시트에서 뺀다. */
    internal fun onPackUpdated() = intent { packUpdated() }

    /** iOS `accountChanged`. 이전 계정의 읽기 · 저장을 버리고 내 일정을 비운 뒤 다시 읽는다. 시트는 그대로 둔다(상위가 닫는다). */
    internal fun onAccountChanged() = intent { plan { changeAccount() } }

    /**
     * 타임테이블 탭에 다시 들어왔다(iOS `timetableAppeared`, 탭 연결 PR에서 흐름이 부른다).
     * 고르기가 열려 있거나 저장 중이면 겹치지 않도록 무시하고, 결과를 모르는 저장이 있으면 다시 저장, 없으면 서버 일정을 읽는다.
     */
    fun onTimetableAppeared() = intent {
        plan {
            if (state.festivalId == null || state.isSaving || state.isPickOpen) return@plan
            if (state.uncertainSave == null) loadSchedule() else saveSchedule()
        }
    }

    /**
     * 확인한 계정이 바뀌었다(iOS `MainTabCoordinator` `clearAccountDisplayData` → `accountChanged`).
     * 다른 계정이거나 로그아웃이면 시트까지 비우고 다시 읽는다. 로그인(없음 → 있음)은 다음 진입이 다시 읽는다.
     */
    private suspend fun userChanged(userId: String?): Unit = subIntent {
        if (userId == lastUserId) return@subIntent
        val previous = lastUserId
        lastUserId = userId
        if (previous == null) return@subIntent
        plan {
            state = state.copy(setDetail = null)
            changeAccount()
        }
    }

    /** iOS `mySetIDsChanged`. 다른 화면에서 저장한 같은 축제의 내 일정. */
    internal fun onMySetIdsChanged(setIds: Set<String>) = intent {
        update { it.copy(mySetIds = setIds) }
    }

    private suspend fun selectionChanged(selection: FestivalBoardSelection?): Unit = subIntent {
        val festivalId = selection?.festivalId
        val pack = selection?.document?.takeIf { it.id == festivalId }
        val isSameFestival = festivalId == state.festivalId
        val days = pack?.let { mapper.days(it, if (isSameFestival) state.days else emptyList()) }.orEmpty()
        // 메모만 바뀌면 공연 목록이 바뀐 것으로 보지 않는다.
        val daysChanged = state.days.map { it.ignoringNotes } != days.map { it.ignoringNotes }
        packSynced(
            days = days,
            lineup = pack?.let(mapper::lineup).orEmpty(),
            source = mapper.source(pack?.source),
            contentStatus = contentStatus(selection, hasDocument = pack != null),
            offlineMessage = offlineMessage(selection, hasDocument = pack != null),
        )
        when {
            !isSameFestival -> festivalChanged(festivalId)
            daysChanged -> packUpdated()
        }
    }

    private suspend fun packSynced(
        days: List<TimetableDay>,
        lineup: List<TimetableLineupDay>,
        source: String,
        contentStatus: TimetableContentStatus,
        offlineMessage: String?,
    ) = subIntent {
        update { current ->
            val selectedDayId = current.selectedDay?.id
            current.copy(
                days = days,
                selectedDayIndex = days.indexOfFirst { it.id == selectedDayId }.coerceAtLeast(0),
                lineup = lineup,
                source = source,
                contentStatus = contentStatus,
                offlineMessage = offlineMessage,
            )
        }
    }

    private suspend fun festivalChanged(festivalId: String?): Unit = subIntent {
        plan { changeFestival(festivalId) }
    }

    private suspend fun packUpdated(): Unit = subIntent {
        plan {
            state.clockAt?.let { state = state.withClock(it) }
            // 열린 내 일정 고르기는 이전 공연 목록을 들고 있으므로 닫고 새 목록으로 정리한다.
            closePick()
            trimSchedule()
        }
    }

    // MARK: - 동기화 규칙 (iOS `TimetableFeature+Schedule`)

    /** 한 번의 상태 변경과 그 뒤에 할 일. [plan]이 상태를 한 번에 바꾸고 할 일을 차례로 한다(iOS Reducer의 상태 + Effect). */
    private inner class Planner(
        var state: TimetableState,
        val now: Instant,
    ) {
        val effects = mutableListOf<ScheduleEffect>()

        fun changeFestival(festivalId: String?) {
            if (festivalId == state.festivalId) return
            closePick()
            state = state.copy(festivalId = festivalId, setDetail = null)
            effects += ScheduleEffect.CancelLoad
            effects += ScheduleEffect.CancelSave
            resetSchedule()
            loadSchedule()
        }

        fun changeAccount() {
            state = state.copy(accountGeneration = state.accountGeneration + 1, loadRequestId = state.loadRequestId + 1)
            closePick()
            effects += ScheduleEffect.CancelLoad
            effects += ScheduleEffect.CancelSave
            resetSchedule()
            loadSchedule()
        }

        fun closePick() {
            if (!state.isPickOpen) return
            state = state.copy(isPickOpen = false)
            effects += ScheduleEffect.ClosePick
        }

        fun resetSchedule() {
            state =
                state.copy(
                    mySetIds = emptySet(),
                    shouldShowOverlapWarning = false,
                    scheduleSync = TimetableScheduleSync.Idle,
                    isScheduleLimitReached = false,
                    isSaving = false,
                    needsSave = false,
                    lastClientAt = null,
                    uncertainSave = null,
                )
        }

        /** 팩에서 사라진 공연을 내 일정에서 뺀다. 없는 공연 ID는 서버가 거부한다. */
        fun trimSchedule() {
            val validIds = state.allSetIds
            if (state.setDetail?.setId?.let { it !in validIds } == true) state = state.copy(setDetail = null)
            val trimmed = state.mySetIds.intersect(validIds)
            if (trimmed == state.mySetIds) return
            state = state.copy(mySetIds = trimmed, shouldShowOverlapWarning = false)
            if (state.scheduleSync == TimetableScheduleSync.Ready) saveSchedule()
        }

        fun loadSchedule() {
            val festivalId = state.festivalId ?: return
            if (state.scheduleSync == TimetableScheduleSync.Loading) return
            state = state.copy(scheduleSync = TimetableScheduleSync.Loading, loadRequestId = state.loadRequestId + 1)
            effects += ScheduleEffect.Load(festivalId, state.loadRequestId)
        }

        fun saveSchedule() {
            if (state.isSaving) {
                state = state.copy(needsSave = true)
                return
            }
            val uncertain = state.uncertainSave
            if (uncertain != null) {
                state = state.copy(uncertainSave = null)
                // 결과를 모르는 요청과 목록이 같으면 같은 요청을 다시 보내 서버가 중복으로 처리하게 한다.
                // 다르면 그사이 다른 기기가 저장했을 수 있어 서버 일정을 먼저 읽는다.
                if (uncertain.setIds.toSet() != state.mySetIds) loadSchedule() else startSave(uncertain)
                return
            }
            // 서버는 같은 시각의 다른 목록을 충돌로 보므로 이전 요청보다 1ms 이상 늦춘다.
            val clientAt = nextClientAt(state.lastClientAt, now)
            state = state.copy(lastClientAt = clientAt)
            startSave(TimetableScheduleSave(state.mySetIds.sorted(), clientAt, state.accountGeneration))
        }

        private fun startSave(save: TimetableScheduleSave) {
            val festivalId = state.festivalId ?: return
            state = state.copy(isSaving = true)
            effects += ScheduleEffect.Save(festivalId, save)
        }
    }

    /** [Planner]가 상태를 바꾼 뒤 할 일(iOS Effect). */
    private sealed interface ScheduleEffect {
        data class Load(
            val festivalId: String,
            val requestId: Int,
        ) : ScheduleEffect

        data class Save(
            val festivalId: String,
            val save: TimetableScheduleSave,
        ) : ScheduleEffect

        data class Notify(val saved: FestivalScheduleSaved) : ScheduleEffect

        data class ShowToast(val setId: String) : ScheduleEffect

        data class OpenPick(val route: TimetableRoute.SchedulePick) : ScheduleEffect

        data object ClosePick : ScheduleEffect

        data object CancelLoad : ScheduleEffect

        data object CancelSave : ScheduleEffect
    }

    /**
     * 상태를 한 번에 바꾸고(겹침 · 열린 시트 내용도 다시 계산, iOS `refreshSetDetail`) 정해진 할 일을 차례로 한다.
     * 공연을 빼 토스트가 숨으면 토스트 대기를 멈춘다.
     */
    private suspend fun plan(block: Planner.() -> Unit): Unit = subIntent {
        var effects: List<ScheduleEffect> = emptyList()
        reduce {
            val now = clock.instant()
            val planner = Planner(state, now).apply(block)
            effects = planner.effects
            planner.state.refreshed(now)
        }
        if (state.setDetail?.isScheduleToastVisible != true) toastJob.getAndSet(null)?.cancel()
        effects.forEach { effect ->
            when (effect) {
                is ScheduleEffect.Load -> fetchSchedule(effect.festivalId, effect.requestId)
                is ScheduleEffect.Save -> replaceSchedule(effect.festivalId, effect.save)
                is ScheduleEffect.Notify -> scheduleUseCase.notifyMyScheduleSaved(effect.saved)
                is ScheduleEffect.ShowToast -> showScheduleToast(effect.setId)
                is ScheduleEffect.OpenPick -> postSideEffect(TimetableSideEffect.SchedulePick(effect.route))
                ScheduleEffect.ClosePick -> postSideEffect(TimetableSideEffect.CloseSchedulePick)
                ScheduleEffect.CancelLoad -> loadJob.getAndSet(null)?.cancel()
                ScheduleEffect.CancelSave -> saveJob.getAndSet(null)?.cancel()
            }
        }
    }

    // MARK: - 계산

    /** 상태를 바꾸고 겹침 · 열린 시트 내용을 다시 계산한다. */
    private suspend fun update(transform: (TimetableState) -> TimetableState): Unit = plan { state = transform(state) }

    /** 다음 저장 요청 시각. 이전 요청보다 1ms 이상 늦다. */
    private fun nextClientAt(previous: Instant?, now: Instant = clock.instant()): Instant =
        previous?.plusMillis(1)?.let { maxOf(now, it) } ?: now

    private fun replace(slot: AtomicReference<Job?>, block: suspend () -> Unit) {
        val job = intent { block() }
        slot.getAndSet(job)?.cancel()
    }

    private fun TimetableState.refreshed(now: Instant): TimetableState {
        val day = selectedDay
        val overlaps = day?.let { overlapCalculator.overlaps(days, mySetIds, it) }.orEmpty()
        val overlapping =
            if (day == null || !shouldShowOverlapWarning) {
                emptySet()
            } else {
                day.sets.filter {
                    it.id in mySetIds && overlapCalculator.setOverlaps(days, mySetIds, it, day).isNotEmpty()
                }.mapTo(mutableSetOf()) { it.id }
            }
        val next = copy(overlaps = overlaps, overlappingSetIds = overlapping)
        val detail = setDetail ?: return next
        val content = next.setDetailContent(detail.setId, now)?.takeIf { it != detail.content } ?: return next
        val hidesToast = detail.isScheduleToastVisible && !content.detail.isScheduled
        return next.copy(setDetail = detail.copy(content = content, isScheduleToastVisible = detail.isScheduleToastVisible && !hidesToast))
    }

    /** 일정을 바꾼 뒤 겹침이 있으면 격자 경고를 띄운다. */
    private fun TimetableState.withOverlapWarning(): TimetableState {
        val day = selectedDay ?: return copy(shouldShowOverlapWarning = false)
        return copy(shouldShowOverlapWarning = overlapCalculator.overlaps(days, mySetIds, day).isNotEmpty())
    }

    /** 상세 시트 내용(iOS `setDetailContent(id:now:)`). 없는 공연이면 null. */
    private fun TimetableState.setDetailContent(id: String, now: Instant): TimetableSetDetailContent? {
        val day = days.firstOrNull { day -> day.sets.any { it.id == id } } ?: return null
        val set = day.sets.firstOrNull { it.id == id } ?: return null
        val interval = day.interval(set)
        return TimetableSetDetailContent(
            detail =
                TimetableSetDetail(
                    artist = set.artist,
                    stage = day.stages.firstOrNull { it.id == set.stageId }?.name ?: UNASSIGNED_STAGE,
                    time = set.timeRangeWithDuration,
                    status = TimetableSetDetail.Status.of(interval, now),
                    isScheduled = id in mySetIds,
                    overlap = overlapCalculator.setOverlaps(days, mySetIds, set, day).firstOrNull(),
                    previousTime = set.previousTime,
                    change = set.startChange,
                    imageUrl = set.imageUrl,
                    memo = set.note,
                ),
            interval = interval,
            source = source,
            isScheduleLoading = scheduleSync == TimetableScheduleSync.Loading,
            isScheduleLimitReached = isScheduleLimitReached,
        )
    }

    private fun contentStatus(selection: FestivalBoardSelection?, hasDocument: Boolean): TimetableContentStatus = when {
        selection == null -> TimetableContentStatus.NoFestival

        hasDocument && selection.document?.performances?.isEmpty() == true -> TimetableContentStatus.Unpublished

        hasDocument -> TimetableContentStatus.Published

        selection.isOnlineDetailLoading -> TimetableContentStatus.Loading

        selection.isConnectionLoading && !selection.isOnlineDetailFailed && !selection.isPackWatchFailed ->
            TimetableContentStatus.Loading

        else -> TimetableContentStatus.Failed
    }

    private fun offlineMessage(selection: FestivalBoardSelection?, hasDocument: Boolean): String? {
        if (selection?.connection != FestivalBoardSelection.Connection.Offline || !hasDocument) return null
        val checkedAt = selection.packCheckedAt ?: return "오프라인 · 저장된 정보"
        val minutes = maxOf(0L, JavaDuration.between(checkedAt, clock.instant()).toMinutes())
        return if (minutes == 0L) "오프라인 · 방금 확인한 정보" else "오프라인 · ${minutes}분 전 정보"
    }

    private val FestivalBoardSelection.isConnectionLoading: Boolean get() = connection == FestivalBoardSelection.Connection.Loading

    internal companion object {
        val CLOCK_INTERVAL: Duration = 1.seconds
        val TOAST_DURATION: Duration = 2.seconds
        private const val UNASSIGNED_STAGE = "무대 미정"
        private const val MINUTES_PER_DAY = 24 * 60

        /** 내 일정 요청 결과. 취소는 그대로 던지고, 정해진 오류 밖은 결과를 모르는 것으로 본다. */
        suspend fun attempt(block: suspend () -> FestivalSchedule): Result<FestivalSchedule> = try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: FestivalScheduleError) {
            Result.failure(error)
        } catch (_: Exception) {
            Result.failure(FestivalScheduleError.Unavailable())
        }

        /** 고른 날 0시 기준 현재 분(iOS `updateClock`). 자정을 넘는 공연이 끝날 때까지 이어진다. */
        fun TimetableState.withClock(now: Instant): TimetableState {
            val clockAt = now.truncatedTo(ChronoUnit.MINUTES)
            val day = selectedDay ?: return copy(clockAt = clockAt, nowMinute = null)
            val minute = floor(JavaDuration.between(day.date, now).toMillis() / 60_000.0).toInt()
            val end = maxOf(MINUTES_PER_DAY, day.sets.maxOfOrNull { it.end } ?: MINUTES_PER_DAY)
            return copy(clockAt = clockAt, nowMinute = minute.takeIf { it in 0 until end })
        }
    }
}
