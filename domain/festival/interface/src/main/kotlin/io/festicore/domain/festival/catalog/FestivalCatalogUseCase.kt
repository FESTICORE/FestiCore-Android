package io.festicore.domain.festival.catalog

import io.festicore.domain.festival.entity.FestivalCatalogError
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalCatalogPage

/** 축제 목록 읽기. 실패는 [FestivalCatalogError]. iOS `FestivalCatalogUseCaseInterface`(목록 부분). */
interface FestivalCatalogUseCase {
    /** 기기에 저장된 목록. 네트워크를 쓰지 않는다. */
    suspend fun cachedFestivals(): List<FestivalCatalogItem>

    /** 서버 목록 전체([PAGE_LIMIT]보다 큰 쪽으로 반복)를 받아 저장한다. */
    suspend fun refreshFestivals(): List<FestivalCatalogItem>

    /** 한 쪽. 첫 쪽 · 더 보기는 [PAGE_LIMIT]. */
    suspend fun festivalPage(after: String?, limit: Int = PAGE_LIMIT): FestivalCatalogPage

    fun imageUrl(value: String?): String?

    companion object {
        /** 첫 쪽 · 더 보기 크기. iOS `FestivalFeature` limit 10. */
        const val PAGE_LIMIT = 10
    }
}
