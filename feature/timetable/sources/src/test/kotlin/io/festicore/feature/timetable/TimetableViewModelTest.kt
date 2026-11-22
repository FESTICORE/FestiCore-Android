package io.festicore.feature.timetable

import io.festicore.domain.festival.FestivalBoardSelection
import io.festicore.domain.festival.FestivalPackDocument
import io.festicore.domain.timetable.TimetableInterval
import io.festicore.domain.timetable.TimetableOverlap
import io.festicore.domain.timetable.TimetableSet
import io.festicore.domain.timetable.TimetableSetDetail
import io.festicore.domain.timetable.TimetableSetOverlap
import io.festicore.feature.timetable.main.TimetableContentStatus
import io.festicore.feature.timetable.main.TimetableScheduleSync
import io.festicore.feature.timetable.main.TimetableSideEffect
import io.festicore.feature.timetable.main.TimetableState
import io.festicore.feature.timetable.main.TimetableViewModel
import io.festicore.feature.timetable.main.dayOffsetForSwipe
import io.festicore.feature.timetable.setdetail.TimetableSetDetailContent
import io.festicore.feature.timetable.setdetail.TimetableSetDetailState
import java.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.OrbitTestContext
import org.orbitmvi.orbit.test.test

/**
 * 타임테이블 ViewModel. iOS `TimetableFeatureTests`의 화면 · 시계 · 팩 동기화 사례와 `TimetableScheduleTests`의 시트 · 토스트 사례를 같은 이름으로 옮긴다.
 * 분석 이벤트 사례는 분석 모듈이 없어 뺀다(설계 T10). 서버 읽기 · 저장 사례는 동기화 PR(S2)에서 옮긴다.
 */
class TimetableViewModelTest {
    private val clock = MutableClock(BEFORE_FESTIVAL)
    private val sleeper = TestSleeper()
    private val boardSelection = FakeBoardSelectionUseCase()
    private val mapper = FakeMapper()

    private fun viewModel() = TimetableViewModel(boardSelection, mapper, FakeOverlapCalculator(), clock, sleeper::sleep)

    private fun minute(value: Int, day: Int = 0): Instant = FIRST_DAY.plusSeconds(day * 86_400L + value * 60L)

    /** 현재 상태로 계산한 내용으로 상세 시트를 연다(iOS `opening`). */
    private suspend fun TestScope.opened(
        state: TimetableState,
        id: String,
        now: Instant = BEFORE_FESTIVAL,
    ): TimetableState {
        clock.now = now
        var opened = state
        viewModel().test(this, initialState = state) {
            containerHost.onSetClicked(id)
            opened = awaitState()
        }
        return opened
    }

    private suspend fun TestScope.content(
        state: TimetableState,
        id: String,
        now: Instant,
    ): TimetableSetDetailContent? = opened(state, id, now).setDetail?.content

    // MARK: - 화면 · 시계 (iOS TimetableFeatureTests)

    @Test
    fun clockRefreshesOnEntryTickAndDayChangeThenCancels() = runTest {
        val initial = sample()
        clock.now = minute(928)
        viewModel().test(this, initialState = initial) {
            containerHost.onClockStarted()
            expectState { copy(clockAt = minute(928)) }
            clock.now = minute(929)
            sleeper.release(TimetableViewModel.CLOCK_INTERVAL)
            expectState { copy(clockAt = minute(929), nowMinute = 929) }
            containerHost.onDaySelected(1)
            expectState { copy(selectedDayIndex = 1, nowMinute = null, overlaps = emptyList()) }
            containerHost.onDaySelected(0)
            expectState { copy(selectedDayIndex = 0, nowMinute = 929, overlaps = initial.overlaps) }
            containerHost.onClockStopped()
            sleeper.release(TimetableViewModel.CLOCK_INTERVAL)
            expectNoItems()
        }
    }

