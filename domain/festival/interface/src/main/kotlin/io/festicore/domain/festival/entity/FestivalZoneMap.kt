package io.festicore.domain.festival.entity

/** S06 존 지도. 받아 둔 팩의 존 영역 · 무대 머리글과 지도 버전. iOS `FestivalZoneMap`. */
data class FestivalZoneMap(
    val festivalId: String,
    /** 지도 데이터가 바뀔 때만 바뀌는 버전(`mapVersion`). */
    val version: String?,
    /** 화면은 쓰지 않는다(iOS와 같음, 설계 E2). */
    val imageUrl: String?,
    /** 지도 너비/높이. 0 이하면 null. */
    val aspect: Double?,
    /** 팩의 깃발 기능 사용 여부. 꺼져 있으면 크루만 보여 준다. */
    val flagsEnabled: Boolean,
    /** `order` 순서. */
    val zones: List<Zone>,
    val stageHeaders: List<StageHeader>,
) {
    /** 영역이 있는 존이 하나라도 있으면 지도를 그린다. 없으면 존 목록이다. */
    val hasLayout: Boolean get() = aspect != null && zones.any { it.area != null }

    fun zone(id: String?): Zone? = zones.firstOrNull { it.id == id }

    data class Zone(
        val id: String,
        val name: String,
        val kind: String?,
        val area: Area?,
        val landmark: String?,
    )

    /** 0~1 비율 사각형. */
    data class Area(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
    )

    /** 무대 이름과 기준 시각에 공연 중인 아티스트를 보여 주는 머리글 줄. */
    data class StageHeader(
        val stageId: String,
        val name: String,
        val x: Double,
        val y: Double,
        val width: Double,
        val nowPlaying: String?,
    )
}
