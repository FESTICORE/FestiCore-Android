package io.festicore.domain.timetable.sources

import io.festicore.domain.timetable.TimetableDay
import io.festicore.domain.timetable.TimetableOverlap
import io.festicore.domain.timetable.TimetablePickOverlap
import io.festicore.domain.timetable.TimetableSet
import io.festicore.domain.timetable.TimetableSetOverlap
import io.festicore.domain.timetable.TimetableStage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 겹침 계산(iOS `TimetableFeature.State.overlaps` · `overlaps(of:in:)` · `SchedulePickFeature.State.overlap(of:)`). */
class TimetableOverlapCalculatorTest {
    private val calculator = TimetableOverlapCalculatorImpl()
    private val stages = listOf(TimetableStage("rivet", "리벳 스테이지"), TimetableStage("wonder", "원더 스테이지"))

    /** 2026-08-07 00:00 KST, 다음 날 */
    private val first =
        TimetableDay(
            id = "d1",
            title = "8/7(금)",
            date = Instant.parse("2026-08-06T15:00:00Z"),
            stages = stages,
            sets = listOf(
                TimetableSet("lime", "라임플래그스", "rivet", start = 955, end = 1010),
                TimetableSet("cry", "크라잉버드", "wonder", start = 955 - 20, end = 1005),
                TimetableSet("late", "새벽 세션", "rivet", start = 1410, end = 1530),
            ),
        )
    private val second =
        TimetableDay(
            id = "d2",
            title = "8/8(토)",
            date = Instant.parse("2026-08-07T15:00:00Z"),
            stages = stages,
            sets = listOf(TimetableSet("open", "오프너", "wonder", start = 60, end = 120)),
        )
    private val days = listOf(first, second)

    @Test
    fun `선택한 날의 내 일정 겹침 구간을 분으로 돌려준다`() {
        val overlaps = calculator.overlaps(days, setOf("lime", "cry"), first)

        assertEquals(listOf(TimetableOverlap(955, 1005)), overlaps)
        assertTrue(calculator.overlaps(days, setOf("lime"), first).isEmpty())
    }

    @Test
    fun `자정을 넘겨 다음 날 공연과 겹치는 구간은 양쪽 날 기준으로 포함한다`() {
        val mine = setOf("late", "open")

        assertEquals(listOf(TimetableOverlap(1440 + 60, 1530)), calculator.overlaps(days, mine, first))
        assertEquals(listOf(TimetableOverlap(60, 90)), calculator.overlaps(days, mine, second))
    }

    @Test
    fun `공연과 겹치는 다른 내 일정을 시작순으로, 무대 이름과 겹친 분을 함께 돌려준다`() {
        val overlaps = calculator.setOverlaps(days, setOf("lime", "cry", "late"), first.sets[0], first)

        assertEquals(listOf(TimetableSetOverlap("크라잉버드", "원더 스테이지", "15:35–16:45", 50)), overlaps)
        assertTrue(calculator.setOverlaps(days, setOf("lime"), first.sets[0], first).isEmpty())
    }

    @Test
    fun `내 일정 고르기는 먼저 시작한 고른 공연 중 가장 많이 겹친 것을 고른다`() {
        val selected = setOf("lime", "cry")

        assertEquals(TimetablePickOverlap("크라잉버드", 50), calculator.pickOverlap(first, selected, first.sets[0]))
        assertNull(calculator.pickOverlap(first, selected, first.sets[1]))
        assertNull(calculator.pickOverlap(first, setOf("cry"), first.sets[0]))
    }
}