    @Test
    fun currentTimeContinuesThroughCrossMidnightSet() = runTest {
        val base = sample()
        val day = base.days[0].copy(sets = listOf(base.days[0].sets[0].copy(end = 1500)))
        val initial = base.copy(days = listOf(day) + base.days.drop(1)).derived()
        clock.now = minute(1460)
        viewModel().test(this, initialState = initial) {
            containerHost.onClockStarted()
            expectState { copy(clockAt = minute(1460), nowMinute = 1460) }
            clock.now = minute(1500)
            sleeper.release(TimetableViewModel.CLOCK_INTERVAL)
            expectState { copy(clockAt = minute(1500), nowMinute = null) }
            containerHost.onClockStopped()
        }
    }

    @Test
    fun contentStatusDoesNotInferPublicationFromEmptyLineup() {
        val state = TimetableState(festivalId = "fst", scheduleSync = TimetableScheduleSync.Ready)
        for (status in listOf(
            TimetableContentStatus.Loading,
            TimetableContentStatus.Failed,
            TimetableContentStatus.NoFestival,
            TimetableContentStatus.Unpublished,
        )) {
            val current = state.copy(contentStatus = status)
            assertFalse(current.isPublished)
            assertFalse(current.canPickSchedule)
        }
        assertTrue(state.copy(contentStatus = TimetableContentStatus.Published).isPublished)
    }

    @Test
    fun retryAsksParentToReloadPack() = runTest {
        boardSelection.selection.value = selection("fst")
        viewModel().test(this, initialState = TimetableState()) {
            containerHost.onRetryClicked().join()
        }
        assertEquals(listOf("fst"), boardSelection.retried)
    }

    @Test
    fun outOfRangeDayIsIgnored() = runTest {
        viewModel().test(this, initialState = sample()) {
            containerHost.onDaySelected(2)
            expectState { copy(selectedDayIndex = 2, overlaps = emptyList()) }
            containerHost.onDaySelected(3)
            expectNoItems()
        }
    }

    @Test
    fun swipingPastFirstDayIsIgnored() = runTest {
        val initial = sample()
        viewModel().test(this, initialState = initial) {
            containerHost.onDaySelected(-1)
            containerHost.onDaySelected(1)
            expectState { copy(selectedDayIndex = 1, overlaps = emptyList()) }
            containerHost.onDaySelected(0)
            expectState { copy(selectedDayIndex = 0, overlaps = initial.overlaps) }
        }
    }

    @Test
    fun daySwipeNeedsMostlyHorizontalDrag() {
        assertEquals(1, dayOffsetForSwipe(-81f, 0f))
        assertEquals(-1, dayOffsetForSwipe(81f, 20f))
        assertNull(dayOffsetForSwipe(-80f, 0f))
        assertNull(dayOffsetForSwipe(-120f, 80f))
        assertNull(dayOffsetForSwipe(20f, 200f))
    }

    @Test
    fun pinchZoomIsKeptAcrossDayChanges() = runTest {
        viewModel().test(this, initialState = sample()) {
            containerHost.onZoomChanged(1.8)
            expectState { copy(zoom = 1.8) }
            containerHost.onDaySelected(1)
            expectState { copy(selectedDayIndex = 1, overlaps = emptyList()) }
            assertEquals(1.8, containerHost.container.stateFlow.value.zoom, 0.0)
        }
    }

    @Test
    fun bannerAsksParentToShowChanges() = runTest {
        viewModel().test(this, initialState = sample(SampleVariant.Changed)) {
            containerHost.onChangeBannerClicked()
            expectSideEffect(TimetableSideEffect.ShowChanges)
        }
    }

    @Test
    fun overlapsCoverOnlyMySets() = runTest {
        val sample = sample()
        viewModel().test(this, initialState = sample.copy(mySetIds = emptySet()).derived()) {
            containerHost.onMySetIdsChanged(sample.mySetIds)
            val state = awaitState()
            assertEquals(4, state.mySetCount)
            assertEquals(listOf(TimetableOverlap(955, 990)), state.overlaps)
            assertEquals(35, state.overlaps.first().minutes)
            assertTrue(state.visibleOverlaps.isEmpty())
            assertEquals(state.overlaps, state.copy(shouldShowOverlapWarning = true).visibleOverlaps)
        }
    }

