package io.festicore.domain.festival.entity

/** Device-local view projection. Never contains credentials or member identities. */
data class FestivalWidgetSnapshot(
    val festivalId: String,
    val festivalName: String,
    val packVersion: Int,
    val etag: String,
    val performances: List<Performance> = emptyList(),
    val days: List<Day> = emptyList(),
    val scheduleKnownForOwner: Boolean = false,
    val pinnedFlagSummary: String? = null,
    val crewSummary: String? = null,
    val lastShuttle: String? = null,
    val lastTrain: String? = null,
    val shuttleStopName: String? = null,
    val transitStationName: String? = null,
) {
    data class Performance(
        val id: String,
        val artist: String,
        val stageId: String,
        val stageName: String,
        val startsAt: Long,
        val endsAt: Long?,
        val isSaved: Boolean = false,
    )
    data class Day(
        val date: String,
        val opensAt: Long?,
    )
}
