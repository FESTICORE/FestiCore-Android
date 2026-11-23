package io.festicore.domain.festival.entity

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** 서버 ISO 8601 시각 해석. iOS `String.isoDate`(`withInternetDateTime`, 소수초 있음/없음). */
object FestivalIsoTime {
    private val pattern = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$""")

    /** `yyyy-MM-ddTHH:mm:ss[.S…](Z|±HH:mm)`만 받는다. 그 밖은 null. */
    fun parse(value: String?): Instant? {
        if (value == null || !pattern.matches(value)) return null
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
