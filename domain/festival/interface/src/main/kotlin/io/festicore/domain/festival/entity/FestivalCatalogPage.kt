package io.festicore.domain.festival.entity

/** 목록 한 쪽. 마지막 쪽은 [nextCursor]가 null. iOS `FestivalCatalogPage`. */
data class FestivalCatalogPage(
    val items: List<FestivalCatalogItem>,
    val nextCursor: String?,
)
