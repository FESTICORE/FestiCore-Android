package io.festicore.domain.timetable.sources

import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.timetable.FestivalPackTimetableMapper
import io.festicore.domain.timetable.entity.TimetableDay
import io.festicore.domain.timetable.entity.TimetableLineupArtist
import io.festicore.domain.timetable.entity.TimetableLineupDay
import io.festicore.domain.timetable.entity.TimetableSet
import io.festicore.domain.timetable.entity.TimetableStage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** iOS `FestivalPackTimetableMapper`. [imageUrl]은 팩의 상대 사진 경로를 서버 주소로 바꾼다. */
internal class FestivalPackTimetableMapperImpl(private val imageUrl: (String?) -> String? = { null }) : FestivalPackTimetableMapper {
    override fun days(pack: FestivalPackDocument, previousDays: List<TimetableDay>): List<TimetableDay> {
        val performances = pack.performances
        // 원본 공연 목록이 비었을 때만 날짜로 빈 날을 만든다. 공연이 있지만 모두 무효면 날짜 0개다(iOS와 같음).
        if (performances.isEmpty()) {
            return pack.days.orEmpty().mapNotNull { day ->
                val value = day.date ?: day.startsAt ?: return@mapNotNull null
                val start = strictDay(value) ?: FestivalIsoTime.parse(value)?.let(::startOfDay) ?: return@mapNotNull null
                TimetableDay(id = dayId(start), title = dayTitle(start), date = start, stages = emptyList(), sets = emptyList())
            }
        }
        val entries =
            performances.mapNotNull { performance ->
                val startsAt = FestivalIsoTime.parse(performance.startsAt) ?: return@mapNotNull null
                val endsAt = FestivalIsoTime.parse(performance.endsAt) ?: return@mapNotNull null
                if (endsAt <= startsAt) null else Entry(startsAt, endsAt, performance)
            }
        return entries.groupBy { startOfDay(it.start) }.toSortedMap().map { (dayStart, group) ->
            val sorted = group.sortedBy { it.start }
            val stages = mutableListOf<TimetableStage>()
            sorted.forEach { entry ->
                val id = entry.performance.stageKey
                if (stages.none { it.id == id }) stages += TimetableStage(id, entry.performance.stageName ?: UNASSIGNED_STAGE_NAME)
            }
            val previousSets = previousDays.firstOrNull { it.date == dayStart }?.sets.orEmpty()
            val sets =
                sorted.map { entry ->
                    val performance = entry.performance
                    val minute = minutes(dayStart, entry.start)
                    val previous = previousSets.firstOrNull { it.id == performance.id }
                    TimetableSet(
                        id = performance.id,
                        artist = performance.artist ?: UNKNOWN_ARTIST,
                        stageId = performance.stageKey,
                        start = minute,
                        end = minutes(dayStart, entry.end),
                        previousStart = previous?.let { if (it.start == minute) it.previousStart else it.start },
                        imageUrl = imageUrl(performance.imageUrl),
                        note = performance.note,
                    )
                }
            TimetableDay(id = dayId(dayStart), title = dayTitle(dayStart), date = dayStart, stages = stages, sets = sets)
        }
    }

    override fun lineup(pack: FestivalPackDocument): List<TimetableLineupDay> {
        val artists = pack.artists.orEmpty().filter { it.name.isNotEmpty() }.map { TimetableLineupArtist(it.name, imageUrl(it.imageUrl)) }
        if (artists.isEmpty()) return emptyList()
        return listOf(TimetableLineupDay(id = pack.id, title = LINEUP_TITLE, artists = artists))
    }

    override fun source(source: FestivalPackDocument.Source?): String {
        val name = source?.name?.takeIf { it.isNotEmpty() } ?: return ""
        val verifiedAt = FestivalIsoTime.parse(source.verifiedAt) ?: return "출처: $name"
        return "출처: $name · 확인 ${monthDayTimeFormatter.format(verifiedAt)}"
    }

    private data class Entry(
        val start: Instant,
        val end: Instant,
        val performance: FestivalPackDocument.Performance,
    )

    private val FestivalPackDocument.Performance.stageKey: String get() = stageId ?: stageName ?: UNASSIGNED_STAGE_ID

    private companion object {
        const val UNASSIGNED_STAGE_ID = "stage-unassigned"
        const val UNASSIGNED_STAGE_NAME = "무대 미정"
        const val UNKNOWN_ARTIST = "출연진 미정"
        const val LINEUP_TITLE = "공개된 라인업"

        /** iOS `AppDateFormat.day`(en_US_POSIX). */
        val dayFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

        /** iOS `AppDateFormat.festivalDay`(`M/d(E)`, ko_KR). */
        val festivalDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN).withZone(TimetableDay.ZONE)

        /** iOS `AppDateFormat.monthDayTime`(en_US_POSIX). */
        val monthDayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.ROOT).withZone(TimetableDay.ZONE)

        fun startOfDay(instant: Instant): Instant =
            instant.atZone(TimetableDay.ZONE).toLocalDate().atStartOfDay(TimetableDay.ZONE).toInstant()

        /** `yyyy-MM-dd`가 정확히 맞을 때만 그날 0시. */
        fun strictDay(value: String): Instant? = try {
            val date = LocalDate.parse(value, dayFormatter)
            if (dayFormatter.format(date) == value) date.atStartOfDay(TimetableDay.ZONE).toInstant() else null
        } catch (_: DateTimeParseException) {
            null
        }

        fun dayId(dayStart: Instant): String = dayFormatter.format(dayStart.atZone(TimetableDay.ZONE).toLocalDate())

        fun dayTitle(dayStart: Instant): String = festivalDayFormatter.format(dayStart)

        fun minutes(dayStart: Instant, at: Instant): Int = (Duration.between(dayStart, at).seconds / 60).toInt()
    }
}
