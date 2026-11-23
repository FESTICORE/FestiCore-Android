package io.festicore.feature.festival.notificationinbox

import androidx.annotation.DrawableRes
import io.festicore.domain.usernotification.entity.UserNotificationItem
import io.festicore.feature.festival.liveboard.LiveBoardPresentation
import io.festicore.ui.designkit.R
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 알림함 · 보드 배너 표시 규칙(iOS `NotificationInboxPresentation`). 날짜는 서울 달력으로 본다(iOS `AppDateFormat.calendar`). */
object NotificationInboxPresentation {
    data class Section(
        val id: String,
        val title: String,
        val items: List<UserNotificationItem>,
    )

    private val zone = LiveBoardPresentation.zone
    private val dayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withZone(zone)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(zone)
    private val monthDayTimeFormat = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US).withZone(zone)

    /** 받은 순서를 지키며 서울 날짜별로 묶는다. 오늘 "오늘", 어제 "어제", 그 밖 `yyyy-MM-dd`. */
    fun sections(items: List<UserNotificationItem>, now: Instant): List<Section> {
        val today = day(now)
        val grouped = LinkedHashMap<String, MutableList<UserNotificationItem>>()
        val titles = HashMap<String, String>()
        for (item in items) {
            val day = day(item.createdAt)
            val id = dayFormat.format(item.createdAt)
            titles[id] =
                when (day) {
                    today -> "오늘"
                    today.minusDays(1) -> "어제"
                    else -> id
                }
            grouped.getOrPut(id) { mutableListOf() } += item
        }
        return grouped.map { (id, sectionItems) -> Section(id, titles.getValue(id), sectionItems) }
    }

    /** 오늘 = "N분 전"류(없으면 "방금"), 어제 = "어제 HH:mm", 그 밖 `MM/dd HH:mm`. */
    fun time(item: UserNotificationItem, now: Instant): String {
        val day = day(item.createdAt)
        val today = day(now)
        return when (day) {
            today -> LiveBoardPresentation.relativeTime(item.createdAt, now) ?: "방금"
            today.minusDays(1) -> "어제 ${timeFormat.format(item.createdAt)}"
            else -> monthDayTimeFormat.format(item.createdAt)
        }
    }

    /** 알림 종류 접두사별 아이콘. Figma S01-1: 시간표 변경(`pack.`)은 달력 아이콘이다. */
    @DrawableRes
    fun icon(type: String): Int = when {
        type.startsWith("crew.") -> R.drawable.ic_users_24
        type.startsWith("flag.") -> R.drawable.ic_flag_16
        type.startsWith("festival.") -> R.drawable.ic_music_24
        type.startsWith("pack.") -> R.drawable.nav_timetable_inactive
        else -> R.drawable.ic_bell_16
    }

    /**
     * 라이브 보드 경로(`festivals/{id}/live`, 앞뒤 `/` 무시)이고 항목 축제와 같을 때만 그 축제 ID.
     * iOS `NotificationInboxFeature.liveFestivalID`.
     */
    fun liveFestivalId(item: UserNotificationItem): String? {
        val parts = item.route?.split("/")?.filter { it.isNotEmpty() } ?: return null
        val festivalId = item.festivalId ?: return null
        if (parts.size != 3 || parts[0] != "festivals" || parts[2] != "live" || parts[1] != festivalId) return null
        return festivalId
    }

    private fun day(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()
}
