package io.festicore.feature.timetable

import io.festicore.domain.festival.entity.FestivalSchedule
import io.festicore.domain.festival.entity.FestivalScheduleError
import io.festicore.domain.festival.entity.FestivalScheduleSaved
import io.festicore.feature.timetable.main.TimetableContentStatus
import io.festicore.feature.timetable.main.TimetableScheduleSave
import io.festicore.feature.timetable.main.TimetableScheduleSync
import io.festicore.feature.timetable.main.TimetableState
import io.festicore.feature.timetable.main.TimetableViewModel
import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.AnalyticsTimetableAction
import io.festicore.service.analytics.event.TimetableActionData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * 내 일정 서버 동기화. iOS `TimetableScheduleTests`의 읽기 · 저장 · 불확실 · 충돌 · 계정 · 축제 · 팩 갱신 사례를 같은 이름으로 옮긴다
 * (시트 · 토스트 사례는 `TimetableViewModelTest`). iOS는 진행 중 저장을 상태 값과 가짜 응답 액션으로 꾸미지만,
 * Orbit은 내부 결과 함수를 밖에서 부를 수 없어 실제로 저장을 보내고 [CompletableDeferred]로 응답을 붙잡아 같은 상황을 만든다.
 */
class TimetableScheduleSyncTest {
    private val analytics = RecordingAnalyticsUseCase()
    private val clock = MutableClock(BEFORE_FESTIVAL)
    private val sleeper = TestSleeper()
    private val boardSelection = FakeBoardSelectionUseCase()
    private val user = FakeCurrentUserProvider()
    private val mapper = FakeMapper()
    private var schedule = FakeScheduleUseCase()

    private fun viewModel() =
        TimetableViewModel(boardSelection, schedule, user, mapper, FakeOverlapCalculator(), analytics, clock, sleeper::sleep)

    private fun readyState(mine: Set<String> = emptySet()): TimetableState =
        sample().copy(festivalId = "fst", mySetIds = mine, scheduleSync = TimetableScheduleSync.Ready).derived()

    /** 현재 상태로 계산한 내용으로 상세 시트를 연다(iOS `opening`). */
    private suspend fun TestScope.opened(state: TimetableState, id: String): TimetableState {
        var opened = state
        viewModel().test(this, initialState = state) {
            containerHost.onSetClicked(id)
            opened = awaitState()
        }
        return opened
    }

    /** 저장마다 응답을 붙잡는 대역. [index]번째 저장은 그 대기가 풀릴 때 [result]로 끝난다. */
    private class HeldSaves(count: Int) {
        val gates = List(count) { CompletableDeferred<Unit>() }
        private var calls = 0

        suspend fun replace(
            setIds: List<String>,
            result: (Int, List<String>) -> FestivalSchedule = { _, ids ->
                FestivalSchedule(ids)
            },
        ): FestivalSchedule {
            val index = calls++
            gates.getOrNull(index)?.await()
            return result(index, setIds)
        }
    }

    @Test
    fun festivalChangeLoadsScheduleOnceAndDropsUnknownIDs() = runTest {
        schedule = FakeScheduleUseCase(listOf(Result.success(FestivalSchedule(listOf("lime", "gone")))))
        viewModel().test(this, initialState = sample()) {
            containerHost.onFestivalChanged("fst")
            expectState {
                copy(festivalId = "fst", mySetIds = emptySet(), scheduleSync = TimetableScheduleSync.Loading, loadRequestId = 1).derived()
            }
            expectState { copy(mySetIds = setOf("lime"), scheduleSync = TimetableScheduleSync.Ready).derived() }
        }
        assertEquals(1, schedule.fetchCount)
    }

    @Test
    fun appearingWhileLoadingDoesNotLoadAgain() = runTest {
        viewModel().test(this, initialState = readyState().copy(scheduleSync = TimetableScheduleSync.Loading)) {
            containerHost.onTimetableAppeared()
            expectNoItems()
        }
        assertEquals(0, schedule.fetchCount)
    }

