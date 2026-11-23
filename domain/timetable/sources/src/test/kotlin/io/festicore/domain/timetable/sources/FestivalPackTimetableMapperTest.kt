package io.festicore.domain.timetable.sources

import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.entity.FestivalPackDocument.Performance
import io.festicore.domain.timetable.TimetableLineupArtist
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `App/Tests/Sources/TimetableRoutingTests.swift`의 매퍼 4건 + stage 상세(축약) 확인. */
class FestivalPackTimetableMapperTest {
    private val mapper = FestivalPackTimetableMapperImpl()

    private fun pack(start: String) = FestivalPackDocument(
        id = "festival",
        timetable = listOf(Performance(id = "set", artist = "아티스트", startsAt = start, endsAt = "2026-08-07T18:00:00+09:00")),
    )

    @Test
    fun mapperOnlyMarksChangesWhenPreviousPerformanceExists() {
        val baseline = mapper.days(pack("2026-08-07T17:00:00+09:00"))
        assertNull(baseline.first().sets.first().previousStart)
        val second = pack("2026-08-07T17:20:00+09:00")
        val changed = mapper.days(second, previousDays = baseline)
        assertEquals(1020, changed.first().sets.first().previousStart)
        assertEquals(1040, changed.first().sets.first().start)
        val repeated = mapper.days(second, previousDays = changed)
        assertEquals(changed, repeated)
    }

    @Test
    fun mapperPassesPerformanceNoteToSet() {
        val pack =
            FestivalPackDocument(
                id = "festival",
                timetable = listOf(
                    Performance(
                        id = "set",
                        artist = "아티스트",
                        startsAt = "2026-08-07T17:00:00+09:00",
                        endsAt = "2026-08-07T18:00:00+09:00",
                        note = "앵콜 없이 정시에 끝나요.",
                    ),
                ),
            )

        assertEquals("앵콜 없이 정시에 끝나요.", mapper.days(pack).first().sets.first().note)
    }

    @Test
    fun unpublishedLineupDoesNotInventArtistDates() {
        val pack =
            FestivalPackDocument(
                id = "festival",
                days = listOf(FestivalPackDocument.Day(date = "2026-08-07", startsAt = null, endsAt = null)),
                artists = listOf(FestivalPackDocument.Artist(name = "아티스트", imageUrl = null)),
            )

        assertEquals("공개된 라인업", mapper.lineup(pack).first().title)
        val day = mapper.days(pack).single()
        assertTrue(day.sets.isEmpty())
        assertEquals("2026-08-07", day.id)
        assertEquals(Instant.parse("2026-08-06T15:00:00Z"), day.date)
    }

    @Test
    fun unpublishedLineupResolvesArtistImage() {
        val pack =
            FestivalPackDocument(
                id = "festival",
                artists = listOf(
                    FestivalPackDocument.Artist(name = "아티스트", imageUrl = "/images/a.jpg"),
                    FestivalPackDocument.Artist(name = "사진 없음", imageUrl = null),
                ),
            )
        val artists = FestivalPackTimetableMapperImpl { value -> value?.let { "https://example.com$it" } }.lineup(pack).first().artists

        assertEquals(
            listOf(
                TimetableLineupArtist(name = "아티스트", imageUrl = "https://example.com/images/a.jpg"),
                TimetableLineupArtist(name = "사진 없음"),
            ),
            artists,
        )
    }

    @Test
    fun `stage 상세 축약 팩은 3일 14공연, 시작순 무대, 서울 자정 날짜로 나뉜다`() {
        val pack = stagePack()
        val days = mapper.days(pack)

        assertEquals(listOf("2026-11-20", "2026-11-21", "2026-11-22"), days.map { it.id })
        assertEquals(listOf("11/20(금)", "11/21(토)", "11/22(일)"), days.map { it.title })
        assertEquals(Instant.parse("2026-11-19T15:00:00Z"), days.first().date)
        days.forEach { day ->
            assertEquals(14, day.sets.size)
            assertEquals(listOf("LIVET STAGE", "WONDER STAGE"), day.stages.map { it.name })
            assertEquals(12 * 60, day.sets.minOf { it.start })
            assertEquals(22 * 60, day.sets.maxOf { it.end })
            assertTrue(day.sets.zipWithNext().all { (a, b) -> a.start <= b.start })
            assertTrue(day.sets.all { it.previousStart == null && it.note == null })
        }
        assertEquals("출처: 공개 수집 카탈로그 · 확인 11/01 12:08", mapper.source(pack.source))
    }

    @Test
    fun `무효 시각 공연만 있으면 날짜를 만들지 않고, 무대 이름이 없으면 무대 미정이다`() {
        val invalid =
            FestivalPackDocument(
                id = "festival",
                days = listOf(FestivalPackDocument.Day(date = "2026-08-07", startsAt = null, endsAt = null)),
                timetable = listOf(Performance(id = "a", startsAt = "2026-08-07T18:00:00+09:00", endsAt = "2026-08-07T18:00:00+09:00")),
            )
        assertTrue(mapper.days(invalid).isEmpty())

        val unnamed =
            FestivalPackDocument(
                id = "festival",
                timetable = listOf(Performance(id = "a", startsAt = "2026-08-07T23:30:00+09:00", endsAt = "2026-08-08T01:30:00+09:00")),
            )
        val day = mapper.days(unnamed).single()
        assertEquals("stage-unassigned", day.stages.single().id)
        assertEquals("무대 미정", day.stages.single().name)
        assertEquals("출연진 미정", day.sets.single().artist)
        assertEquals(1410 to 1530, day.sets.single().start to day.sets.single().end)
    }

    @Test
    fun `출처는 이름이 없으면 빈 값, 확인 시각이 없으면 이름만 적는다`() {
        assertEquals("", mapper.source(null))
        assertEquals("", mapper.source(FestivalPackDocument.Source(name = "", url = null, verifiedAt = "2026-11-01T03:08:48Z")))
        assertEquals("출처: 공식", mapper.source(FestivalPackDocument.Source(name = "공식", url = null, verifiedAt = null)))
    }

    private fun stagePack(): FestivalPackDocument {
        val text = checkNotNull(javaClass.getResource("/timetable-stage.json")).readText()
        val root = Json.parseToJsonElement(text).jsonObject
        fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.content
        val source = root.getValue("source").jsonObject
        return FestivalPackDocument(
            id = root.string("id").orEmpty(),
            timetable = root.getValue("timetable").jsonArray.map { element ->
                val item = element.jsonObject
                Performance(
                    id = item.string("id").orEmpty(),
                    artist = item.string("artist"),
                    stageId = item.string("stageId"),
                    stageName = item.string("stageName"),
                    startsAt = item.string("startsAt"),
                    endsAt = item.string("endsAt"),
                )
            },
            source = FestivalPackDocument.Source(source.string("name"), source.string("url"), source.string("verifiedAt")),
        )
    }
}
