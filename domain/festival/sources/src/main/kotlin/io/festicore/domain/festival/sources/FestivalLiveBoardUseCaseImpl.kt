package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalLiveBoardRepository
import io.festicore.domain.festival.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.FestivalLiveBoardUseCase
import io.festicore.domain.festival.FestivalOfficialNews
import io.festicore.domain.festival.FestivalPackDocument

/** iOS `FestivalLiveBoardUseCaseImpl` + 상세 조회. */
internal class FestivalLiveBoardUseCaseImpl(private val repository: FestivalLiveBoardRepository) : FestivalLiveBoardUseCase {
    override suspend fun liveBoard(festivalId: String): FestivalLiveBoardSnapshot = repository.liveBoard(festivalId)

    override suspend fun cachedLiveBoard(festivalId: String): FestivalLiveBoardSnapshot? = repository.cachedLiveBoard(festivalId)

    override suspend fun officialNews(festivalId: String): FestivalOfficialNews = repository.officialNews(festivalId)

    override suspend fun festivalDetail(festivalId: String): FestivalPackDocument = repository.festivalDetail(festivalId)
}
