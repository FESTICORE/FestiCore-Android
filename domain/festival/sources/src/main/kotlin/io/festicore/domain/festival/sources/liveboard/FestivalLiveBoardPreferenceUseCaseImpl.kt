package io.festicore.domain.festival.sources.liveboard

import io.festicore.core.storage.shared.LiveBoardPreferenceStore
import io.festicore.domain.festival.liveboard.FestivalLiveBoardPreferenceUseCase
/** iOS `FestivalSelectionStore` · `FestivalGuideVisibilityStore`의 저장을 [LiveBoardPreferenceStore]에 맡긴다. */
internal class FestivalLiveBoardPreferenceUseCaseImpl(private val store: LiveBoardPreferenceStore) : FestivalLiveBoardPreferenceUseCase {
    override suspend fun savedFestivalId(): String? = store.selectedFestivalId()

    override suspend fun saveSelectedFestivalId(festivalId: String) = store.saveSelectedFestivalId(festivalId)

    override suspend fun isFollowedFlagGuideHidden(festivalId: String): Boolean = store.isFollowedFlagGuideHidden(festivalId)

    override suspend fun hideFollowedFlagGuide(festivalId: String) = store.hideFollowedFlagGuide(festivalId)
}
