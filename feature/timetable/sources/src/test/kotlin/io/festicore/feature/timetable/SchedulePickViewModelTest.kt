package io.festicore.feature.timetable

import io.festicore.domain.festival.entity.FestivalSchedule
import io.festicore.domain.festival.entity.FestivalScheduleError
import io.festicore.domain.timetable.TimetablePickOverlap
import io.festicore.domain.timetable.entity.TimetableDay
import io.festicore.domain.timetable.entity.TimetableSet
import io.festicore.domain.timetable.entity.TimetableStage
import io.festicore.feature.timetable.schedulepick.SchedulePickFormat
import io.festicore.feature.timetable.schedulepick.SchedulePickSideEffect
import io.festicore.feature.timetable.schedulepick.SchedulePickViewModel
import java.time.Instant
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** 내 일정 고르기 ViewModel. iOS `SchedulePickFeatureTests` 9건을 같은 이름으로 옮긴다. */
class SchedulePickViewModelTest {
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private val clock = MutableClock(now)
    private val boardSelection = FakeBoardSelectionUseCase(selection("festival"))
    private val mapper = FakeMapper(days = mapOf("festival" to days))
    private var schedule = FakeScheduleUseCase()

    private fun viewModel(selected: List<String> = emptyList(), timeoutMillis: Long? = null) = SchedulePickViewModel(
        route = TimetableRoute.SchedulePick("festival", selected, null),
        boardSelectionUseCase = boardSelection,
        mapper = mapper,
        overlapCalculator = FakeOverlapCalculator(),
        scheduleUseCase = schedule,
        clock = clock,
        saveTimeoutMillis = timeoutMillis,
    )

    private fun cachedPackUseCase(): io.festicore.domain.festival.pack.FestivalPackUseCase {
        val document = io.festicore.domain.festival.entity.FestivalPackDocument(id = "festival")
        val pack = io.festicore.domain.festival.entity.CachedFestivalPack("festival", 1, "v1", now, document)
        return object : io.festicore.domain.festival.pack.FestivalPackUseCase {
            override val state = kotlinx.coroutines.flow.MutableStateFlow(
                io.festicore.domain.festival.entity.FestivalPackState(packs = mapOf("festival" to pack)),
            )
            override suspend fun reloadCachedPacks() = listOf(pack)
            override fun packUpdates(festivalId: String) =
                kotlinx.coroutines.flow.emptyFlow<io.festicore.domain.festival.entity.PackSyncResult>()
            override fun downloadPack(festivalId: String) =
                kotlinx.coroutines.flow.emptyFlow<io.festicore.domain.festival.entity.PackDownloadEvent>()
            override suspend fun syncPack(festivalId: String): io.festicore.domain.festival.entity.PackSyncResult = error("not used")
            override suspend fun deleteExpiredPacks() = emptyList<String>()
        }
    }

    @Test
    fun targetCachedPackProvidesDaysWhenAnotherBoardIsSelected() {
        val packs = cachedPackUseCase()
        val picker = SchedulePickViewModel(
            route = TimetableRoute.SchedulePick("festival", emptyList(), null),
            boardSelectionUseCase = FakeBoardSelectionUseCase(selection("another")),
            mapper = mapper,
            overlapCalculator = FakeOverlapCalculator(),
            scheduleUseCase = schedule,
            clock = clock,
            packUseCase = packs,
        )
        assertEquals(days, picker.container.stateFlow.value.days)
    }

    @Test
    fun matchingOnlineDocumentWinsOverOlderCache() {
        val online = io.festicore.domain.festival.entity.FestivalPackDocument(id = "festival", name = "최신 온라인")
        val picker = SchedulePickViewModel(
            route = TimetableRoute.SchedulePick("festival", emptyList(), null),
            boardSelectionUseCase = FakeBoardSelectionUseCase(selection("festival", document = online)),
            mapper = mapper,
            overlapCalculator = FakeOverlapCalculator(),
            scheduleUseCase = schedule,
            clock = clock,
            packUseCase = cachedPackUseCase(),
        )
        assertEquals(days, picker.container.stateFlow.value.days)
        assertEquals(listOf(online), mapper.mappedPacks)
    }

    @Test
    fun matchingOnlineDocumentWorksWithoutDownloadedPack() {
        assertEquals(days, viewModel().container.stateFlow.value.days)
        assertEquals("festival", mapper.mappedPacks.single().id)
    }

    @Test
    fun tappingSetTogglesSelection() = runTest {
        viewModel().test(this) {
            containerHost.onSetTapped("saucy")
            assertEquals(setOf("saucy"), awaitState().selectedSetIds)
            containerHost.onSetTapped("saucy")
            assertTrue(awaitState().selectedSetIds.isEmpty())
        }
    }

    @Test
    fun nextSavesSelectionAsMyScheduleAndReportsSaved() = runTest {
        viewModel(listOf("spyair", "coldrain")).test(this) {
            containerHost.onNextTapped()
            assertTrue(awaitState().isSaving)
            val saved = awaitState()
            assertFalse(saved.isSaving)
            assertEquals(setOf("spyair", "coldrain"), saved.selectedSetIds)
            expectSideEffect(SchedulePickSideEffect.Finished(SchedulePickResult.Saved("festival", setOf("coldrain", "spyair"))))
        }
        assertEquals(listOf(FakeScheduleUseCase.Replaced("festival", listOf("coldrain", "spyair"), now)), schedule.replaced)
    }

