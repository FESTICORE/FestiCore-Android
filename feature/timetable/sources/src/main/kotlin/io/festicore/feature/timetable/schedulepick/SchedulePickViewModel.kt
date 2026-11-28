package io.festicore.feature.timetable.schedulepick

import androidx.lifecycle.ViewModel
import io.festicore.domain.festival.entity.FestivalSchedule
import io.festicore.domain.festival.entity.FestivalScheduleError
import io.festicore.domain.festival.schedule.FestivalScheduleUseCase
import io.festicore.domain.festival.selection.FestivalBoardSelectionUseCase
import io.festicore.domain.timetable.FestivalPackTimetableMapper
import io.festicore.domain.timetable.TimetableOverlapCalculator
import io.festicore.feature.timetable.SchedulePickResult
import io.festicore.feature.timetable.TimetableRoute
import io.festicore.feature.timetable.main.TimetableState
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S15-1 내 일정 고르기(iOS `SchedulePickFeature`). 고른 공연을 서버 내 일정 전체로 저장하고 결과를 부르는 쪽에 돌려준다.
 * 날짜는 경로에 싣지 않고 보드 공유 팩(설계 T1)을 매퍼로 다시 옮긴다(설계 T8). 규칙은 `feature/timetable/.spec/schedule-pick-screen.md`.
 */
class SchedulePickViewModel(
    route: TimetableRoute.SchedulePick,
    boardSelectionUseCase: FestivalBoardSelectionUseCase,
    mapper: FestivalPackTimetableMapper,
    private val overlapCalculator: TimetableOverlapCalculator,
    private val scheduleUseCase: FestivalScheduleUseCase,
    private val clock: Clock = Clock.systemUTC(),
    private val packUseCase: io.festicore.domain.festival.pack.FestivalPackUseCase? = null,
    private val saveTimeoutMillis: Long? = null,
) : ViewModel(),
    OrbitContainerHost<SchedulePickState, SchedulePickState, SchedulePickSideEffect> {
    override val container: OrbitContainer<SchedulePickState, SchedulePickState, SchedulePickSideEffect> =
        orbitContainer(
            SchedulePickState(
                festivalId = route.festivalId,
                days =
                    (
                        boardSelectionUseCase.selection.value
                            ?.document
                            ?.takeIf { it.id == route.festivalId }
                            ?: packUseCase?.state?.value?.packs?.get(route.festivalId)?.document
                        )
                        ?.let { mapper.days(it, emptyList()) }
                        .orEmpty(),
                selectedSetIds = route.selectedSetIds.toSet(),
                previousClientAt = route.previousClientAtEpochMillis?.let(Instant::ofEpochMilli),
            ).withOverlaps(),
        )

    private val saveJob = AtomicReference<Job?>(null)

    // MARK: - View

    /** 날짜 세그먼트(iOS `dayTapped`). 범위 밖은 무시한다. */
    fun onDayTapped(index: Int) = intent {
        if (index !in state.days.indices) return@intent
        update { it.copy(selectedDayIndex = index) }
    }

    /** 공연 행(iOS `setTapped`). 저장 중이면 무시하고, 넣거나 빼면 안내를 지운다. 60개면 넣지 않고 한도 안내를 띄운다. */
    fun onSetTapped(setId: String) = intent {
        if (state.isSaving) return@intent
        update { current ->
            when {
                setId in current.selectedSetIds -> current.copy(selectedSetIds = current.selectedSetIds - setId, errorMessage = null)

                current.selectedSetIds.size >= TimetableState.SCHEDULE_LIMIT ->
                    current.copy(errorMessage = "내 일정은 ${TimetableState.SCHEDULE_LIMIT}개까지 고를 수 있어요.")

                else -> current.copy(selectedSetIds = current.selectedSetIds + setId, errorMessage = null)
            }
        }
    }

    /** "다음 · N개 선택"(iOS `nextTapped`). 하나 이상 골랐고 저장 중이 아닐 때만 저장한다. */
    fun onNextTapped() = intent {
        if (!state.canSubmit) return@intent
        update { it.copy(isSaving = true, errorMessage = null) }
        save()
    }

    /** "건너뛰기"(iOS `skipTapped`). */
    fun onSkipTapped() = intent {
        if (state.isSaving) return@intent
        postSideEffect(SchedulePickSideEffect.Finished(SchedulePickResult.Skipped))
    }

    /** "나중에 할게요"(iOS `laterTapped`). */
    fun onLaterTapped() = intent {
        if (state.isSaving) return@intent
        postSideEffect(SchedulePickSideEffect.Finished(SchedulePickResult.Later))
    }

    // MARK: - Async

    /** 고른 공연을 저장한다. 새로 보내면 이전 저장을 취소한다(iOS `save`, `cancelInFlight`). */
    private suspend fun save() = subIntent {
        val festivalId = state.festivalId
        val setIds = state.selectedSetIds.sorted()
        // 서버는 같은 시각의 다른 목록을 충돌로 보므로 부르는 쪽의 이전 요청보다 1ms 이상 늦춘다.
        val now = clock.instant()
        val clientAt = state.previousClientAt?.plusMillis(1)?.let { maxOf(now, it) } ?: now
        val job =
            intent {
                val result =
                    try {
                        Result.success(
                            if (saveTimeoutMillis == null) {
                                scheduleUseCase.saveMySchedule(festivalId, setIds, clientAt)
                            } else {
                                withTimeout(saveTimeoutMillis) { scheduleUseCase.saveMySchedule(festivalId, setIds, clientAt) }
                            },
                        )
                    } catch (_: TimeoutCancellationException) {
                        Result.failure(FestivalScheduleError.Unavailable())
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: FestivalScheduleError) {
                        Result.failure(error)
                    } catch (_: Exception) {
                        Result.failure(FestivalScheduleError.Unavailable())
                    }
                result.fold(onSuccess = { onSaveSucceeded(it) }, onFailure = { onSaveFailed(it) })
            }
        saveJob.getAndSet(job)?.cancel()
    }

    // MARK: - Inner

    private suspend fun onSaveSucceeded(schedule: FestivalSchedule) = subIntent {
        update { it.copy(isSaving = false, selectedSetIds = schedule.setIds.toSet()) }
        postSideEffect(SchedulePickSideEffect.Finished(SchedulePickResult.Saved(state.festivalId, state.selectedSetIds)))
    }

    private suspend fun onSaveFailed(error: Throwable) = subIntent {
        update { current ->
            if (error is FestivalScheduleError.Stale) {
                // 다른 기기에서 더 늦게 바뀐 일정이 있으면 덮어쓰지 않고 서버 일정을 보여 준다.
                current.copy(
                    isSaving = false,
                    selectedSetIds = error.schedule.setIds.toSet(),
                    errorMessage = "다른 기기에서 바뀐 일정을 불러왔어요. 확인하고 다시 눌러 주세요.",
                )
            } else {
                current.copy(isSaving = false, errorMessage = "내 일정을 저장하지 못했어요. 다시 시도해 주세요.")
            }
        }
    }

    // MARK: - 계산

    private suspend fun update(transform: (SchedulePickState) -> SchedulePickState) = subIntent {
        reduce { transform(state).withOverlaps() }
    }

    private fun SchedulePickState.withOverlaps(): SchedulePickState {
        val day = selectedDay ?: return copy(overlaps = emptyMap())
        val overlaps =
            day.sets.mapNotNull { set -> overlapCalculator.pickOverlap(day, selectedSetIds, set)?.let { set.id to it } }.toMap()
        return copy(overlaps = overlaps)
    }
}
