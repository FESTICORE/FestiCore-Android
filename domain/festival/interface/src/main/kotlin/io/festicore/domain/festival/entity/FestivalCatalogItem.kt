package io.festicore.domain.festival.entity

/** 서버 · 캐시에서 받은 축제 한 건. 날짜는 `yyyy-MM-dd` 문자열 그대로. iOS `FestivalCatalogItem`. */
data class FestivalCatalogItem(
    val id: String,
    val name: String,
    val venue: String? = null,
    val firstDate: String? = null,
    val lastDate: String? = null,
    /** 불러올 수 있는 https 주소로 바꾼 썸네일. 없거나 쓸 수 없으면 null. */
    val thumbnailUrl: String? = null,
    val packVersion: Int,
    val status: String? = null,
    /** 이름이 있고 https인 예매처만. */
    val ticketLinks: List<FestivalTicketLink> = emptyList(),
    /** null은 서버나 캐시에 이 필드가 없었다는 뜻이다. */
    val artists: List<FestivalCatalogArtist>? = null,
)

data class FestivalCatalogArtist(
    val name: String,
    val performances: List<FestivalCatalogPerformance>,
)

data class FestivalCatalogPerformance(
    val startsAt: String,
    val date: String,
    val stageName: String?,
)

data class FestivalTicketLink(
    val label: String,
    val url: String,
)