    @Test
    fun prepSaveDeadlineReleasesNavigationWithoutDroppingSelection() = runTest {
        schedule = FakeScheduleUseCase(replace = { _, _, _ -> kotlinx.coroutines.awaitCancellation() })
        viewModel(listOf("saucy"), timeoutMillis = 100).test(this) {
            containerHost.onNextTapped()
            assertTrue(awaitState().isSaving)
            advanceTimeBy(101)
            runCurrent()
            assertFalse(containerHost.container.stateFlow.value.isSaving)
            val failed = awaitState()
            assertEquals(setOf("saucy"), failed.selectedSetIds)
            assertTrue(failed.errorMessage != null)
        }
    }

    @Test
    fun nextWithoutSelectionDoesNothing() = runTest {
        viewModel().test(this) {
            containerHost.onNextTapped()
            expectNoItems()
        }
        assertTrue(schedule.replaced.isEmpty())
    }

    @Test
    fun failedSaveKeepsSelectionAndShowsMessage() = runTest {
        schedule = FakeScheduleUseCase(replace = { _, _, _ -> throw FestivalScheduleError.Unavailable() })
        viewModel(listOf("natori")).test(this) {
            containerHost.onNextTapped()
            assertTrue(awaitState().isSaving)
            val failed = awaitState()
            assertFalse(failed.isSaving)
            assertEquals(setOf("natori"), failed.selectedSetIds)
            assertEquals("내 일정을 저장하지 못했어요. 다시 시도해 주세요.", failed.errorMessage)
        }
    }

    @Test
    fun staleSaveShowsNewerServerSchedule() = runTest {
        schedule = FakeScheduleUseCase(replace = { _, _, _ -> throw FestivalScheduleError.Stale(FestivalSchedule(listOf("saucy"))) })
        viewModel(listOf("natori")).test(this) {
            containerHost.onNextTapped()
            assertTrue(awaitState().isSaving)
            val stale = awaitState()
            assertFalse(stale.isSaving)
            assertEquals(setOf("saucy"), stale.selectedSetIds)
            assertEquals("다른 기기에서 바뀐 일정을 불러왔어요. 확인하고 다시 눌러 주세요.", stale.errorMessage)
        }
    }

    @Test
    fun skipAndLaterReportToParent() = runTest {
        viewModel().test(this) {
            containerHost.onSkipTapped()
            expectSideEffect(SchedulePickSideEffect.Finished(SchedulePickResult.Skipped))
            containerHost.onLaterTapped()
            expectSideEffect(SchedulePickSideEffect.Finished(SchedulePickResult.Later))
        }
    }

    @Test
    fun dayTapChangesDayWithinRange() = runTest {
        viewModel().test(this) {
            containerHost.onDayTapped(1)
            assertEquals(1, awaitState().selectedDayIndex)
            containerHost.onDayTapped(5)
            expectNoItems()
        }
    }

    @Test
    fun overlapNamesEarlierSelectedSetAndSharedMinutes() {
        val state = viewModel(listOf("coldrain", "spyair")).container.stateFlow.value
        // 공유 팩의 날짜를 매퍼로 다시 옮긴다(설계 T8).
        assertEquals(days, state.days)
        assertEquals(TimetablePickOverlap("coldrain", 20), state.overlaps["spyair"])
        assertNull(state.overlaps["coldrain"])
    }

    @Test
    fun particleFollowsLastHangulSyllable() {
        assertEquals("와", SchedulePickFormat.withParticle("새벽세시밴드"))
        assertEquals("와", SchedulePickFormat.withParticle("소음주의보"))
        assertEquals("과", SchedulePickFormat.withParticle("호락호락"))
        assertEquals("과", SchedulePickFormat.withParticle("coldrain"))
        assertEquals("01:05", SchedulePickFormat.clock(25 * 60 + 5))
    }

    /** 60개를 이미 골랐으면 넣지 않고 안내한다(iOS `setTapped` 한도 분기, 이식 테스트에는 없는 규칙이라 함께 고정한다). */
    @Test
    fun selectionStopsAtLimitWithMessage() = runTest {
        val full = (0 until 60).map { "set-$it" }
        viewModel(full).test(this) {
            containerHost.onSetTapped("saucy")
            val limited = awaitState()
            assertEquals(full.toSet(), limited.selectedSetIds)
            assertEquals("내 일정은 60개까지 고를 수 있어요.", limited.errorMessage)
            containerHost.onSetTapped("set-0")
            assertNull(awaitState().errorMessage)
        }
    }

    private companion object {
        /** Figma S15-1 예시 공연(iOS `SchedulePickFeatureTests.state()`). 시각은 그날 0시부터의 분이다. */
        val stages = listOf(TimetableStage("livet", "LIVET STAGE"), TimetableStage("wonder", "WONDER STAGE"))
        val days =
            listOf(
                TimetableDay(
                    id = "day1",
                    title = "11/20 금",
                    date = Instant.ofEpochSecond(1_795_100_400),
                    stages = stages,
                    sets =
                        listOf(
                            TimetableSet("saucy", "Saucy Dog", "livet", 930, 980),
                            TimetableSet("coldrain", "coldrain", "wonder", 1000, 1050),
                            TimetableSet("spyair", "SPYAIR", "livet", 1030, 1080),
                            TimetableSet("natori", "natori", "wonder", 1100, 1160),
                        ),
                ),
                TimetableDay("day2", "11/21 토", Instant.ofEpochSecond(1_795_186_800), stages, emptyList()),
            )
    }
}
