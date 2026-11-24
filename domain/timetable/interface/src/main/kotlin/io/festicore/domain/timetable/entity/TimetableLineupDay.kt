package io.festicore.domain.timetable.entity

/** 공개 전 라인업 묶음. iOS `TimetableLineupDay`. */
data class TimetableLineupDay(
    val id: String,
    val title: String,
    val artists: List<TimetableLineupArtist>,
)

data class TimetableLineupArtist(
    val name: String,
    val imageUrl: String? = null,
)