    @Test
    fun changingDayHidesTheOverlapWarning() = runTest {
        val initial = sample().copy(shouldShowOverlapWarning = true).derived()
        viewModel().test(this, initialState = initial) {
            containerHost.onDaySelected(0)
            assertEquals(1, initial.visibleOverlaps.size)
            containerHost.onDaySelected(1)
            expectState {
                copy(selectedDayIndex = 1, shouldShowOverlapWarning = false, overlaps = emptyList(), overlappingSetIds = emptySet())
            }
            containerHost.onDaySelected(0)
            val back = awaitState()
            assertEquals(0, back.selectedDayIndex)
            assertEquals(1, back.overlaps.size)
            assertTrue(back.visibleOverlaps.isEmpty())
        }
    }

    @Test
    fun changedSetsAreCounted() {
        assertEquals(2, sample(SampleVariant.Changed).selectedDay?.changedCount)
        assertEquals(0, sample().selectedDay?.changedCount)
    }

    @Test
    fun unpublishedStateShowsLineup() {
        val state = sample(SampleVariant.Unpublished)
        assertFalse(state.isPublished)
        assertEquals(listOf(5, 4), state.lineup.map { it.artists.size })
    }

    @Test
    fun packSyncReplacesPackValuesAndKeepsSelectedDayByID() = runTest {
        val initial = sample().copy(selectedDayIndex = 1).derived()
        val days = initial.days.reversed()
        val lineup = sample(SampleVariant.Unpublished).lineup
        viewModel().test(this, initialState = initial) {
            containerHost.onPackSynced(days, lineup, "출처: 새 팩", TimetableContentStatus.Unpublished, "오프라인 · 저장된 정보")
            // 0808은 뒤집은 목록에서도 가운데라 인덱스가 1로 남는다.
            expectState {
                copy(
                    days = days,
                    selectedDayIndex = 1,
                    lineup = lineup,
                    source = "출처: 새 팩",
                    contentStatus = TimetableContentStatus.Unpublished,
                    offlineMessage = "오프라인 · 저장된 정보",
                )
            }
            containerHost.onDaySelected(0)
            expectState { copy(selectedDayIndex = 0) }
            // 고른 날짜(0809)가 원래 순서에서는 마지막이라 인덱스를 따라 옮긴다.
            containerHost.onPackSynced(initial.days, emptyList(), "", TimetableContentStatus.NoFestival, null)
            expectState {
                copy(
                    days = initial.days,
                    selectedDayIndex = 2,
                    lineup = emptyList(),
                    source = "",
                    contentStatus = TimetableContentStatus.NoFestival,
                    offlineMessage = null,
                )
            }
        }
    }

    @Test
    fun packSyncWithoutSelectedDayFallsBackToFirstDay() = runTest {
        viewModel().test(this, initialState = sample()) {
            containerHost.onPackSynced(emptyList(), emptyList(), "", TimetableContentStatus.NoFestival, null)
            expectState {
                copy(
                    days = emptyList(),
                    selectedDayIndex = 0,
                    source = "",
                    contentStatus = TimetableContentStatus.NoFestival,
                    overlaps = emptyList(),
                )
            }
        }
    }

    @Test
    fun parentScheduleChangeReplacesMySets() = runTest {
        viewModel().test(this, initialState = sample()) {
            containerHost.onMySetIdsChanged(setOf("lime"))
            expectState { copy(mySetIds = setOf("lime"), overlaps = emptyList()) }
        }
    }

