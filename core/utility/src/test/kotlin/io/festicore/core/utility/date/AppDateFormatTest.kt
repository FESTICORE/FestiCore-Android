package io.festicore.core.utility.date

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `AppDateFormatTests`의 `noticeDay` 2건(같은 이름). */
class AppDateFormatTest {
    @Test
    fun dottedDayUsesSeoulDayAcrossUTCMidnightBoundary() {
        val date = AppDateFormat.isoInstant("2026-11-03T15:30:00Z")

        assertNotNull(date)
        assertEquals("2026.11.04", AppDateFormat.noticeDay(date!!))
    }

    // iOS는 위와 같은 입력을 쓴다. 여기서는 서울 자정 바로 앞뒤를 함께 본다.
    @Test
    fun noticeDayUsesSeoulDateAcrossUTCMidnight() {
        assertEquals("2026.11.03", AppDateFormat.noticeDay(Instant.parse("2026-11-03T14:59:59Z")))
        assertEquals("2026.11.04", AppDateFormat.noticeDay(Instant.parse("2026-11-03T15:00:00Z")))
    }

    // iOS `isoParsingAcceptsFractionalAndWholeSeconds`.
    @Test
    fun isoParsingAcceptsFractionalAndWholeSeconds() {
        assertNotNull(AppDateFormat.isoInstant("2026-10-30T12:00:00+09:00"))
        assertNotNull(AppDateFormat.isoInstant("2026-10-30T12:00:00.250+09:00"))
        assertNull(AppDateFormat.isoInstant("2026-10-30 12:00:00"))
    }
}
