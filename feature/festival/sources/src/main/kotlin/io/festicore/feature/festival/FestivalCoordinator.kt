package io.festicore.feature.festival

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import io.festicore.domain.auth.CurrentUserProvider
import io.festicore.feature.festival.liveboard.LiveBoardBoundary
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

data class FestivalCoordinatorState(
    val routes: List<NavKey> = listOf(FestivalRoute.List),
    /** 목록에서 고른 축제. */
    val selectedFestivalId: String? = null,
    /** 맨 위 라이브 보드에 시트 · 다이얼로그가 떠 있다. */
    val isLiveBoardPresentingModal: Boolean = false,
    /** 보드가 마지막으로 알린, 아직 대상 화면이 없는 경계 이동(알림함 · 크루 등). 존 지도는 화면을 쌓는다. */
    val lastBoundary: LiveBoardBoundary? = null,
) {
    /**
     * 탭 바를 숨길지(iOS `MainTabCoordinator.shouldHideTabBar`의 라이브 탭 부분). 스택이 목록뿐이면 숨기고,
     * 맨 위 보드에 바꾸기 시트나 알림창이 떠 있으면 숨긴다(설계 D9). 존 지도는 탭 바를 보인다.
     */
    val hidesTabBar: Boolean
        get() = routes.size == 1 || (routes.lastOrNull() is FestivalRoute.LiveBoard && isLiveBoardPresentingModal)
}

/** 라이브 흐름 밖으로 나가는 이동. 상위(메인 탭)가 처리한다. */
sealed interface FestivalCoordinatorSideEffect {
    /** 깃발 탭으로 바꾼다(iOS `delegate(.flagTabRequested)`, 설계 E6). */
    data object FlagTabRequested : FestivalCoordinatorSideEffect
}

/**
 * 라이브 탭 흐름(iOS `FestivalCoordinator`). 첫 화면은 목록(`routes = [.festivalList]`)이다.
 * 목록에서 축제를 고르면 라이브 보드를 쌓고(iOS `routes.push(.liveBoard)`), 보드의 뒤로 · "전체 페스티벌 보기"는 pop이다.
 * 보드 크루 존 칩은 존 지도를 쌓는다(이미 있으면 무시). 계정이 바뀌면 계정의 팔로우 · 크루를 보여 주는 존 지도를 스택에서 뺀다(iOS `clearAccountData`, 설계 E9).
 */
class FestivalCoordinator(private val currentUserProvider: CurrentUserProvider) :
    ViewModel(),
    OrbitContainerHost<FestivalCoordinatorState, FestivalCoordinatorState, FestivalCoordinatorSideEffect> {
    /** 이 흐름을 만든 계정. 여기서 바뀔 때만 정리한다(구독 시작이 늦어도 놓치지 않게 만들 때 읽는다). */
    private var lastUserId = currentUserProvider.currentUserId.value

    override val container =
        orbitContainer<FestivalCoordinatorState, FestivalCoordinatorSideEffect>(FestivalCoordinatorState()) {
            currentUserProvider.currentUserId.collect { userId ->
                if (userId == lastUserId) return@collect
                lastUserId = userId
                onAccountChanged()
            }
        }

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

    /** 보드의 경계 이동. 존 지도는 쌓고(iOS `zoneMapRequested`), 대상 화면이 아직 없는 나머지는 받아 두기만 한다. */
    fun onLiveBoardBoundary(boundary: LiveBoardBoundary) = intent {
        when (boundary) {
            is LiveBoardBoundary.ZoneMap -> {
                if (state.routes.any { it is FestivalRoute.ZoneMap }) return@intent
                reduce { state.copy(routes = state.routes + FestivalRoute.ZoneMap(boundary.festivalId, boundary.zoneId)) }
            }

            else -> reduce { state.copy(lastBoundary = boundary) }
        }
    }

    /** 존 지도 "깃발을 팔로우하면…" 띠(iOS `zoneMap(.delegate(.flagDirectoryRequested))`): 보드로 돌아가 깃발 탭을 요청한다. */
    fun onZoneMapFlagDirectoryRequested() = intent {
        reduce { state.copy(routes = state.routes.dropLast(1).ifEmpty { state.routes }) }
        postSideEffect(FestivalCoordinatorSideEffect.FlagTabRequested)
    }

    private fun onAccountChanged() = intent {
        reduce { state.copy(routes = state.routes.filterNot { it is FestivalRoute.ZoneMap }) }
    }
}