    /** iOS `MainTabCoordinator+Sync` 내용 상태 판정 · 오프라인 안내 · 축제 변경(Android는 타임테이블이 공유 상태를 직접 읽는다, 설계 T1). */
    @Test
    fun boardSelectionDecidesContentStatusAndOfflineMessage() = runTest {
        val days = sample().days
        mapper.days = mapOf("fst" to days)
        clock.now = Instant.parse("2026-11-01T03:13:00Z")
        viewModel().test(this, initialState = TimetableState()) {
            runOnCreate()
            assertEquals(TimetableContentStatus.NoFestival, awaitState().contentStatus)

            boardSelection.selection.value = selection("fst", document = null, connection = FestivalBoardSelection.Connection.Loading)
            assertEquals(TimetableContentStatus.Loading, awaitState().contentStatus)
            assertEquals("fst", awaitState().festivalId)

            boardSelection.selection.value = selection("fst", document = null, isOnlineDetailFailed = true)
            assertEquals(TimetableContentStatus.Failed, awaitState().contentStatus)

            boardSelection.selection.value =
                selection(
                    "fst",
                    connection = FestivalBoardSelection.Connection.Offline,
                    packCheckedAt = Instant.parse("2026-11-01T03:08:48Z"),
                )
            val unpublished = awaitState()
            assertEquals(TimetableContentStatus.Unpublished, unpublished.contentStatus)
            assertEquals("오프라인 · 4분 전 정보", unpublished.offlineMessage)
            assertEquals(days, unpublished.days)

            // 다른 축제의 문서는 쓰지 않는다(iOS `displayDocument.id == festivalID`).
            boardSelection.selection.value = selection("fst", document = FestivalPackDocument(id = "other"), isPackWatchFailed = true)
            val failed = awaitState()
            assertEquals(TimetableContentStatus.Failed, failed.contentStatus)
            assertNull(failed.offlineMessage)
            assertTrue(failed.days.isEmpty())
            cancelAndIgnoreRemainingItems()
        }
    }

    // MARK: - 공연 상세 시트 (iOS TimetableFeatureTests · TimetableScheduleTests)

    @Test
    fun tappingSetOpensDetailWithoutChangingSchedule() = runTest {
        // 샘플 첫날 정오. lime(17:00–17:50)은 5시간 뒤 시작한다.
        val now = minute(12 * 60)
        clock.now = now
        val initial = sample()
        viewModel().test(this, initialState = initial) {
            containerHost.onSetClicked("lime")
            expectState {
                copy(
                    setDetail =
                        TimetableSetDetailState(
                            setId = "lime",
                            content =
                                TimetableSetDetailContent(
                                    detail =
                                        TimetableSetDetail(
                                            artist = "라임플래그스",
                                            stage = "리벳 스테이지",
                                            time = "17:00–17:50 · 50분",
                                            status = TimetableSetDetail.Status.Upcoming(300),
                                            isScheduled = false,
                                        ),
                                    interval = TimetableInterval(now.plusSeconds(5 * 3600L), now.plusSeconds(5 * 3600L + 50 * 60L)),
                                    source = initial.source,
                                    isScheduleLoading = false,
                                    isScheduleLimitReached = false,
                                ),
                        ),
                )
            }
            containerHost.onSetDetailDismissed()
            expectState { copy(setDetail = null) }
        }
    }

    @Test
    fun detailStatusFollowsCurrentTime() {
        val start = Instant.ofEpochSecond(1_786_089_600)
        val state =
            TimetableSetDetailState(
                setId = "lime",
                content =
                    TimetableSetDetailContent(
                        detail = TimetableSetDetail("라임", "리벳 스테이지", "17:00–17:50 · 50분", TimetableSetDetail.Status.Upcoming(30), false),
                        interval = TimetableInterval(start, start.plusSeconds(50 * 60L)),
                        source = "",
                        isScheduleLoading = false,
                        isScheduleLimitReached = false,
                    ),
            )
        assertEquals("1시간 30분 후", state.detail(start.minusSeconds(90 * 60L)).relativeTime)
        assertEquals(TimetableSetDetail.Status.Live, state.detail(start.plusSeconds(10 * 60L)).status)
        assertEquals(TimetableSetDetail.Status.Ended, state.detail(start.plusSeconds(50 * 60L)).status)
    }

    @Test
    fun setDetailShowsTheSetNoteAsMemo() = runTest {
        val state = sample()
        assertNull(content(state, "cryingbird", FIRST_DAY)?.detail?.memo)
        val noted = state.copy(
            days = state.days.map { day ->
                day.copy(
                    sets = day.sets.map {
                        if (it.id ==
                            "cryingbird"
                        ) {
                            it.copy(note = "앵콜 없이 정시에 끝나요.")
                        } else {
                            it
                        }
                    },
                )
            },
        )
        assertEquals("앵콜 없이 정시에 끝나요.", content(noted, "cryingbird", FIRST_DAY)?.detail?.memo)
    }

