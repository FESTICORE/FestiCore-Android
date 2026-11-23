package io.festicore.feature.timetable

import io.festicore.domain.festival.entity.FestivalSchedule
import io.festicore.domain.festival.entity.FestivalScheduleError
import io.festicore.domain.festival.entity.FestivalScheduleSaved
import io.festicore.feature.timetable.main.TimetableScheduleSave
import io.festicore.feature.timetable.main.TimetableScheduleSync
import io.festicore.feature.timetable.main.TimetableSideEffect
import io.festicore.feature.timetable.main.TimetableState
import io.festicore.feature.timetable.main.TimetableViewModel
import io.festicore.feature.timetable.schedulepick.SchedulePickSideEffect
import io.festicore.feature.timetable.schedulepick.SchedulePickViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * 타임테이블 ↔ 내 일정 고르기. iOS `TimetableSchedulePickTests` 13건을 같은 이름으로 옮긴다.
 * iOS는 고르기가 타임테이블 상태(`destination`) 안에 있지만 Android는 Coordinator 경로 + 별도 ViewModel이라(설계 T8),
 * 고르기 ViewModel이 낸 결과를 타임테이블 `onSchedulePickResult`로 넘겨 같은 흐름을 확인한다.
 */
class TimetableSchedulePickTest {
    private val clock = MutableClock(BEFORE_FESTIVAL)
    private val boardSelection = FakeBoardSelectionUseCase(selection("fst"))
    private val mapper = FakeMapper(days = mapOf("fst" to sample().days))
    private var schedule = FakeScheduleUseCase(fetch = { error("이 테스트는 서버 일정을 읽지 않아야 한다") })

    private fun viewModel() = TimetableViewModel(
        boardSelection,
        schedule,
        FakeCurrentUserProvider(),
        mapper,
        FakeOverlapCalculator(),
        clock,
        TestSleeper()::sleep,
    )

    private fun pickViewModel(route: TimetableRoute.SchedulePick) =
        SchedulePickViewModel(route, boardSelection, mapper, FakeOverlapCalculator(), schedule, clock)

    private fun readyState(mine: Set<String> = emptySet()): TimetableState =
        sample().copy(festivalId = "fst", mySetIds = mine, scheduleSync = TimetableScheduleSync.Ready).derived()

    /** 고르기가 열린 상태(iOS `opened`). 경로는 타임테이블이 여는 값과 같다. */
    private fun opened(state: TimetableState): Pair<TimetableState, TimetableRoute.SchedulePick> =
        state.copy(isPickOpen = true) to TimetableRoute.SchedulePick("fst", state.mySetIds.sorted(), state.lastClientAt?.toEpochMilli())

    /** 고르기에서 [setIds]를 고르고 "다음"을 눌러 받은 결과. */
    private suspend fun TestScope.pickAndSave(route: TimetableRoute.SchedulePick, vararg setIds: String): SchedulePickResult {
        var result: SchedulePickResult = SchedulePickResult.Skipped
        pickViewModel(route).test(this) {
            setIds.forEach {
                containerHost.onSetTapped(it)
                awaitState()
            }
            containerHost.onNextTapped()
            assertTrue(awaitState().isSaving)
            awaitState()
            result = (awaitSideEffect() as SchedulePickSideEffect.Finished).result
        }
        return result
    }

    @Test
    fun tappingButtonOpensSchedulePickWithMySchedule() = runTest {
        viewModel().test(this, initialState = readyState(setOf("lime"))) {
            containerHost.onSchedulePickClicked()
            expectState { copy(isPickOpen = true) }
            expectSideEffect(TimetableSideEffect.SchedulePick(TimetableRoute.SchedulePick("fst", listOf("lime"), null)))
        }
        // 열린 고르기는 지금 내 일정 · 공유 팩 날짜로 시작한다.
        val pick = pickViewModel(TimetableRoute.SchedulePick("fst", listOf("lime"), null)).container.stateFlow.value
        assertEquals(setOf("lime"), pick.selectedSetIds)
        assertEquals(sample().days, pick.days)
        assertTrue(schedule.replaced.isEmpty())
    }

