package io.festicore.feature.festival.widget

import io.festicore.domain.festival.entity.FestivalWidgetSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalWidgetPresentationTest {
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private fun snapshot(end: String? = "2026-11-26T16:00:00Z") = FestivalWidgetSnapshot(
        festivalId = "festival",
        festivalName = "축제",
        packVersion = 1,
        etag = "v1",
        performances = listOf(
            FestivalWidgetSnapshot.Performance(
                "set",
                "긴 아티스트",
                "main",
                "메인",
                time("2026-11-26T14:30:00Z"),
                end?.let(::time),
            ),
        ),
        days = listOf(
            FestivalWidgetSnapshot.Day("2026-11-26", time("2026-11-26T06:00:00Z")),
            FestivalWidgetSnapshot.Day("2026-11-27", time("2026-11-27T06:00:00Z")),
        ),
    )

    @Test fun `future festival shows opening before its first calendar day`() {
        val result = presentWidget(snapshot(), time("2026-11-23T00:00:00Z"))
        assertEquals(WidgetPhase.Before, result.phase)
        assertTrue(result.subtitle.contains("15:00 개장"))
    }

    @Test fun `WR2 next festival morning shows upcoming gate after overnight performance ended`() {
        val base = snapshot()
        assertEquals(WidgetPhase.Before, presentWidget(base, time("2026-11-27T01:00:00Z")).phase)
        assertEquals(WidgetPhase.GoingHome, presentWidget(base, time("2026-11-26T15:30:00Z")).phase)
        assertFalse(presentWidget(base, time("2026-11-27T06:00:00Z")).phase == WidgetPhase.Before)
    }

    @Test fun `overnight active set is not demoted to next day before opening`() {
        val result = presentWidget(snapshot(), time("2026-11-26T15:30:00Z"))
        assertEquals(WidgetPhase.GoingHome, result.phase)
        assertTrue(result.subtitle.contains("01:00"))
    }

    @Test fun `unknown overnight ending is not invented`() {
        val result = presentWidget(snapshot(null), time("2026-11-26T15:30:00Z"))
        assertEquals(WidgetPhase.Unknown, result.phase)
        assertTrue(result.title.contains("미정"))
    }

    @Test fun `unknown schedule keeps neutral CTA and maximum four rows`() {
        val base = snapshot()
        val next = base.performances.single().copy(startsAt = time("2026-11-27T08:00:00Z"), endsAt = time("2026-11-27T10:00:00Z"))
        val result = presentWidget(base.copy(performances = (1..6).map { next.copy(id = "$it") }), time("2026-11-27T07:00:00Z"))
        assertEquals("앱에서 일정 보기", result.footer)
        assertEquals(4, result.rows.size)
    }

    @Test fun `storage failure is distinct from no pack`() {
        assertEquals(WidgetPhase.Unavailable, presentWidget(null, 0, unavailable = true).phase)
    }

    @Test fun `null end is current until next same stage start without inventing display end`() {
        val base = snapshot(null)
        val next = base.performances.single().copy(
            id = "next",
            artist = "다음",
            startsAt = time("2026-11-26T16:00:00Z"),
            endsAt = time("2026-11-26T18:00:00Z"),
        )
        val result = presentWidget(base.copy(performances = base.performances + next), time("2026-11-26T15:00:00Z"))
        assertEquals("긴 아티스트", result.title)
        assertTrue(result.subtitle.contains("종료 미정"))
        assertFalse(result.subtitle.contains("–01:00"))
    }

    @Test fun `final ending directs to festival list with matching CTA`() {
        val result = presentWidget(snapshot().copy(days = emptyList()), time("2026-11-26T16:00:00Z"))
        assertEquals(WidgetPhase.Finished, result.phase)
        assertNull(result.festivalId)
        assertEquals("축제 둘러보기", result.footer)
    }

    @Test fun `playing poster also shows next personal schedule`() {
        val base = snapshot(end = "2026-11-26T18:00:00Z")
        val next = base.performances.single().copy(
            id = "next",
            artist = "내 다음 공연",
            startsAt = time("2026-11-26T18:00:00Z"),
            endsAt = time("2026-11-26T19:00:00Z"),
            isSaved = true,
        )
        val result =
            presentWidget(base.copy(performances = base.performances + next, scheduleKnownForOwner = true), time("2026-11-26T15:00:00Z"))
        assertTrue(result.info.contains("내 다음 공연"))
        assertTrue(result.info.contains("03:00"))
    }

    @Test fun `opening and final hour boundaries are inclusive and empty pack has a distinct CTA`() {
        val base = snapshot().copy(days = listOf(snapshot().days.first()))
        assertEquals(WidgetPhase.Before, presentWidget(base, time("2026-11-26T05:59:59Z")).phase)
        assertEquals(WidgetPhase.Playing, presentWidget(base, time("2026-11-26T06:00:00Z")).phase)
        assertEquals(WidgetPhase.Playing, presentWidget(base, time("2026-11-26T14:59:59Z")).phase)
        assertEquals(WidgetPhase.GoingHome, presentWidget(base, time("2026-11-26T15:00:00Z")).phase)
        assertEquals(WidgetPhase.Finished, presentWidget(base, time("2026-11-26T16:00:00Z")).phase)
        assertEquals("축제 둘러보기", presentWidget(null, 0).footer)
    }

    @Test fun `WI1 festival opening groups post midnight headliner through the gap`() {
        val base = snapshot()
        val first = base.performances.single().copy(startsAt = time("2026-11-26T14:00:00Z"), endsAt = time("2026-11-26T14:50:00Z"))
        val headliner = first.copy(id = "head", startsAt = time("2026-11-26T15:10:00Z"), endsAt = time("2026-11-26T16:00:00Z"))
        val data = base.copy(performances = listOf(first, headliner))
        assertEquals(WidgetPhase.Playing, presentWidget(data, time("2026-11-26T13:00:00Z")).phase)
        for (at in listOf("2026-11-26T14:55:00Z", "2026-11-26T15:05:00Z")) {
            val result = presentWidget(data, time(at))
            assertTrue(result.phase != WidgetPhase.Finished && result.phase != WidgetPhase.Before)
        }
    }

    @Test fun `WI2 unknown stage ending does not hide another known playing stage`() {
        val base = snapshot(null)
        val unknown = base.performances.single().copy(startsAt = time("2026-11-26T06:00:00Z"))
        val known = unknown.copy(
            id = "known",
            artist = "지금 공연",
            stageId = "second",
            startsAt = time("2026-11-26T07:00:00Z"),
            endsAt = time("2026-11-26T14:00:00Z"),
        )
        val result = presentWidget(base.copy(performances = listOf(unknown, known)), time("2026-11-26T08:00:00Z"))
        assertEquals(WidgetPhase.Playing, result.phase)
        assertEquals("지금 공연", result.title)
    }

    @Test fun `WI3 transit renders parsed local clock and supplied stop names only`() {
        val result =
            presentWidget(
                snapshot().copy(
                    lastShuttle = "2026-11-27T01:20:00+09:00",
                    lastTrain = "01:40",
                    shuttleStopName = "동문",
                    transitStationName = "축제역",
                ),
                time("2026-11-26T15:30:00Z"),
            )
        assertTrue(result.subtitle.contains("셔틀 01:20 동문"))
        assertTrue(result.subtitle.contains("막차 01:40 축제역"))
        assertFalse(result.subtitle.contains("2026-"))
        assertFalse(
            presentWidget(snapshot().copy(lastShuttle = "not-a-time"), time("2026-11-26T15:30:00Z")).subtitle.contains("not-a-time"),
        )
    }

    @Test fun `WI5 unavailable storage preserves recovered public pack as supporting information`() {
        val result = presentWidget(snapshot(), time("2026-11-26T15:30:00Z"), unavailable = true)
        assertEquals("festival", result.festivalId)
        assertTrue(listOf(result.title, result.subtitle, result.info).joinToString().contains("축제"))
    }

    @Test fun `WI8 nonfinal ending includes next opening and next unknown end stage start`() {
        val finished = presentWidget(snapshot(), time("2026-11-26T16:00:00Z"))
        assertTrue(finished.subtitle.contains("15:00"))
        val base = snapshot(null)
        val next = base.performances.single().copy(
            id = "next",
            startsAt = time("2026-11-26T16:00:00Z"),
            endsAt = time("2026-11-26T18:00:00Z"),
        )
        val current = presentWidget(base.copy(performances = base.performances + next), time("2026-11-26T15:00:00Z"))
        assertTrue(current.subtitle.contains("종료 미정 · 다음 01:00"))
    }
}