    @Test
    fun addingAnOverlappingSetShowsTheBoardWarning() = runTest {
        val initial = opened(sample().copy(mySetIds = setOf("noise-returns")).derived(), "cryingbird", minute(928))
        viewModel().test(this, initialState = initial) {
            containerHost.onScheduleToggled()
            val state = awaitState()
            assertTrue("cryingbird" in state.mySetIds)
            assertTrue(state.shouldShowOverlapWarning)
            assertEquals(35, state.visibleOverlaps.first().minutes)
            assertEquals(setOf("noise-returns", "cryingbird"), state.overlappingSetIds)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun scheduleStopsAtLimit() = runTest {
        val full = (0 until TimetableState.SCHEDULE_LIMIT).map { "set-$it" }.toSet()
        val initial = opened(sample().copy(mySetIds = full).derived(), "lime")
        viewModel().test(this, initialState = initial) {
            containerHost.onScheduleToggled()
            val state = awaitState()
            assertTrue(state.isScheduleLimitReached)
            assertEquals(true, state.setDetail?.content?.isScheduleLimitReached)
            assertEquals(full, state.mySetIds)
        }
    }

    @Test
    fun endedSetCannotBeAdded() = runTest {
        val afterFirstDay = FIRST_DAY.plusSeconds(2 * 86_400L)
        val initial = opened(sample().copy(mySetIds = emptySet()).derived(), "opening", afterFirstDay)
        viewModel().test(this, initialState = initial) {
            containerHost.onScheduleToggled()
            expectNoItems()
        }
    }

    @Test
    fun detailShowsTimeUntilStartAndStatus() = runTest {
        val state = sample() // lime 17:00–17:50
        val upcoming = content(state, "lime", minute(15 * 60 + 28))?.detail
        assertEquals("1시간 32분 후", upcoming?.relativeTime)
        assertEquals("리벳 스테이지", upcoming?.stage)
        assertEquals("17:00–17:50 · 50분", upcoming?.time)
        assertEquals("40분 후", content(state, "lime", minute(16 * 60 + 20))?.detail?.relativeTime)
        assertEquals("1시간 후", content(state, "lime", minute(16 * 60))?.detail?.relativeTime)
        assertEquals(TimetableSetDetail.Status.Live, content(state, "lime", minute(17 * 60 + 10))?.detail?.status)
        assertEquals(TimetableSetDetail.Status.Ended, content(state, "lime", minute(18 * 60))?.detail?.status)
    }

    @Test
    fun detailCarriesArtistImage() = runTest {
        val url = "https://example.com/lime.webp"
        val base = sample()
        val state = base.copy(
            days = base.days.map { day ->
                day.copy(
                    sets = day.sets.map {
                        if (it.id ==
                            "lime"
                        ) {
                            it.copy(imageUrl = url)
                        } else {
                            it
                        }
                    },
                )
            },
        )
        assertEquals(url, content(state, "lime", FIRST_DAY)?.detail?.imageUrl)
        assertNull(content(state, "noise", FIRST_DAY)?.detail?.imageUrl)
    }

    @Test
    fun overlapAcrossMidnightIsDetected() = runTest {
        val base = sample()
        val days = base.days.mapIndexed { index, day ->
            if (index ==
                1
            ) {
                day.copy(sets = listOf(TimetableSet("dawn", "새벽 세션", "rivet", 30, 90)))
            } else {
                day
            }
        }
        // 1일차 23:30–다음날 01:30, 2일차 00:30–01:30
        val state = base.copy(days = days, mySetIds = setOf("midnight", "dawn")).derived()
        val overlap = content(state, "midnight", BEFORE_FESTIVAL)?.detail?.overlap
        assertEquals(TimetableSetOverlap("새벽 세션", "리벳 스테이지", "00:30–01:30", 60), overlap)
        assertEquals("새벽 세션(리벳 스테이지 00:30–01:30)과 60분 겹쳐요", overlap?.warning)
        assertEquals(listOf(TimetableOverlap(1470, 1530)), state.overlaps)
    }

    @Test
    fun overlapWarningNamesStageTimeAndSharedMinutes() = runTest {
        // 리벳 15:40–16:30, 원더 15:55–16:45
        val state = sample().copy(mySetIds = setOf("noise-returns", "cryingbird")).derived()
        assertEquals("크라잉버드(원더 스테이지 15:55–16:45)와 35분 겹쳐요", content(state, "noise-returns", BEFORE_FESTIVAL)?.detail?.overlap?.warning)
    }

    @Test
    fun changedSetShowsPreviousTimeAndDuration() = runTest {
        // lime 16:40 → 17:00–17:50
        val detail = content(sample(SampleVariant.Changed), "lime", BEFORE_FESTIVAL)?.detail
        assertEquals("16:40", detail?.previousTime)
        assertEquals("20분 미뤄짐", detail?.change)
        assertEquals("17:00–17:50 · 50분", detail?.time)
    }

    // MARK: - 내 일정 토스트 (iOS TimetableScheduleTests)

    private fun localState(): TimetableState =
        sample().copy(festivalId = "fst", mySetIds = emptySet(), scheduleSync = TimetableScheduleSync.LocalOnly).derived()

    @Test
    fun addingShowsToastForTwoSeconds() = runTest {
        val initial = opened(localState(), "lime")
        viewModel().test(this, initialState = initial) {
            containerHost.onScheduleToggled()
            val added = awaitState()
            assertEquals(setOf("lime"), added.mySetIds)
            assertEquals(true, added.setDetail?.content?.detail?.isScheduled)
            assertEquals(false, added.setDetail?.isScheduleToastVisible)
            assertEquals(true, awaitState().setDetail?.isScheduleToastVisible)
            sleeper.release(TimetableViewModel.TOAST_DURATION)
            assertEquals(false, awaitState().setDetail?.isScheduleToastVisible)
        }
    }

    @Test
    fun removingHidesToastImmediately() = runTest {
        val initial = opened(localState(), "lime")
        viewModel().test(this, initialState = initial) {
            addLime()
            containerHost.onScheduleToggled()
            val removed = awaitState()
            assertTrue(removed.mySetIds.isEmpty())
            assertEquals(false, removed.setDetail?.content?.detail?.isScheduled)
            assertEquals(false, removed.setDetail?.isScheduleToastVisible)
            sleeper.release(TimetableViewModel.TOAST_DURATION)
            expectNoItems()
        }
    }

    @Test
    fun dismissingSheetHidesToast() = runTest {
        viewModel().test(this, initialState = opened(localState(), "lime")) {
            addLime()
            containerHost.onSetDetailDismissed()
            assertNull(awaitState().setDetail)
            sleeper.release(TimetableViewModel.TOAST_DURATION)
            expectNoItems()
        }
    }

    @Test
    fun selectingAnotherSetHidesToast() = runTest {
        viewModel().test(this, initialState = opened(localState(), "lime")) {
            addLime()
            containerHost.onSetClicked("noise")
            val state = awaitState()
            assertEquals("noise", state.setDetail?.setId)
            assertEquals(false, state.setDetail?.isScheduleToastVisible)
            sleeper.release(TimetableViewModel.TOAST_DURATION)
            expectNoItems()
        }
    }

    @Test
    fun festivalChangeHidesToast() = runTest {
        viewModel().test(this, initialState = opened(localState(), "lime")) {
            addLime()
            containerHost.onFestivalChanged("next")
            val state = awaitState()
            assertEquals("next", state.festivalId)
            assertTrue(state.mySetIds.isEmpty())
            assertNull(state.setDetail)
            sleeper.release(TimetableViewModel.TOAST_DURATION)
            expectNoItems()
        }
    }

    @Test
    fun packUpdateClosingSelectedSetHidesToast() = runTest {
        val lime = opened(localState(), "lime")
        val initial = lime.copy(setDetail = lime.setDetail?.copy(setId = "gone", isScheduleToastVisible = true))
        viewModel().test(this, initialState = initial) {
            containerHost.onPackUpdated()
            expectState { copy(setDetail = null) }
        }
    }

    /** 열린 lime 시트에서 내 일정에 넣고 토스트가 뜨는 것까지 확인한다(iOS `addLime`). */
    private suspend fun OrbitTestContext<TimetableState, TimetableSideEffect, TimetableViewModel>.addLime() {
        containerHost.onScheduleToggled()
        assertEquals(setOf("lime"), awaitState().mySetIds)
        assertEquals(true, awaitState().setDetail?.isScheduleToastVisible)
    }
}
