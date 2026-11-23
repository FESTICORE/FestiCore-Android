package io.festicore.domain.festival.entity

import java.time.Instant

/**
 * 받은 팩의 가는 길 · 준비물 · 반입 금지. 라이브 보드가 쓰는 [FestivalPackDocument.Directions]와 별개로 같은 팩 JSON을 다시 읽는다.
 * 주소는 https · 호스트가 있는 것만 둔다. iOS `FestivalPackDetail`.
 */
data class FestivalPackDetail(
    val pack: FestivalReceivedPack,
    val directions: Directions = Directions(),
    val checklist: List<ChecklistItem> = emptyList(),
    val rules: List<String> = emptyList(),
    /** 팩 전체 출처. */
    val source: Source? = null,
) {
    data class Directions(
        /** 서버가 적은 그대로의 마지막 셔틀 · 막차 시각. */
        val lastShuttleAt: String? = null,
        val lastTrainAt: String? = null,
        val shuttle: Section? = null,
        val transit: Section? = null,
        val parking: Section? = null,
        /** 운영팀 추천 메모. */
        val memo: String? = null,
        val naverMapUrl: String? = null,
        val kakaoMapUrl: String? = null,
        /** 출처를 확인한 축제 장소 좌표. */
        val venueLocation: Coordinate? = null,
        val source: Source? = null,
    ) {
        /** 보여 줄 가는 길 정보가 하나도 없는지. */
        val isEmpty: Boolean
            get() = lastShuttleAt == null && lastTrainAt == null && shuttle == null && transit == null &&
                parking == null && memo == null && naverMapUrl == null && kakaoMapUrl == null
    }

    data class Section(
        val body: String?,
        val links: List<Link> = emptyList(),
        /** 대중교통의 가까운 역 이름. */
        val stationName: String? = null,
        /** 셔틀 정류장 이름. */
        val stopName: String? = null,
        val walkMinutes: Int? = null,
        /** 서버가 준 역 · 정류장 좌표. */
        val location: Coordinate? = null,
    )

    data class Coordinate(
        val latitude: Double,
        val longitude: Double,
    )

    data class Link(
        val label: String,
        val url: String,
    )

    data class ChecklistItem(
        val id: String,
        val label: String,
        /** 운영팀이 정한 필수 항목. 지울 수 없다. */
        val required: Boolean = false,
        val note: String? = null,
    )

    data class Source(
        val name: String,
        val verifiedAt: Instant?,
    )
}
