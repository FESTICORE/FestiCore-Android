package io.festicore.domain.timetable.sources

import io.festicore.domain.timetable.TimetableOverlapCalculator
import io.festicore.domain.timetable.TimetablePickOverlap
import io.festicore.domain.timetable.entity.TimetableDay
import io.festicore.domain.timetable.entity.TimetableInterval
import io.festicore.domain.timetable.entity.TimetableOverlap
import io.festicore.domain.timetable.entity.TimetableSet
import io.festicore.domain.timetable.entity.TimetableSetOverlap
import java.time.Duration
import kotlin.math.roundToLong

/** iOS `TimetableFeature.State`(`overlaps` · `myIntervals` · `overlaps(of:in:)`)와 `SchedulePickFeature.State.overlap(of:)`. */
internal class TimetableOverlapCalculatorImpl : TimetableOverlapCalculator {
    override fun overlaps(
        days: List<TimetableDay>,
        mySetIds: Set<String>,
        day: TimetableDay,
    ): List<TimetableOverlap> {
        val mine = myIntervals(days, mySetIds)
        return mine.indices.flatMap { index ->
            val current = mine[index]
            mine.drop(index + 1).mapNotNull { other ->
                if (current.dayId != day.id && other.dayId != day.id) return@mapNotNull null
                val shared = current.interval.intersection(other.interval) ?: return@mapNotNull null
                TimetableOverlap(
                    start = (Duration.between(day.date, shared.start).toMillis() / 60_000.0).toInt(),
                    end = (Duration.between(day.date, shared.end).toMillis() / 60_000.0).toInt(),
                )
            }
        }
    }

    override fun setOverlaps(
        days: List<TimetableDay>,
        mySetIds: Set<String>,
        set: TimetableSet,
        day: TimetableDay,
    ): List<TimetableSetOverlap> {
        val interval = day.interval(set)
        return myIntervals(days, mySetIds).mapNotNull { mine ->
            if (mine.set.id == set.id) return@mapNotNull null
            val shared = mine.interval.intersection(interval) ?: return@mapNotNull null
            val stages = days.firstOrNull { it.id == mine.dayId }?.stages.orEmpty()
            TimetableSetOverlap(
                artist = mine.set.artist,
                stage = stages.firstOrNull { it.id == mine.set.stageId }?.name ?: UNASSIGNED_STAGE_NAME,
                time = mine.set.timeRange,
                minutes = (Duration.between(shared.start, shared.end).toMillis() / 60_000.0).roundToLong().toInt(),
            )
        }
    }

    override fun pickOverlap(
        day: TimetableDay,
        selectedSetIds: Set<String>,
        set: TimetableSet,
    ): TimetablePickOverlap? {
        if (set.id !in selectedSetIds) return null
        return day.sets
            .filter { it.id != set.id && it.id in selectedSetIds && it.start <= set.start }
            .mapNotNull { other ->
                val minutes = minOf(other.end, set.end) - maxOf(other.start, set.start)
                if (minutes > 0) TimetablePickOverlap(other.artist, minutes) else null
            }.maxByOrNull { it.minutes }
    }

    private data class Mine(
        val set: TimetableSet,
        val dayId: String,
        val interval: TimetableInterval,
    )

    private fun myIntervals(days: List<TimetableDay>, mySetIds: Set<String>): List<Mine> = days
        .flatMap { day -> day.sets.filter { it.id in mySetIds }.map { Mine(it, day.id, day.interval(it)) } }
        .sortedBy { it.interval.start }

    /** 길이가 0보다 큰 공통 구간. 없으면 null. */
    private fun TimetableInterval.intersection(other: TimetableInterval): TimetableInterval? {
        val start = maxOf(start, other.start)
        val end = minOf(end, other.end)
        return if (end > start) TimetableInterval(start, end) else null
    }

    private companion object {
        const val UNASSIGNED_STAGE_NAME = "무대 미정"
    }
}
