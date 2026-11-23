package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalCatalogItem
import io.festicore.domain.festival.FestivalCatalogPage
import io.festicore.domain.festival.FestivalCatalogRepository
import io.festicore.domain.festival.sources.model.dto.FestivalItemDto
import io.festicore.domain.festival.sources.model.mapper.mapCatalogErrors
import io.festicore.domain.festival.sources.model.mapper.toDomain
import io.festicore.domain.festival.sources.service.FestivalCatalogService

/** iOS `FestivalCatalogRepositoryImpl`. DTO를 도메인으로 바꾸고 오류를 [io.festicore.domain.festival.FestivalCatalogError]로 바꾼다. */
internal class FestivalCatalogRepositoryImpl(private val service: FestivalCatalogService) : FestivalCatalogRepository {
    override suspend fun cachedFestivals(): List<FestivalCatalogItem> = mapCatalogErrors { service.cached().toDomain() }

    override suspend fun refreshFestivals(): List<FestivalCatalogItem> = mapCatalogErrors { service.refresh().toDomain() }

    override suspend fun festivalPage(after: String?, limit: Int): FestivalCatalogPage = mapCatalogErrors {
        val page = service.page(after, limit)
        FestivalCatalogPage(items = page.items.toDomain(), nextCursor = page.nextCursor)
    }

    override fun imageUrl(value: String?): String? = service.imageUrl(value)

    private fun List<FestivalItemDto>.toDomain() = map { it.toDomain(service::imageUrl) }
}