    @Test
    fun tappingButtonWhileLoadingScheduleDoesNotOpen() = runTest {
        viewModel().test(this, initialState = readyState().copy(scheduleSync = TimetableScheduleSync.Loading)) {
            containerHost.onSchedulePickClicked()
            expectNoItems()
        }
    }

    @Test
    fun tappingButtonWhileSavingOrLocalOnlyDoesNotOpen() = runTest {
        for (state in listOf(readyState().copy(isSaving = true), readyState().copy(scheduleSync = TimetableScheduleSync.LocalOnly))) {
            viewModel().test(this, initialState = state) {
                containerHost.onSchedulePickClicked()
                expectNoItems()
            }
        }
    }

    @Test
    fun tappingButtonTwiceKeepsFirstPick() = runTest {
        viewModel().test(this, initialState = readyState()) {
            containerHost.onSchedulePickClicked()
            expectState { copy(isPickOpen = true) }
            expectSideEffect(TimetableSideEffect.SchedulePick(TimetableRoute.SchedulePick("fst", emptyList(), null)))
            containerHost.onSchedulePickClicked()
            expectNoItems()
        }
    }

    @Test
    fun pickSaveIsLaterThanPreviousSave() = runTest {
        val previous = BEFORE_FESTIVAL.plusSeconds(60)
        val (initial, route) = opened(readyState().copy(lastClientAt = previous))
        val result = pickAndSave(route, "lime")
        assertEquals(listOf(previous.plusMillis(1)), schedule.replaced.map { it.clientAt })
        viewModel().test(this, initialState = initial) {
            containerHost.onSchedulePickResult(result)
            // 타임테이블의 다음 저장은 이 시각보다 1ms 이상 늦게 보낸다.
            assertEquals(previous.plusMillis(1), awaitState().lastClientAt)
        }
    }

    @Test
    fun lateTimetableSaveAfterPickSaveIsIgnored() = runTest {
        val late = CompletableDeferred<Unit>()
        var calls = 0
        schedule =
            FakeScheduleUseCase(replace = { _, ids, _ ->
                if (calls++ == 0) late.await()
                FestivalSchedule(ids)
            })
        // 고르기를 연 뒤 타임테이블 저장이 응답을 기다리고, 그 사이 목록이 또 바뀌었다.
        val (initial, route) = opened(readyState(setOf("noise")))
        viewModel().test(this, initialState = initial) {
            containerHost.onSetClicked("noise")
            awaitState()
            containerHost.onScheduleToggled()
            assertTrue(awaitState().isSaving)
            containerHost.onScheduleToggled()
            assertTrue(awaitState().needsSave)
            awaitState()
            val result = pickAndSave(route.copy(selectedSetIds = emptyList()), "lime")
            containerHost.onSchedulePickResult(result)
            val saved = awaitState()
            assertFalse(saved.isPickOpen)
            assertEquals(setOf("lime"), saved.mySetIds)
            assertFalse(saved.isSaving)
            assertFalse(saved.needsSave)
            assertEquals(BEFORE_FESTIVAL.plusMillis(1), saved.lastClientAt)
            // 고르기 전에 보낸 타임테이블 저장의 응답이 늦게 와도 목록 · 알림이 바뀌지 않는다.
            late.complete(Unit)
            cancelAndIgnoreRemainingItems()
            assertEquals(setOf("lime"), containerHost.container.stateFlow.value.mySetIds)
        }
        assertEquals(listOf(FestivalScheduleSaved("fst", setOf("lime"))), schedule.notified)
    }

    @Test
    fun packUpdateWhilePickIsOpenClosesIt() = runTest {
        viewModel().test(this, initialState = opened(readyState(setOf("lime"))).first) {
            containerHost.onPackUpdated()
            expectState { copy(isPickOpen = false) }
            expectSideEffect(TimetableSideEffect.CloseSchedulePick)
        }
        assertTrue(schedule.replaced.isEmpty())
    }

