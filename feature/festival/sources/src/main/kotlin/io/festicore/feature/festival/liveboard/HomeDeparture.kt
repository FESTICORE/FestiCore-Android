package io.festicore.feature.festival.liveboard

import io.festicore.domain.festival.FestivalIsoTime
import io.festicore.domain.festival.FestivalPackDocument
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

/** 귀가 카드 역 · 셔틀 정류장까지 도보 분. 1차는 서버 값만 쓴다(설계 D6). iOS `FestivalFeature.HomeWalkMinutes`. */
internal data class HomeWalkMinutes(
    val station: Int? = null,
    val shuttleStop: Int? = null,
)

/**
 * 귀가 카드가 안내할 출발편. 지금 걸어가도 탈 수 있는 막차를 먼저, 안 되면 셔틀을 고른다.
 * 걸어서 닿을 수 있는 편이 없으면 아직 떠나지 않은 첫 편을 그대로 안내한다. 모두 떠났으면 [of]가 null이다. iOS `FestivalHomeDeparture`.
 */
internal data class HomeDeparture(
    val kind: Kind,
    val date: Instant,
) {
    enum class Kind {
        Train,
        Shuttle,
    }

    val name: String get() = if (kind == Kind.Train) "막차" else "셔틀"

    /** 출발까지 남은 분. 남는 초는 1분으로 올린다. */
    fun remainingMinutes(now: Instant): Int = ceil(Duration.between(now, date).toMillis() / 60_000.0).toInt()

    companion object {
        fun of(
            directions: FestivalPackDocument.Directions,
            walkMinutes: HomeWalkMinutes,
            now: Instant,
        ): HomeDeparture? {
            val upcoming =
                listOfNotNull(
                    candidate(Kind.Train, directions.lastTrainAt, walkMinutes.station, now),
                    candidate(Kind.Shuttle, directions.lastShuttleAt, walkMinutes.shuttleStop, now),
                )
            val chosen = upcoming.firstOrNull { it.isReachable(now) } ?: upcoming.firstOrNull() ?: return null
            return HomeDeparture(chosen.kind, chosen.date)
        }

        private class Candidate(
            val kind: Kind,
            val date: Instant,
            val walkMinutes: Int?,
        ) {
            /** 지금 걸어가도 출발 전에 닿는지. 도보 시간을 모르면 떠나기 전이면 닿는다고 본다. */
            fun isReachable(now: Instant): Boolean = Duration.between(now, date).toMillis() > (walkMinutes ?: 0) * 60_000L
        }

        private fun candidate(
            kind: Kind,
            value: String?,
            walkMinutes: Int?,
            now: Instant,
        ): Candidate? {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val date = departureDate(trimmed, now) ?: return null
            if (date <= now) return null
            return Candidate(kind, date, walkMinutes)
        }

        /** ISO 시각 또는 `HH:mm`. `HH:mm`이 저녁(18시 이후)에 본 새벽(6시 전) 시각이면 다음 날로 본다. */
        private fun departureDate(value: String, now: Instant): Instant? {
            FestivalIsoTime.parse(value)?.let { return it }
            val parts = value.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            val local = now.atZone(LiveBoardPresentation.zone)
            val sameDay = local.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!sameDay.toInstant().isAfter(now) && hour < 6 && local.hour >= 18) return sameDay.plusDays(1).toInstant()
            return sameDay.toInstant()
        }
    }
}
