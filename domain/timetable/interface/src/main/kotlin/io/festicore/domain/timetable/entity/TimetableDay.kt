package io.festicore.domain.timetable.entity

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** 하루치 시간표. iOS `TimetableDay`. */
data class TimetableDay(
    val id: String,
    val title: String,
    /** 그날 0시(Asia/Seoul). 공연의 실제 시각은 여기에 분을 더해 구한다. */
    val date: Instant,
    val stages: List<TimetableStage>,
    val sets: List<TimetableSet>,
) {
    /** 공연의 실제 시작 · 종료 시각. */
    fun interval(set: TimetableSet): TimetableInterval = TimetableInterval(
        start = date.plus(Duration.ofMinutes(set.start.toLong())),
        end = date.plus(Duration.ofMinutes(set.end.toLong())),
    )

    /** 시작 시각이 바뀐 공연 수. */
    val changedCount: Int get() = sets.count { it.previousStart != null }

    /** 한 줄 메모를 뺀 사본. 내 일정 고르기에 보이지 않는 메모만 바뀐 팩 갱신을 구분한다. */
    val ignoringNotes: TimetableDay get() = copy(sets = sets.map { it.copy(note = null) })

    companion object {
        /** 날짜 · 시각을 계산하는 시간대. iOS `AppDateFormat.timeZone`. */
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        /** 그날 0시부터의 분을 `HH:mm`으로 적는다. 자정을 넘긴 시각은 다음날 시각으로 적는다. */
        fun clock(minute: Int): String {
            val value = minute % (24 * 60)
            return String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60)
        }
    }
}

data class TimetableStage(
    val id: String,
    val name: String,
)

/** 공연 하나. [start] · [end]는 그날 0시부터의 분(자정을 넘기면 1440 이상). iOS `TimetableSet`. */
data class TimetableSet(
    val id: String,
    val artist: String,
    val stageId: String,
    val start: Int,
    val end: Int,
    /** 바뀌기 전 시작 시각. 바뀌지 않았으면 null. */
    val previousStart: Int? = null,
    /** 출연진 사진 주소. 없으면 null. */
    val imageUrl: String? = null,
    /** 공연 상세 시트의 한 줄 메모. 없으면 null. */
    val note: String? = null,
) {
    /** `HH:mm–HH:mm`. 자정을 넘기면 끝 시각 앞에 "다음날"을 붙인다. */
    val timeRange: String
        get() {
            val crossesMidnight = start < 24 * 60 && end > 24 * 60
            return "${TimetableDay.clock(start)}–${if (crossesMidnight) "다음날 " else ""}${TimetableDay.clock(end)}"
        }

    /** 공연 시각과 길이를 "17:00–17:50 · 50분"으로 적는다. */
    val timeRangeWithDuration: String get() = "$timeRange · ${end - start}분"

    /** 바뀌기 전 시작 시각(`HH:mm`). 바뀌지 않았으면 null. */
    val previousTime: String? get() = previousStart?.let(TimetableDay::clock)

    /** 바뀐 시작 시각을 "20분 미뤄짐" · "15분 당겨짐"으로 적는다. 바뀌지 않았으면 null. */
    val startChange: String?
        get() = previousStart?.let { previous ->
            val delta = start - previous
            if (delta > 0) "${delta}분 미뤄짐" else "${-delta}분 당겨짐"
        }
}

/** 실제 시각 구간. iOS `DateInterval`. */
data class TimetableInterval(
    val start: Instant,
    val end: Instant,
)
