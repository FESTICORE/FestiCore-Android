package io.festicore.feature.timetable.main

import androidx.lifecycle.ViewModel
import io.festicore.domain.festival.FestivalBoardSelection
import io.festicore.domain.festival.FestivalBoardSelectionUseCase
import io.festicore.domain.timetable.FestivalPackTimetableMapper
import io.festicore.domain.timetable.TimetableDay
import io.festicore.domain.timetable.TimetableLineupDay
import io.festicore.domain.timetable.TimetableOverlapCalculator
import io.festicore.domain.timetable.TimetableSetDetail
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S02 타임테이블(iOS `TimetableFeature`). 라이브 보드가 공유 상태에 쓴 선택 축제 · 표시 문서(설계 T1)를 읽어
 * 날짜별 격자 · 공개 전 라인업 · 출처 · 오프라인 안내로 옮기고, 공연 상세 시트 상태까지 가진다(설계 T7).
 * 규칙은 `feature/timetable/.spec/timetable-screen.md`. 내 일정 서버 읽기 · 저장은 동기화 PR(S2)에서 붙는다.
 */
class TimetableViewModel(
    private val boardSelectionUseCase: FestivalBoardSelectionUseCase,
    private val mapper: FestivalPackTimetableMapper,
    private val overlapCalculator: TimetableOverlapCalculator,
    private val clock: Clock = Clock.systemUTC(),
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) : ViewModel(),
    OrbitContainerHost<TimetableState, TimetableState, TimetableSideEffect> {
    override val container: OrbitContainer<TimetableState, TimetableState, TimetableSideEffect> =
        orbitContainer(TimetableState()) {
            boardSelectionUseCase.selection.collect { selectionChanged(it) }
        }

    private val clockJob = AtomicReference<Job?>(null)
    private val toastJob = AtomicReference<Job?>(null)

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

    fun onSchedulePickClicked() = intent {
        val festivalId = state.festivalId
        if (!state.canPickSchedule || festivalId == null) return@intent
        postSideEffect(TimetableSideEffect.SchedulePick(festivalId, state.mySetIds))
    }

    // MARK: - Scope (공연 상세 시트)

    /**
     * 시트의 "내 일정에 넣기" 토글(iOS `toggleSchedule`). 서버 일정을 읽는 중이면 무시한다.
     * 빼면 한도 안내를 끄고, 넣을 때는 끝난 공연을 거절하고 60개면 한도 안내를 띄운다. 넣으면 토스트를 2초 보인다.
     */
    fun onScheduleToggled() = intent {
        val id = state.setDetail?.setId ?: return@intent
        if (state.scheduleSync == TimetableScheduleSync.Loading) return@intent
        if (id in state.mySetIds) {
            update { it.copy(mySetIds = it.mySetIds - id, isScheduleLimitReached = false).withOverlapWarning() }
            return@intent
        }
        if (state.setDetailContent(id, clock.instant())?.detail?.status == TimetableSetDetail.Status.Ended) return@intent
        if (state.mySetIds.size >= TimetableState.SCHEDULE_LIMIT) {
            update { it.copy(isScheduleLimitReached = true) }
            return@intent
        }
        update { it.copy(mySetIds = it.mySetIds + id).withOverlapWarning() }
        showScheduleToast(id)
    }

    /** 시트를 닫았다(iOS `setDetail.dismiss`). */
    fun onSetDetailDismissed() = intent {
        update { it.copy(setDetail = null, isScheduleLimitReached = false) }
    }

    // MARK: - Async

    /** 토스트를 2초 보인다. 다시 넣으면 처음부터 센다(iOS `showScheduleToast`). */
    private suspend fun showScheduleToast(setId: String) = subIntent {
        update { current -> current.copy(setDetail = current.setDetail?.copy(isScheduleToastVisible = true)) }
        val job =
            intent {
                sleep(TOAST_DURATION)
                onScheduleToastExpired(setId)
            }
        toastJob.getAndSet(job)?.cancel()
    }

    // MARK: - Inner

    private suspend fun onClockUpdated(now: Instant) = subIntent {
        update { it.withClock(now) }
    }

    private suspend fun onScheduleToastExpired(setId: String) = subIntent {
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

    /** iOS `festivalChanged`. 같으면 무시하고, 바뀌면 시트를 닫고 내 일정을 비운다. */
    internal fun onFestivalChanged(festivalId: String?) = intent { festivalChanged(festivalId) }

    /** iOS `packUpdated`. 시각을 다시 계산하고, 팩에서 사라진 공연을 내 일정 · 시트에서 뺀다. */
    internal fun onPackUpdated() = intent { packUpdated() }

    /** iOS `mySetIDsChanged`. 다른 화면에서 저장한 같은 축제의 내 일정. */
    internal fun onMySetIdsChanged(setIds: Set<String>) = intent {
        update { it.copy(mySetIds = setIds) }
    }

    private suspend fun selectionChanged(selection: FestivalBoardSelection?) = subIntent {
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

    private suspend fun festivalChanged(festivalId: String?) = subIntent {
        if (festivalId == state.festivalId) return@subIntent
        update {
            it.copy(
                festivalId = festivalId,
                setDetail = null,
                mySetIds = emptySet(),
                shouldShowOverlapWarning = false,
                scheduleSync = TimetableScheduleSync.Idle,
                isScheduleLimitReached = false,
            )
        }
    }

    private suspend fun packUpdated() = subIntent {
        update { current ->
            val clocked = current.clockAt?.let { current.withClock(it) } ?: current
            val validIds = clocked.allSetIds
            val detail = clocked.setDetail?.takeIf { it.setId in validIds }
            val trimmed = clocked.mySetIds.intersect(validIds)
            val warning = if (trimmed != clocked.mySetIds) false else clocked.shouldShowOverlapWarning
            clocked.copy(setDetail = detail, mySetIds = trimmed, shouldShowOverlapWarning = warning)
        }
    }

    // MARK: - 계산

    /** 상태를 바꾸고 겹침 · 열린 시트 내용을 다시 계산한다(iOS `refreshSetDetail`). 공연을 빼면 토스트를 바로 숨긴다. */
    private suspend fun update(transform: (TimetableState) -> TimetableState) = subIntent {
        reduce { transform(state).refreshed(clock.instant()) }
        if (state.setDetail?.isScheduleToastVisible != true) toastJob.getAndSet(null)?.cancel()
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
