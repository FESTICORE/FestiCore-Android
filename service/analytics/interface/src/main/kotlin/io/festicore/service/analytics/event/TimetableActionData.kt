package io.festicore.service.analytics.event

/** iOS `AnalyticsTimetableAction`. */
enum class AnalyticsTimetableAction(val rawValue: String) {
    SetOpen("set_open"),
    ScheduleToggle("schedule_toggle"),
    MineOnlyToggle("mine_only_toggle"),
    DaySelect("day_select"),
}

/** iOS `TimetableActionData`. [setId]는 공연 단위 조작일 때만 보낸다. */
data class TimetableActionData(
    val action: AnalyticsTimetableAction,
    val festivalId: String,
    val setId: String? = null,
)
