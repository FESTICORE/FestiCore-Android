package io.festicore.domain.timetable.entity

import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

/** 공연 상세 시트 내용. iOS `TimetableSetDetail`. */
data class TimetableSetDetail(
    val artist: String,
    val stage: String,
    val time: String,
    val status: Status,
    val isScheduled: Boolean,
    /** 시간이 겹치는 다른 내 일정 공연 중 가장 먼저 시작하는 공연. */
    val overlap: TimetableSetOverlap? = null,
    /** 바뀌기 전 시작 시각(`HH:mm`). 바뀌지 않았으면 null. */
    val previousTime: String? = null,
    val change: String? = null,
    /** 출연진 사진 주소. 없으면 시트에 이름만 보인다. */
    val imageUrl: String? = null,
    /** 한 줄 메모. 없으면 시트에서 숨긴다. */
    val memo: String? = null,
) {
    sealed interface Status {
        data class Upcoming(val minutes: Int) : Status

        data object Live : Status

        data object Ended : Status

        companion object {
            /** 공연의 실제 시작 · 종료 시각과 현재 시각으로 진행 상태를 구한다. 남은 분은 올림한다. */
            fun of(interval: TimetableInterval, now: Instant): Status = when {
                now >= interval.end -> Ended
                now >= interval.start -> Live
                else -> Upcoming(ceil(Duration.between(now, interval.start).toMillis() / 60_000.0).toInt())
            }
        }
    }

    val relativeTime: String
        get() = when (status) {
            is Status.Upcoming -> {
                val minutes = status.minutes
                if (minutes < 60) {
                    "${minutes}분 후"
                } else {
                    val hours = minutes / 60
                    val rest = minutes % 60
                    if (rest == 0) "${hours}시간 후" else "${hours}시간 ${rest}분 후"
                }
            }

            Status.Live -> "공연 중"

            Status.Ended -> "종료"
        }
}

/** 시트 경고에 쓰는 겹친 내 일정 공연. iOS `TimetableSetOverlap`. */
data class TimetableSetOverlap(
    val artist: String,
    val stage: String,
    /** `HH:mm–HH:mm` */
    val time: String,
    /** 겹치는 시간(분). */
    val minutes: Int,
) {
    /** "크라잉버드(원더 스테이지 15:55–16:45)와 35분 겹쳐요" */
    val warning: String get() = "$artist($stage $time)$particle ${minutes}분 겹쳐요"

    /**
     * 이름 끝 글자가 한글이면 받침에 따라 `와` · `과`를 고르고, 아니면 `와`를 쓴다.
     * 자모가 나뉜 이름도 같은 글자로 보도록 완성형(NFC)으로 바꿔 판단한다.
     */
    private val particle: String
        get() {
            val composed = Normalizer.normalize(artist, Normalizer.Form.NFC)
            if (composed.isEmpty()) return "와"
            val last = composed.codePointBefore(composed.length)
            if (last !in 0xAC00..0xD7A3) return "와"
            return if ((last - 0xAC00) % 28 == 0) "와" else "과"
        }
}
