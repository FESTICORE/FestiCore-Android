package io.festicore.domain.festival.sources.liveboard

import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalOfficialNews
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.liveboard.FestivalLiveBoardRepository
import io.festicore.domain.festival.liveboard.FestivalLiveBoardUseCase
/** iOS `FestivalLiveBoardUseCaseImpl` + 상세 조회. */
internal class FestivalLiveBoardUseCaseImpl(private val repository: FestivalLiveBoardRepository) : FestivalLiveBoardUseCase {
    override suspend fun liveBoard(festivalId: String): FestivalLiveBoardSnapshot = repository.liveBoard(festivalId)

    override suspend fun cachedLiveBoard(festivalId: String): FestivalLiveBoardSnapshot? = repository.cachedLiveBoard(festivalId)

    override suspend fun officialNews(festivalId: String): FestivalOfficialNews = repository.officialNews(festivalId)

    override suspend fun festivalDetail(festivalId: String): FestivalPackDocument = repository.festivalDetail(festivalId)
}
