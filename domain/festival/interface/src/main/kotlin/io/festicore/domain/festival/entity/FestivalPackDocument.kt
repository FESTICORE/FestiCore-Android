package io.festicore.domain.festival.entity

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
    /** 가는 길 중 귀가 카드에 쓰는 값. 형식이 틀리거나 없으면 null. */
    val directions: Directions? = null,
    /** 존 지도 배경 이미지. 화면은 쓰지 않는다(iOS와 같음, 설계 E2). */
    val mapImageUrl: String? = null,
    /** 지도 너비/높이. */
    val mapAspect: Double? = null,
    /** 지도 데이터가 바뀔 때만 바뀌는 버전. */
    val mapVersion: String? = null,
    /** 지도 위 무대 머리글 자리. */
    val mapStages: List<MapStage>? = null,
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

    /** iOS `FestivalPackDetail.Directions`에서 귀가 카드가 읽는 값만. */
    data class Directions(
        /** 서버가 적은 그대로의 마지막 셔틀 · 막차 시각(ISO 또는 `HH:mm`). */
        val lastShuttleAt: String? = null,
        val lastTrainAt: String? = null,
        val shuttleStopName: String? = null,
        val transitStationName: String? = null,
        /** 서버가 적은 역까지 도보 분. */
        val transitWalkMinutes: Int? = null,
        /** 서버가 준 셔틀 정류장 · 역 좌표. 귀가 "가는 길"의 목적지로 쓴다. */
        val shuttleLocation: FestivalPackDetail.Coordinate? = null,
        val transitLocation: FestivalPackDetail.Coordinate? = null,
        /** 축제 장소 좌표. 목적지 좌표가 없을 때 이름을 이 근처에서 찾는다. */
        val venueLocation: FestivalPackDetail.Coordinate? = null,
    )

    data class TicketLink(
        val label: String,
        val url: String,
    )

    data class Zone(
        val id: String,
        val name: String,
        /** 존 카드 보조 문구(예: 무대 앞). */
        val kind: String? = null,
        /** 존 지도 정렬 순서. */
        val order: Int? = null,
        val area: Area? = null,
        /** 영역 오른쪽 위 표지(예: TENT 무대). */
        val landmark: String? = null,
    )

    /** 지도 왼쪽 위 기준 0~1 비율 사각형. */
    data class Area(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
    )

    /** 무대 머리글 자리(0~1 비율, 높이는 화면 고정). */
    data class MapStage(
        val stageId: String,
        val x: Double,
        val y: Double,
        val width: Double,
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
