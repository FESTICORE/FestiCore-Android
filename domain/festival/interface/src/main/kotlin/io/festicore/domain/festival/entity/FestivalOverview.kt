package io.festicore.domain.festival.entity

/** 목록 화면에 보이는 축제(카탈로그 + 저장된 팩). iOS `FestivalOverview`. */
data class FestivalOverview(
    val id: String,
    val name: String,
    val venue: String?,
    val firstDate: String?,
    val lastDate: String?,
    val thumbnailUrl: String?,
    val packVersion: Int,
    val isCached: Boolean,
    /** 기기에 저장된 팩 버전. 저장된 팩이 없으면 null. */
    val cachedPackVersion: Int?,
    /** 카탈로그 상태(`discovered` · `upcoming` · `live` · `ended`). 팩만 있는 항목은 null. */
    val catalogStatus: String?,
    /** 첫 번째 예매처 주소. */
    val ticketUrl: String?,
    /** null이면 서버나 캐시가 아티스트 검색 필드를 아직 주지 않는다. */
    val artists: List<FestivalCatalogArtist>?,
)
