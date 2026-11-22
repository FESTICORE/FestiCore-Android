package io.festicore.domain.timetable

/** 격자에 그리는 내 일정 겹침 구간(선택한 날 0시부터의 분). iOS `TimetableOverlap`. */
data class TimetableOverlap(
    val start: Int,
    val end: Int,
) {
    val minutes: Int get() = end - start
}
