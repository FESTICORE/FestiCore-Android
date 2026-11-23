package io.festicore.domain.festival.liveboard

import io.festicore.domain.festival.catalog.FestivalCatalogUseCase

/**
 * 라이브 보드가 기기에 남기는 값. iOS `FestivalCatalogUseCase.saveSelectedFestivalID`(선택 축제)와
 * `FestivalGuideVisibilityUseCaseInterface`(팔로우 안내 숨김)를 합친다.
 */
interface FestivalLiveBoardPreferenceUseCase {
    suspend fun savedFestivalId(): String?

    suspend fun saveSelectedFestivalId(festivalId: String)

    suspend fun isFollowedFlagGuideHidden(festivalId: String): Boolean

    suspend fun hideFollowedFlagGuide(festivalId: String)
}