    @Test
    fun togglingScheduleTracksAnalyticsEvent() = runTest {
        viewModel().test(this, initialState = opened(readyState(setOf("lime")), "lime")) {
            containerHost.onScheduleToggled()
            assertTrue(awaitState().isSaving)
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(
            listOf(AnalyticsEvent.TimetableAction(TimetableActionData(AnalyticsTimetableAction.ScheduleToggle, "fst", setId = "lime"))),
            analytics.events,
        )
    }

    @Test
    fun togglingWhileLoadingIsIgnored() = runTest {
        val initial = opened(readyState().copy(scheduleSync = TimetableScheduleSync.Loading), "lime")
        viewModel().test(this, initialState = initial) {
            containerHost.onScheduleToggled()
            expectNoItems()
        }
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun toggleSavesLatestSnapshotAfterInFlightSave() = runTest {
        val held = HeldSaves(1)
        schedule = FakeScheduleUseCase(replace = { _, ids, _ -> held.replace(ids) })
        viewModel().test(this, initialState = opened(readyState(setOf("lime")), "lime")) {
            // 빼면 빈 목록 저장이 나가 응답을 기다린다.
            containerHost.onScheduleToggled()
            val inFlight = awaitState()
            assertTrue(inFlight.isSaving)
            assertEquals(BEFORE_FESTIVAL, inFlight.lastClientAt)
            // 저장 중에 다시 넣으면 끝난 뒤 저장할 것만 표시한다.
            containerHost.onScheduleToggled()
            val pending = awaitState()
            assertEquals(setOf("lime"), pending.mySetIds)
            assertTrue(pending.needsSave)
            assertEquals(true, pending.setDetail?.content?.detail?.isScheduled)
            assertEquals(true, awaitState().setDetail?.isScheduleToastVisible)

            held.gates[0].complete(Unit)
            val resent = awaitState()
            assertFalse(resent.needsSave)
            assertTrue(resent.isSaving)
            assertEquals(BEFORE_FESTIVAL.plusMillis(1), resent.lastClientAt)
            assertFalse(awaitState().isSaving)
            // 같은 시각에 이어진 저장도 서버 충돌을 피하도록 1ms 늦춘다.
            assertEquals(
                listOf(
                    FakeScheduleUseCase.Replaced("fst", emptyList(), BEFORE_FESTIVAL),
                    FakeScheduleUseCase.Replaced("fst", listOf("lime"), BEFORE_FESTIVAL.plusMillis(1)),
                ),
                schedule.replaced,
            )
            assertEquals(listOf(saved(), saved("lime")), schedule.notified)
            sleeper.release(TimetableViewModel.TOAST_DURATION)
            assertEquals(false, awaitState().setDetail?.isScheduleToastVisible)
        }
    }

    @Test
    fun staleSaveAdoptsServerScheduleAndDropsPendingChange() = runTest {
        val held = HeldSaves(1)
        schedule =
            FakeScheduleUseCase(replace = { _, ids, _ ->
                held.replace(ids) { _, _ -> throw FestivalScheduleError.Stale(FestivalSchedule(listOf("noise", "gone"))) }
            })
        viewModel().test(this, initialState = opened(readyState(), "lime")) {
            containerHost.onScheduleToggled()
            assertTrue(awaitState().isSaving)
            assertEquals(true, awaitState().setDetail?.isScheduleToastVisible)
            // 응답 전에 다시 빼 대기 저장을 만든다.
            containerHost.onScheduleToggled()
            val pending = awaitState()
            assertTrue(pending.needsSave)
            assertTrue(pending.mySetIds.isEmpty())

            held.gates[0].complete(Unit)
            val adopted = awaitState()
            assertFalse(adopted.isSaving)
            assertFalse(adopted.needsSave)
            assertEquals(setOf("noise"), adopted.mySetIds)
            assertEquals(listOf(saved("noise")), schedule.notified)
            // 대기 저장은 버린다.
            assertEquals(1, schedule.replaced.size)
        }
    }

    @Test
    fun unauthorizedLoadKeepsScheduleOnDevice() = runTest {
        schedule = FakeScheduleUseCase(listOf(Result.failure(FestivalScheduleError.Unauthorized())))
        viewModel().test(this, initialState = sample()) {
            containerHost.onFestivalChanged("fst")
            expectState {
                copy(festivalId = "fst", mySetIds = emptySet(), scheduleSync = TimetableScheduleSync.Loading, loadRequestId = 1).derived()
            }
            expectState { copy(scheduleSync = TimetableScheduleSync.LocalOnly) }
            containerHost.onSetClicked("lime")
            assertEquals("lime", awaitState().setDetail?.setId)
            containerHost.onScheduleToggled()
            assertEquals(setOf("lime"), awaitState().mySetIds)
            assertEquals(true, awaitState().setDetail?.isScheduleToastVisible)
            sleeper.release(TimetableViewModel.TOAST_DURATION)
            assertEquals(false, awaitState().setDetail?.isScheduleToastVisible)
        }
        assertTrue(schedule.replaced.isEmpty())
    }

    @Test
    fun rejectedSaveReloadsServerSchedule() = runTest {
        schedule =
            FakeScheduleUseCase(
                listOf(Result.success(FestivalSchedule(listOf("noise")))),
                replace = { _, _, _ -> throw FestivalScheduleError.Rejected() },
            )
        viewModel().test(this, initialState = readyState(setOf("lime", "gone"))) {
            containerHost.onPackUpdated()
            expectState { copy(mySetIds = setOf("lime"), isSaving = true, lastClientAt = BEFORE_FESTIVAL).derived() }
            expectState { copy(isSaving = false, scheduleSync = TimetableScheduleSync.Loading, loadRequestId = 1) }
            expectState { copy(mySetIds = setOf("noise"), scheduleSync = TimetableScheduleSync.Ready).derived() }
        }
    }

    @Test
    fun uncertainSaveIsResentAsTheSameRequest() = runTest {
        var attempts = 0
        schedule =
            FakeScheduleUseCase(replace = { _, ids, _ ->
                if (attempts++ == 0) throw FestivalScheduleError.Unavailable()
                FestivalSchedule(ids)
            })
        val save = TimetableScheduleSave(listOf("lime"), BEFORE_FESTIVAL, 0)
        viewModel().test(this, initialState = readyState(setOf("lime", "gone"))) {
            containerHost.onPackUpdated()
            expectState { copy(mySetIds = setOf("lime"), isSaving = true, lastClientAt = BEFORE_FESTIVAL).derived() }
            expectState { copy(isSaving = false, uncertainSave = save) }
            clock.now = BEFORE_FESTIVAL.plusSeconds(60)
            containerHost.onTimetableAppeared()
            expectState { copy(uncertainSave = null, isSaving = true) }
            expectState { copy(isSaving = false) }
        }
        assertEquals(listOf(saved("lime")), schedule.notified)
        // 결과를 모르는 요청과 같은 시각 · 목록으로 다시 보낸다.
        assertEquals(List(2) { FakeScheduleUseCase.Replaced("fst", save.setIds, save.clientAt) }, schedule.replaced)
    }

    @Test
    fun uncertainSaveWithChangedScheduleReadsServerFirst() = runTest {
        schedule = FakeScheduleUseCase(listOf(Result.success(FestivalSchedule(listOf("encore")))))
        val initial = readyState(setOf("lime", "noise")).copy(uncertainSave = TimetableScheduleSave(listOf("lime"), BEFORE_FESTIVAL, 0))
        viewModel().test(this, initialState = initial) {
            containerHost.onTimetableAppeared()
            expectState { copy(uncertainSave = null, scheduleSync = TimetableScheduleSync.Loading, loadRequestId = 1) }
            expectState { copy(mySetIds = setOf("encore"), scheduleSync = TimetableScheduleSync.Ready).derived() }
        }
        assertTrue(schedule.replaced.isEmpty())
    }

    @Test
    fun packUpdateRemovesDeletedSetsAndSaves() = runTest {
        viewModel().test(this, initialState = readyState(setOf("lime", "gone"))) {
            containerHost.onPackUpdated()
            expectState { copy(mySetIds = setOf("lime"), isSaving = true, lastClientAt = BEFORE_FESTIVAL).derived() }
            expectState { copy(isSaving = false) }
        }
        assertEquals(listOf(saved("lime")), schedule.notified)
        assertEquals(listOf(listOf("lime")), schedule.replaced.map { it.setIds })
    }

    @Test
    fun lateResultFromPreviousFestivalIsIgnored() = runTest {
        val late = CompletableDeferred<FestivalSchedule>()
        var calls = 0
        schedule = FakeScheduleUseCase(fetch = { if (calls++ == 0) late.await() else FestivalSchedule(emptyList()) })
        viewModel().test(this, initialState = sample()) {
            containerHost.onFestivalChanged("fst")
            expectState {
                copy(festivalId = "fst", mySetIds = emptySet(), scheduleSync = TimetableScheduleSync.Loading, loadRequestId = 1).derived()
            }
            containerHost.onFestivalChanged("next")
            expectState { copy(festivalId = "next", loadRequestId = 2) }
            expectState { copy(scheduleSync = TimetableScheduleSync.Ready) }
            late.complete(FestivalSchedule(listOf("lime")))
            expectNoItems()
        }
    }

    @Test
    fun accountChangeClearsScheduleAndDropsPreviousLoad() = runTest {
        schedule = FakeScheduleUseCase(listOf(Result.failure(FestivalScheduleError.Unauthorized())))
        val initial =
            opened(readyState(setOf("lime")), "lime").copy(
                scheduleSync = TimetableScheduleSync.Loading,
                loadRequestId = 3,
                isSaving = true,
                needsSave = true,
                isScheduleLimitReached = true,
                lastClientAt = BEFORE_FESTIVAL,
                uncertainSave = TimetableScheduleSave(listOf("lime"), BEFORE_FESTIVAL, 0),
            )
        viewModel().test(this, initialState = initial) {
            containerHost.onAccountChanged()
            val cleared = awaitState()
            assertTrue(cleared.mySetIds.isEmpty())
            assertEquals(TimetableScheduleSync.Loading, cleared.scheduleSync)
            assertFalse(cleared.isScheduleLimitReached)
            assertEquals(5, cleared.loadRequestId)
            assertEquals(1, cleared.accountGeneration)
            assertFalse(cleared.isSaving)
            assertFalse(cleared.needsSave)
            assertNull(cleared.lastClientAt)
            assertNull(cleared.uncertainSave)
            assertEquals(false, cleared.setDetail?.content?.detail?.isScheduled)
            assertEquals(false, cleared.setDetail?.content?.isScheduleLimitReached)
            val loaded = awaitState()
            assertEquals(TimetableScheduleSync.LocalOnly, loaded.scheduleSync)
            assertEquals(false, loaded.setDetail?.content?.isScheduleLoading)
            // 시트는 상위가 닫는다(iOS `clearAccountDisplayData`). 이 입력만으로는 그대로다.
            assertEquals("lime", loaded.setDetail?.setId)
        }
        assertEquals(1, schedule.fetchCount)
    }

    /** iOS `MainTabCoordinator` 계정 입력(`MTC:488-503 · 527-536`). Android는 타임테이블이 현재 사용자를 직접 본다(설계 "계정 변경 입력"). */
    @Test
    fun currentUserChangeClearsSheetAndReloadsButLoginDoesNot() = runTest {
        user.currentUserId.value = "usr_1"
        schedule = FakeScheduleUseCase(listOf(Result.success(FestivalSchedule(listOf("noise")))))
        // 보드 공유 상태가 지금 화면과 같은 값을 갖게 해 만들 때 바뀌는 것이 없게 한다(문서에 공연 · 출처가 없다).
        val initial = opened(readyState(setOf("lime")).copy(source = "", contentStatus = TimetableContentStatus.Unpublished), "lime")
        mapper.days = mapOf("fst" to initial.days)
        boardSelection.selection.value = selection("fst")
        viewModel().test(this, initialState = initial) {
            runOnCreate()
            user.currentUserId.value = "usr_2"
            val cleared = awaitState()
            assertNull(cleared.setDetail)
            assertTrue(cleared.mySetIds.isEmpty())
            assertEquals(1, cleared.accountGeneration)
            assertEquals(setOf("noise"), awaitState().mySetIds)
            // 로그아웃도 계정 변경이다.
            user.currentUserId.value = null
            assertEquals(2, awaitState().accountGeneration)
            assertEquals(TimetableScheduleSync.Ready, awaitState().scheduleSync)
            // 로그인(없음 → 있음)은 다음 진입이 다시 읽는다.
            user.currentUserId.value = "usr_3"
            expectNoItems()
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(2, schedule.fetchCount)
    }

    @Test
    fun previousAccountSaveCannotOverwriteCurrentSave() = runTest {
        val held = HeldSaves(2)
        schedule = FakeScheduleUseCase(replace = { _, ids, _ -> held.replace(ids) })
        viewModel().test(this, initialState = opened(readyState(setOf("lime", "gone")), "noise")) {
            // 이전 계정의 저장이 응답을 기다린다.
            containerHost.onPackUpdated()
            assertTrue(awaitState().isSaving)
            containerHost.onAccountChanged()
            val changed = awaitState()
            assertTrue(changed.mySetIds.isEmpty())
            assertEquals(1, changed.accountGeneration)
            assertFalse(changed.isSaving)
            assertEquals(TimetableScheduleSync.Ready, awaitState().scheduleSync)
            containerHost.onScheduleToggled()
            val current = awaitState()
            assertEquals(setOf("noise"), current.mySetIds)
            assertTrue(current.isSaving)
            assertEquals(true, awaitState().setDetail?.isScheduleToastVisible)
            // 이전 계정 저장의 응답이 늦게 와도 목록 · 저장 중 표시가 바뀌지 않는다.
            held.gates[0].complete(Unit)
            expectNoItems()
            held.gates[1].complete(Unit)
            val saved = awaitState()
            assertFalse(saved.isSaving)
            assertEquals(setOf("noise"), saved.mySetIds)
            sleeper.release(TimetableViewModel.TOAST_DURATION)
            assertEquals(false, awaitState().setDetail?.isScheduleToastVisible)
        }
        assertEquals(listOf(saved("noise")), schedule.notified)
        assertEquals(listOf(listOf("lime"), listOf("noise")), schedule.replaced.map { it.setIds })
    }

    @Test
    fun successfulSaveDelegatesSavedSnapshot() = runTest {
        val held = HeldSaves(1)
        schedule = FakeScheduleUseCase(replace = { _, ids, _ -> held.replace(ids) })
        viewModel().test(this, initialState = opened(readyState(setOf("lime", "gone")), "noise")) {
            containerHost.onPackUpdated()
            assertTrue(awaitState().isSaving)
            containerHost.onScheduleToggled()
            val pending = awaitState()
            assertEquals(setOf("lime", "noise"), pending.mySetIds)
            assertTrue(pending.needsSave)
            assertEquals(true, awaitState().setDetail?.isScheduleToastVisible)
            held.gates[0].complete(Unit)
            assertFalse(awaitState().needsSave)
            assertFalse(awaitState().isSaving)
            cancelAndIgnoreRemainingItems()
        }
        // 보낸 목록 그대로 알리고, 대기 목록을 이어 저장한 뒤 그 목록을 알린다.
        assertEquals(listOf(saved("lime"), saved("lime", "noise")), schedule.notified)
    }

    @Test
    fun staleSaveRemovingAddedSetHidesToast() = runTest {
        schedule = FakeScheduleUseCase(replace = { _, _, _ -> throw FestivalScheduleError.Stale(FestivalSchedule(emptyList())) })
        viewModel().test(this, initialState = opened(readyState(), "lime")) {
            containerHost.onScheduleToggled()
            val added = awaitState()
            assertEquals(setOf("lime"), added.mySetIds)
            assertTrue(added.isSaving)
            assertEquals(BEFORE_FESTIVAL, added.lastClientAt)
            assertEquals(true, added.setDetail?.content?.detail?.isScheduled)
            assertEquals(true, awaitState().setDetail?.isScheduleToastVisible)
            val adopted = awaitState()
            assertFalse(adopted.isSaving)
            assertTrue(adopted.mySetIds.isEmpty())
            assertEquals(false, adopted.setDetail?.content?.detail?.isScheduled)
            assertEquals(false, adopted.setDetail?.isScheduleToastVisible)
            sleeper.release(TimetableViewModel.TOAST_DURATION)
            expectNoItems()
        }
        assertEquals(listOf(saved()), schedule.notified)
    }

    @Test
    fun reloadAfterRejectedSaveRemovingAddedSetHidesToast() = runTest {
        schedule =
            FakeScheduleUseCase(
                listOf(Result.success(FestivalSchedule(emptyList()))),
                replace = { _, _, _ -> throw FestivalScheduleError.Rejected() },
            )
        viewModel().test(this, initialState = opened(readyState(), "lime")) {
            containerHost.onScheduleToggled()
            assertTrue(awaitState().isSaving)
            assertEquals(true, awaitState().setDetail?.isScheduleToastVisible)
            val reloading = awaitState()
            assertFalse(reloading.isSaving)
            assertEquals(TimetableScheduleSync.Loading, reloading.scheduleSync)
            assertEquals(1, reloading.loadRequestId)
            assertEquals(true, reloading.setDetail?.content?.isScheduleLoading)
            val loaded = awaitState()
            assertTrue(loaded.mySetIds.isEmpty())
            assertEquals(TimetableScheduleSync.Ready, loaded.scheduleSync)
            assertEquals(false, loaded.setDetail?.content?.detail?.isScheduled)
            assertEquals(false, loaded.setDetail?.content?.isScheduleLoading)
            assertEquals(false, loaded.setDetail?.isScheduleToastVisible)
            sleeper.release(TimetableViewModel.TOAST_DURATION)
            expectNoItems()
        }
    }

    private fun saved(vararg ids: String) = FestivalScheduleSaved("fst", ids.toSet())
}
