package io.festicore.domain.festival.sources.selection

import io.festicore.domain.festival.entity.FestivalBoardSelection
import io.festicore.domain.festival.selection.FestivalBoardSelectionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** 보드 선택 공유 저장소(설계 T1, Koin `single`). 메모리에만 두고 기기에 남기지 않는다(선택 축제 ID 저장은 [io.festicore.domain.festival.liveboard.FestivalLiveBoardPreferenceUseCase]). */
internal class FestivalBoardSelectionStore : FestivalBoardSelectionUseCase {
    private val state = MutableStateFlow<FestivalBoardSelection?>(null)
    private val retries = MutableSharedFlow<String>(extraBufferCapacity = 1)

    override val selection: StateFlow<FestivalBoardSelection?> = state.asStateFlow()

    override val retryRequests: Flow<String> = retries.asSharedFlow()

    override fun update(selection: FestivalBoardSelection) {
        state.value = selection
    }

    override fun clear() {
        state.value = null
    }

    override suspend fun requestRetry(festivalId: String) = retries.emit(festivalId)
}
