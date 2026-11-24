package io.festicore.domain.timetable.entity

import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `Domain/TimetableDomain/Tests/Sources/TimetableEntityTests.swift` 8건. */
class TimetableEntityTest {
    /** 2026-08-07 00:00 KST */
    private val day =
        TimetableDay(
            id = "day-1",
            title = "8.7 금",
            date = Instant.ofEpochSecond(1_786_028_400),
            stages = listOf(TimetableStage(id = "rivet", name = "리벳 스테이지")),
            sets = listOf(
                TimetableSet(id = "lime", artist = "라임플래그스", stageId = "rivet", start = 1020, end = 1070, previousStart = 1000),
                TimetableSet(id = "midnight", artist = "미드나잇", stageId = "rivet", start = 1410, end = 1530),
            ),
        )

    @Test
    fun ignoringNotesComparesOnlyScheduleFields() {
        val day =
            TimetableDay(
                id = "day",
                title = "토",
                date = Instant.EPOCH,
                stages = emptyList(),
                sets = listOf(TimetableSet(id = "set", artist = "A", stageId = "stage", start = 600, end = 660, note = "앵콜 없이 정시에 끝나요.")),
            )
        val noteChanged = day.copy(sets = listOf(day.sets[0].copy(note = "리벳 앞 왼쪽이 덜 붐벼요.")))
        val timeChanged = day.copy(sets = listOf(day.sets[0].copy(start = 610)))

        assertNotEquals(day, noteChanged)
        assertEquals(day.ignoringNotes, noteChanged.ignoringNotes)
        assertNotEquals(day.ignoringNotes, timeChanged.ignoringNotes)
        assertNull(day.ignoringNotes.sets.first().note)
    }

    @Test
    fun timeTextsMatchDesign() {
        assertEquals("23:30–다음날 01:30", day.sets[1].timeRange)
        assertEquals("12:30–13:10", TimetableSet(id = "a", artist = "", stageId = "", start = 750, end = 790).timeRange)
        assertEquals("17:00–17:50 · 50분", day.sets[0].timeRangeWithDuration)
        assertEquals("16:40", day.sets[0].previousTime)
        assertEquals("20분 미뤄짐", day.sets[0].startChange)
        assertEquals(
            "15분 당겨짐",
            TimetableSet(id = "b", artist = "", stageId = "", start = 1090, end = 1130, previousStart = 1105).startChange,
        )
        assertNull(day.sets[1].previousTime)
        assertNull(day.sets[1].startChange)
        assertEquals("01:30", TimetableDay.clock(1530))
    }

    @Test
    fun intervalAddsMinutesToDayStartAcrossMidnight() {
        val midnight = day.interval(day.sets[1])

        assertEquals(day.date.plus(Duration.ofMinutes(1410)), midnight.start)
        assertEquals(day.date.plus(Duration.ofMinutes(1530)), midnight.end)
        assertEquals(1, day.changedCount)
    }

    @Test
    fun statusFollowsCurrentTime() {
        val interval = day.interval(day.sets[0])

        assertEquals(TimetableSetDetail.Status.Upcoming(2), TimetableSetDetail.Status.of(interval, interval.start.minusSeconds(90)))
        assertEquals(TimetableSetDetail.Status.Live, TimetableSetDetail.Status.of(interval, interval.start))
        assertEquals(TimetableSetDetail.Status.Ended, TimetableSetDetail.Status.of(interval, interval.end))
    }

    @Test
    fun relativeTimeReadsHoursAndMinutes() {
        fun text(status: TimetableSetDetail.Status) =
            TimetableSetDetail(artist = "", stage = "", time = "", status = status, isScheduled = false).relativeTime

        assertEquals("40분 후", text(TimetableSetDetail.Status.Upcoming(40)))
        assertEquals("1시간 후", text(TimetableSetDetail.Status.Upcoming(60)))
        assertEquals("1시간 32분 후", text(TimetableSetDetail.Status.Upcoming(92)))
        assertEquals("공연 중", text(TimetableSetDetail.Status.Live))
        assertEquals("종료", text(TimetableSetDetail.Status.Ended))
    }

    @Test
    fun overlapMinutesAreTheSharedRange() {
        assertEquals(35, TimetableOverlap(start = 955, end = 990).minutes)
    }

    @Test
    fun overlapWarningUsesParticleForNonHangulName() {
        val overlap = TimetableSetOverlap(artist = "NELL", stage = "원더 스테이지", time = "15:55–16:45", minutes = 35)

        assertEquals("NELL(원더 스테이지 15:55–16:45)와 35분 겹쳐요", overlap.warning)
    }

    @Test
    fun overlapWarningUsesParticleForDecomposedHangulName() {
        val decomposed = Normalizer.normalize("새벽 세션", Normalizer.Form.NFD)
        val overlap = TimetableSetOverlap(artist = decomposed, stage = "리벳 스테이지", time = "00:30–01:30", minutes = 60)

        assertTrue(overlap.warning.endsWith(")과 60분 겹쳐요"))
    }
}
