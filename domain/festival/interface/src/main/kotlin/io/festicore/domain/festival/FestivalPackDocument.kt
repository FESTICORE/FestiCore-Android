package io.festicore.domain.festival

/**
 * 축제 팩 문서(온라인 상세 또는 받은 팩). 시각은 서버 문자열 그대로 두고 쓰는 쪽에서 [FestivalIsoTime]으로 해석한다.
 * iOS `FestivalPackDocument`.
 */
data class FestivalPackDocument(
    val id: String,
    val name: String? = null,
    val venue: String? = null,
    val thumbUrl: String? = null,
    val eventPeriod: EventPeriod? = null,
    val days: List<Day>? = null,
    val artists: List<Artist>? = null,
    val timetable: List<Performance>? = null,
    val sets: List<Performance>? = null,
    val stages: List<Stage>? = null,
    val flagsEnabled: Boolean? = null,
    val zones: List<Zone>? = null,
    val source: Source? = null,
    val ticketLinks: List<TicketLink>? = null,
) {
    fun zoneName(id: String?): String? = id?.let { zoneId -> zones?.firstOrNull { it.id == zoneId }?.name }

    /** 공연 목록. `timetable`이 있으면 그대로, 없으면 `sets`에 무대 이름(첫 무대 정의)을 채워 쓴다. */
    val performances: List<Performance>
        get() {
            timetable?.takeIf { it.isNotEmpty() }?.let { return it }
            val stageNames = mutableMapOf<String, String>()
            stages.orEmpty().forEach { stageNames.putIfAbsent(it.id, it.name) }
            return sets.orEmpty().map { it.copy(stageName = it.stageName ?: it.stageId?.let(stageNames::get)) }
        }

    /** 공연 시각과 별개인 축제 개최 기간. */
    data class EventPeriod(
        val firstDate: String,
        val lastDate: String,
    )

    data class Day(
        val date: String?,
        val startsAt: String?,
        val endsAt: String?,
    )

    data class TicketLink(
        val label: String,
        val url: String,
    )

    data class Zone(
        val id: String,
        val name: String,
    )

    data class Source(
        val name: String?,
        val url: String?,
        val verifiedAt: String?,
    )

    data class Artist(
        val name: String,
        val imageUrl: String?,
    )

    data class Stage(
        val id: String,
        val name: String,
    )

    data class Performance(
        val id: String,
        val artist: String? = null,
        val stageId: String? = null,
        val stageName: String? = null,
        val startsAt: String? = null,
        val endsAt: String? = null,
        val imageUrl: String? = null,
        /** 공연 상세 시트의 한 줄 메모. 공백만이면 null. */
        val note: String? = null,
    )
}
