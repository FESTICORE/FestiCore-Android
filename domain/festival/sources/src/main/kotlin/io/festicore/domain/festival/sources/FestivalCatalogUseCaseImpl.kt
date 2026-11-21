package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalCatalogItem
import io.festicore.domain.festival.FestivalCatalogPage
import io.festicore.domain.festival.FestivalCatalogRepository
import io.festicore.domain.festival.FestivalCatalogUseCase

/** iOS `FestivalCatalogUseCaseImpl`(목록 부분). 선택 저장은 라이브 보드 단계에서 붙인다. */
internal class FestivalCatalogUseCaseImpl(private val repository: FestivalCatalogRepository) : FestivalCatalogUseCase {
    override suspend fun cachedFestivals(): List<FestivalCatalogItem> = repository.cachedFestivals()

    override suspend fun refreshFestivals(): List<FestivalCatalogItem> = repository.refreshFestivals()

    override suspend fun festivalPage(after: String?, limit: Int): FestivalCatalogPage = repository.festivalPage(after, limit)

    override fun imageUrl(value: String?): String? = repository.imageUrl(value)
}
