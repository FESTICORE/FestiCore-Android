package io.festicore.feature.profile.notice

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 공지 화면 문구. */
internal object NoticePresentation {
    // iOS `AppDateFormat.noticeDay`(`yyyy.MM.dd`, en_US_POSIX, 서울 시간대). feature는 core를 의존하지 않아 같은 패턴을 둔다.
    private val noticeDay = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.US).withZone(ZoneId.of("Asia/Seoul"))

    /** 게시 날짜(서울 기준). */
    fun day(publishedAt: Instant): String = noticeDay.format(publishedAt)
}
