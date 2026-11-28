package io.festicore.domain.festival.sources.liveboard

import io.festicore.core.storage.shared.LiveBoardPreferenceStore
import io.festicore.domain.festival.liveboard.FestivalLiveBoardPreferenceUseCase
import io.festicore.domain.festival.sources.widget.FestivalWidgetRepository
import io.festicore.domain.festival.sources.widget.widgetAttempt
/** iOS `FestivalSelectionStore` · `FestivalGuideVisibilityStore`의 저장을 [LiveBoardPreferenceStore]에 맡긴다. */
internal class FestivalLiveBoardPreferenceUseCaseImpl(
    private val store: LiveBoardPreferenceStore,
    private val widget: FestivalWidgetRepository? = null,
) : FestivalLiveBoardPreferenceUseCase {
    override suspend fun savedFestivalId(): String? = store.selectedFestivalId()

    override suspend fun saveSelectedFestivalId(festivalId: String) {
        val previous = widgetAttempt { store.selectedFestivalId() }.value
        store.saveSelectedFestivalId(festivalId)
        widgetAttempt { widget?.selectionChanged(previous, festivalId) }
    }

    override suspend fun isFollowedFlagGuideHidden(festivalId: String): Boolean = store.isFollowedFlagGuideHidden(festivalId)

    override suspend fun hideFollowedFlagGuide(festivalId: String) = store.hideFollowedFlagGuide(festivalId)
}
