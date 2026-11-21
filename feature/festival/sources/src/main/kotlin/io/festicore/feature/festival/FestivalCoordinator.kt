package io.festicore.feature.festival

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

data class FestivalCoordinatorState(
    val routes: List<NavKey> = listOf(FestivalRoute.List),
    /** 목록에서 고른 축제. 라이브 보드 단계에서 이 값으로 보드를 쌓는다. */
    val selectedFestivalId: String? = null,
)

/**
 * 라이브 탭 흐름(iOS `FestivalCoordinator`). 첫 화면은 목록(`routes = [.festivalList]`)이다.
 * 목록에서 축제를 고르면 ID를 받아 둔다. 라이브 보드 push(iOS `routes.push(.liveBoard)`)는 다음 단계에서 붙인다.
 */
class FestivalCoordinator :
    ViewModel(),
    OrbitContainerHost<FestivalCoordinatorState, FestivalCoordinatorState, Nothing> {
    override val container = orbitContainer<FestivalCoordinatorState, Nothing>(FestivalCoordinatorState())

    fun goBack() = intent { reduce { state.copy(routes = state.routes.dropLast(1).ifEmpty { state.routes }) } }

    /** iOS `festivalList(.view(.selectFestival(id)))`. */
    fun onFestivalSelected(festivalId: String) = intent { reduce { state.copy(selectedFestivalId = festivalId) } }
}