    @Test
    fun appearingWhilePickIsOpenDoesNotReloadOrSave() = runTest {
        val state = readyState().copy(uncertainSave = TimetableScheduleSave(emptyList(), BEFORE_FESTIVAL, 0))
        viewModel().test(this, initialState = opened(state).first) {
            containerHost.onTimetableAppeared()
            expectNoItems()
        }
        assertTrue(schedule.replaced.isEmpty())
    }

    @Test
    fun accountOrFestivalChangeWhilePickIsSavingDropsResponse() = runTest {
        for (change in listOf<(TimetableViewModel) -> Unit>({ it.onAccountChanged() }, { it.onFestivalChanged("next") })) {
            schedule = FakeScheduleUseCase()
            val (initial, route) = opened(readyState())
            viewModel().test(this, initialState = initial) {
                change(containerHost)
                assertFalse(awaitState().isPickOpen)
                expectSideEffect(TimetableSideEffect.CloseSchedulePick)
                assertEquals(TimetableScheduleSync.Ready, awaitState().scheduleSync)
                // 닫힌 고르기의 저장 결과가 늦게 와도 반영하지 않는다(Coordinator가 이미 경로를 뺐다).
                containerHost.onSchedulePickResult(pickAndSave(route, "lime"))
                expectNoItems()
                assertTrue(containerHost.container.stateFlow.value.mySetIds.isEmpty())
            }
            assertTrue(schedule.notified.isEmpty())
        }
    }

    @Test
    fun savingClosesPickAndRefreshesMySchedule() = runTest {
        val (initial, route) = opened(readyState())
        val result = pickAndSave(route, "lime")
        assertEquals(SchedulePickResult.Saved("fst", setOf("lime")), result)
        viewModel().test(this, initialState = initial) {
            containerHost.onSchedulePickResult(result)
            expectState { copy(isPickOpen = false, mySetIds = setOf("lime"), lastClientAt = BEFORE_FESTIVAL).derived() }
        }
        assertEquals(listOf(FakeScheduleUseCase.Replaced("fst", listOf("lime"), BEFORE_FESTIVAL)), schedule.replaced)
        assertEquals(listOf(FestivalScheduleSaved("fst", setOf("lime"))), schedule.notified)
    }

    @Test
    fun failedSaveKeepsPickOpenWithMessageAndLeavesMySchedule() = runTest {
        var attempts = 0
        schedule =
            FakeScheduleUseCase(replace = { _, _, _ ->
                attempts += 1
                throw FestivalScheduleError.Unavailable()
            })
        val (_, route) = opened(readyState(setOf("lime")))
        pickViewModel(route).test(this) {
            containerHost.onNextTapped()
            assertTrue(awaitState().isSaving)
            val failed = awaitState()
            assertFalse(failed.isSaving)
            assertEquals("내 일정을 저장하지 못했어요. 다시 시도해 주세요.", failed.errorMessage)
            // 결과를 돌려주지 않아 고르기가 열린 채 남는다.
            expectNoItems()
        }
        assertEquals(1, attempts)
    }

    @Test
    fun skippingClosesPickWithoutChangingMySchedule() = runTest {
        val (initial, route) = opened(readyState(setOf("lime")))
        var result: SchedulePickResult? = null
        pickViewModel(route).test(this) {
            containerHost.onSkipTapped()
            result = (awaitSideEffect() as SchedulePickSideEffect.Finished).result
        }
        assertEquals(SchedulePickResult.Skipped, result)
        viewModel().test(this, initialState = initial) {
            containerHost.onSchedulePickResult(SchedulePickResult.Skipped)
            expectState { copy(isPickOpen = false) }
        }
        assertTrue(schedule.replaced.isEmpty())
    }

    @Test
    fun accountChangeClosesOpenPick() = runTest {
        schedule = FakeScheduleUseCase()
        viewModel().test(this, initialState = opened(readyState()).first) {
            containerHost.onAccountChanged()
            expectState { copy(isPickOpen = false, accountGeneration = 1, loadRequestId = 2, scheduleSync = TimetableScheduleSync.Loading) }
            expectSideEffect(TimetableSideEffect.CloseSchedulePick)
            expectState { copy(scheduleSync = TimetableScheduleSync.Ready) }
        }
        assertTrue(schedule.replaced.isEmpty())
    }
}
