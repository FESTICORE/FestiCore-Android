package io.festicore.domain.festival.catalog

import io.festicore.domain.festival.entity.FestivalCatalogError
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalCatalogPage

/** 축제 목록 저장소. 실패는 [FestivalCatalogError]. iOS `FestivalCatalogRepositoryInterface`. */
interface FestivalCatalogRepository {
    /** 기기에 저장된 목록. 네트워크를 쓰지 않는다. 없으면 빈 목록. */
    suspend fun cachedFestivals(): List<FestivalCatalogItem>

    /** 서버 목록 전체를 다시 받아 저장한다. */
    suspend fun refreshFestivals(): List<FestivalCatalogItem>

    /** 한 쪽. [after]가 null이면 첫 쪽. 마지막 쪽을 받으면 첫 쪽부터 모은 목록을 저장한다. */
    suspend fun festivalPage(after: String?, limit: Int): FestivalCatalogPage

    /** 썸네일 값을 불러올 https 주소로 바꾼다. 쓸 수 없으면 null. */
    fun imageUrl(value: String?): String?
}
