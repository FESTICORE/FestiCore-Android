package io.festicore.domain.festival.sources.repository

import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalOfficialNews
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.liveboard.FestivalLiveBoardRepository
import io.festicore.domain.festival.sources.model.mapper.mapLiveBoardErrors
import io.festicore.domain.festival.sources.model.mapper.toDomain
import io.festicore.domain.festival.sources.service.FestivalCatalogService
import io.festicore.domain.festival.sources.service.FestivalLiveBoardService
import io.festicore.domain.festival.sources.service.FestivalOfficialNewsService

/** DTO를 엔티티로 바꾸고 오류를 [io.festicore.domain.festival.entity.FestivalLiveBoardError]로 바꾼다. */
internal class FestivalLiveBoardRepositoryImpl(
    private val liveBoardService: FestivalLiveBoardService,
    private val officialNewsService: FestivalOfficialNewsService,
    private val catalogService: FestivalCatalogService,
) : FestivalLiveBoardRepository {
    override suspend fun liveBoard(festivalId: String): FestivalLiveBoardSnapshot = mapLiveBoardErrors {
        val fetch = liveBoardService.refresh(festivalId)
        fetch.board.toDomain(fetch.flags, catalogService::imageUrl, fetch.receivedAt)
    }

    override suspend fun cachedLiveBoard(festivalId: String): FestivalLiveBoardSnapshot? = mapLiveBoardErrors {
        liveBoardService.cachedFetch(festivalId)?.let { fetch ->
            fetch.board.toDomain(fetch.flags, catalogService::imageUrl, fetch.receivedAt)
        }
    }

    override suspend fun officialNews(festivalId: String): FestivalOfficialNews = mapLiveBoardErrors {
        officialNewsService.officialNews(festivalId).toDomain()
    }

    override suspend fun festivalDetail(festivalId: String): FestivalPackDocument = mapLiveBoardErrors {
        catalogService.festivalDetail(festivalId).toDomain()
    }
}
