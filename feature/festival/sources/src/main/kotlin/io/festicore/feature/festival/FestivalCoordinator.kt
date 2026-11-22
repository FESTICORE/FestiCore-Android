package io.festicore.feature.festival

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import io.festicore.feature.festival.liveboard.LiveBoardBoundary
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

data class FestivalCoordinatorState(
    val routes: List<NavKey> = listOf(FestivalRoute.List),
    /** 목록에서 고른 축제. */
    val selectedFestivalId: String? = null,
    /** 맨 위 라이브 보드에 시트 · 다이얼로그가 떠 있다. */
    val isLiveBoardPresentingModal: Boolean = false,
    /** 보드가 마지막으로 알린 경계 이동. 대상 화면(알림함 · 존 지도 · 크루 등)은 다음 문서에서 붙인다. */
    val lastBoundary: LiveBoardBoundary? = null,
) {
    /**
     * 탭 바를 숨길지(iOS `MainTabCoordinator.shouldHideTabBar`의 라이브 탭 부분). 스택이 목록뿐이면 숨기고,
     * 맨 위 보드에 바꾸기 시트나 알림창이 떠 있으면 숨긴다(설계 D9).
     */
    val hidesTabBar: Boolean
        get() = routes.size == 1 || (routes.lastOrNull() is FestivalRoute.LiveBoard && isLiveBoardPresentingModal)
}

/**
 * 라이브 탭 흐름(iOS `FestivalCoordinator`). 첫 화면은 목록(`routes = [.festivalList]`)이다.
 * 목록에서 축제를 고르면 라이브 보드를 쌓고(iOS `routes.push(.liveBoard)`), 보드의 뒤로 · "전체 페스티벌 보기"는 pop이다.
 */
class FestivalCoordinator :
    ViewModel(),
    OrbitContainerHost<FestivalCoordinatorState, FestivalCoordinatorState, Nothing> {
    override val container = orbitContainer<FestivalCoordinatorState, Nothing>(FestivalCoordinatorState())

    fun goBack() = intent {
        reduce { state.copy(routes = state.routes.dropLast(1).ifEmpty { state.routes }, isLiveBoardPresentingModal = false) }
    }

    /** iOS `festivalList(.view(.selectFestival(id)))`. 같은 보드가 이미 위에 있으면 다시 쌓지 않는다. */
    fun onFestivalSelected(festivalId: String) = intent {
        val route = FestivalRoute.LiveBoard(festivalId)
        reduce {
            state.copy(
                selectedFestivalId = festivalId,
                routes = if (state.routes.lastOrNull() == route) state.routes else state.routes + route,
            )
        }
    }

    /** 보드 시트 · 다이얼로그 표시가 바뀌었다(탭 바 숨김). */
    fun onLiveBoardPresentingModalChanged(isPresenting: Boolean) = intent {
        reduce { state.copy(isLiveBoardPresentingModal = isPresenting) }
    }

    /** 보드의 경계 이동(설계 "경계만 표시"). 1차는 받아 두기만 한다. */
    fun onLiveBoardBoundary(boundary: LiveBoardBoundary) = intent { reduce { state.copy(lastBoundary = boundary) } }
}
