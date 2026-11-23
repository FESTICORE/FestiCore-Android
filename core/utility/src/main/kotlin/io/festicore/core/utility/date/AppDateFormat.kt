package io.festicore.core.utility.date

import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 서버 시각 해석과 화면 날짜 문구. 화면 날짜는 서울 시간대로 만든다. iOS `AppDateFormat`(FestiCoreUtility).
 * 지금은 공지(`noticeDay`)에 쓰는 것만 옮겼다.
 */
object AppDateFormat {
    /** iOS `AppDateFormat.timeZone`. */
    val timeZone: ZoneId = ZoneId.of("Asia/Seoul")

    private val noticeDayFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.US).withZone(timeZone)

    // iOS ISO8601DateFormatter `withInternetDateTime`(소수초 있음/없음)와 같은 모양만 받는다.
    private val isoPattern = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|[+-]\d{2}:\d{2})$""")

    /** 공지 날짜 `yyyy.MM.dd`(서울 기준). iOS `AppDateFormat.noticeDay`. */
    fun noticeDay(instant: Instant): String = noticeDayFormatter.format(instant)

    /** ISO 8601 시각. 소수초가 있든 없든 받고, 모양이 다르면 null. iOS `String.isoDate`. */
    fun isoInstant(value: String): Instant? {
        if (!isoPattern.matches(value)) return null
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeException) {
            null
        }
    }
}
