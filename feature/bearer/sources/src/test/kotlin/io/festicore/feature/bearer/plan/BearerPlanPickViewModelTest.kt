package io.festicore.feature.bearer.plan

import io.festicore.domain.bearer.entity.BearerSet
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `BearerPlanPickFeatureTests` 4건 + `AppDateFormatTests` `festivalDay`. 함수 이름을 iOS와 같게 둔다. */
class BearerPlanPickViewModelTest {
    /** 8/7(금) 17:00·19:00, 8/8(토) 17:00 KST */
    private val sets =
        listOf(
            BearerSet(
                id = "set_fri_2",
                artist = "실리카겔",
                stageName = "리벳 스테이지",
                startsAt = Instant.ofEpochSecond(1_786_096_800),
                endsAt = Instant.ofEpochSecond(1_786_100_400),
            ),
            BearerSet(
                id = "set_fri_1",
                artist = "새소년",
                stageName = "원더 스테이지",
                startsAt = Instant.ofEpochSecond(1_786_089_600),
                endsAt = Instant.ofEpochSecond(1_786_093_200),
            ),
            BearerSet(
                id = "set_sat_1",
                artist = "잔나비",
                stageName = "리벳 스테이지",
                startsAt = Instant.ofEpochSecond(1_786_176_000),
                endsAt = Instant.ofEpochSecond(1_786_179_600),
            ),
        )

    // 공연을 서울 기준 날짜로 묶고 이미 고른 공연이 있는 날로 연다
    @Test
    fun groupsDaysAndOpensPlannedDay() {
        val state = BearerPlanPickState.initial(sets = sets, plan = listOf("set_sat_1"))
        assertEquals(listOf("8/7(금)", "8/8(토)"), state.days.map { it.title })
        assertEquals(listOf("set_fri_1", "set_fri_2"), state.days.first().sets.map { it.id })
        assertEquals(1, state.selectedDayIndex)
    }

    // 공연을 눌러 고르고 빼며, 완료하면 시작 순으로 돌려준다
    @Test
    fun togglesAndReturnsOrderedPlan() = runTest {
        BearerPlanPickViewModel(sets = sets, plan = emptyList()).test(this) {
            containerHost.onSetTapped("set_sat_1")
            expectState { copy(selectedSetIds = setOf("set_sat_1")) }
            containerHost.onSetTapped("set_fri_2")
            expectState { copy(selectedSetIds = setOf("set_sat_1", "set_fri_2")) }
            containerHost.onSetTapped("set_fri_1")
            expectState { copy(selectedSetIds = setOf("set_sat_1", "set_fri_2", "set_fri_1")) }
            containerHost.onSetTapped("set_fri_2")
            expectState { copy(selectedSetIds = setOf("set_sat_1", "set_fri_1")) }
            containerHost.onDayTapped(1)
            expectState { copy(selectedDayIndex = 1) }
            containerHost.onDayTapped(5)
            containerHost.onDoneTapped()
            expectSideEffect(BearerPlanPickSideEffect.Picked(setIds = listOf("set_fri_1", "set_sat_1")))
        }
    }

    // 서버 한도 30개를 넘기면 고르지 않고 안내한다
    @Test
    fun limitsToThirty() = runTest {
        val plan = (1..30).map { "old_$it" }
        BearerPlanPickViewModel(sets = sets, plan = plan).test(this) {
            containerHost.onSetTapped("set_fri_1")
            expectState { copy(limitMessage = "오늘 계획은 30개까지 고를 수 있어요.") }
            containerHost.onSetTapped("old_1")
            expectState { copy(selectedSetIds = selectedSetIds - "old_1", limitMessage = null) }
        }
    }

    // 뒤로 가면 고르지 않고 닫는다
    @Test
    fun backCloses() = runTest {
        BearerPlanPickViewModel(sets = sets, plan = emptyList()).test(this) {
            containerHost.onBackTapped()
            expectSideEffect(BearerPlanPickSideEffect.CloseRequested)
        }
    }

    // iOS `AppDateFormatTests.koreanDayFormatsUseSeoulCalendar`의 `festivalDay` 줄
    @Test
    fun festivalDay() {
        assertEquals("10/30(금)", BearerPlanFormat.festivalDay(LocalDate.of(2026, 10, 30)))
    }
}
