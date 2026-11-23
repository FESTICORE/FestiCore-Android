package io.festicore.feature.festival

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.feature.festival.liveboard.LiveBoardBoundary
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

data class FestivalCoordinatorState(
    val routes: List<NavKey> = listOf(FestivalRoute.List),
    /** 목록에서 고른 축제. */
    val selectedFestivalId: String? = null,
    /** 맨 위 라이브 보드에 시트 · 다이얼로그가 떠 있다. */
    val isLiveBoardPresentingModal: Boolean = false,
    /** 보드가 마지막으로 알린, 아직 대상 화면이 없는 경계 이동(크루 등). 존 지도 · 알림함은 화면을 쌓는다. */
    val lastBoundary: LiveBoardBoundary? = null,
    /** 맨 위 보드가 바꿀 수 있는 축제 ID(보드 카탈로그). 알림이 연 축제가 여기 없으면 알림함에 팝업을 띄운다(iOS `board.catalog`). */
    val liveBoardFestivalIds: Set<String> = emptySet(),
    /** 알림함에서 연 축제. 보드가 받아 그 축제로 바꾼 뒤 비운다(설계 E8). */
    val pendingBoardFestivalId: String? = null,
) {
    /**
     * 탭 바를 숨길지(iOS `MainTabCoordinator.shouldHideTabBar`의 라이브 탭 부분). 스택이 목록뿐이면 숨기고,
     * 맨 위 보드에 바꾸기 시트나 알림창이 떠 있거나 알림함이 맨 위면 숨긴다(설계 D9 · "탭 바 표시"). 존 지도는 탭 바를 보인다.
     */
    val hidesTabBar: Boolean
        get() {
            val top = routes.lastOrNull()
            return routes.size == 1 ||
                (top is FestivalRoute.LiveBoard && isLiveBoardPresentingModal) ||
                top is FestivalRoute.NotificationInbox
        }
}

/** 라이브 흐름 밖으로 나가는 이동. 상위(메인 탭)가 처리한다. */
sealed interface FestivalCoordinatorSideEffect {
    /** 깃발 탭으로 바꾼다(iOS `delegate(.flagTabRequested)`, 설계 E6). */
    data object FlagTabRequested : FestivalCoordinatorSideEffect
}

/**
 * 라이브 탭 흐름(iOS `FestivalCoordinator`). 첫 화면은 목록(`routes = [.festivalList]`)이다.
 * 목록에서 축제를 고르면 라이브 보드를 쌓고(iOS `routes.push(.liveBoard)`), 보드의 뒤로 · "전체 페스티벌 보기"는 pop이다.
 * 보드 크루 존 칩은 존 지도를, 종 아이콘 · 새 알림 배너는 알림함을 쌓는다(이미 있으면 무시).
 * 계정이 바뀌면 계정의 팔로우 · 크루를 보여 주는 존 지도와 이전 계정의 알림함을 스택에서 뺀다(iOS `clearAccountData`, 설계 E9).
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

    /** 보드의 경계 이동. 존 지도 · 알림함은 쌓고(iOS `zoneMapRequested` · `notificationInboxRequested`), 대상 화면이 아직 없는 나머지는 받아 두기만 한다. */
    fun onLiveBoardBoundary(boundary: LiveBoardBoundary) = intent {
        when (boundary) {
            is LiveBoardBoundary.ZoneMap -> {
                if (state.routes.any { it is FestivalRoute.ZoneMap }) return@intent
                reduce { state.copy(routes = state.routes + FestivalRoute.ZoneMap(boundary.festivalId, boundary.zoneId)) }
            }

            // 보드가 세션을 확인하고 보낸다(비로그인은 Login 경계).
            LiveBoardBoundary.NotificationInbox -> {
                if (state.routes.any { it is FestivalRoute.NotificationInbox }) return@intent
                reduce { state.copy(routes = state.routes + FestivalRoute.NotificationInbox) }
            }

            else -> reduce { state.copy(lastBoundary = boundary) }
        }
    }

    /** 존 지도 "깃발을 팔로우하면…" 띠(iOS `zoneMap(.delegate(.flagDirectoryRequested))`): 보드로 돌아가 깃발 탭을 요청한다. */
    fun onZoneMapFlagDirectoryRequested() = intent {
        reduce { state.copy(routes = state.routes.dropLast(1).ifEmpty { state.routes }) }
        postSideEffect(FestivalCoordinatorSideEffect.FlagTabRequested)
    }

    /** 보드가 바꿀 수 있는 축제 목록이 바뀌었다(알림 열기 판단용). */
    fun onLiveBoardFestivalsChanged(festivalIds: Set<String>) = intent {
        reduce { state.copy(liveBoardFestivalIds = festivalIds) }
    }

    /**
     * 알림함에서 라이브 보드 알림을 열었다(iOS `notificationInbox(.delegate(.rowOpened))`). 보드에 그 축제가 없으면 `false`를 돌려주고
     * 알림함에 남는다. 있으면 알림함을 pop하고 보드에 축제 전환을 맡긴다(설계 E8, 같은 축제면 보드가 무시한다).
     */
    fun onNotificationFestivalOpened(festivalId: String): Boolean {
        val current = container.stateFlow.value
        if (current.routes.none { it is FestivalRoute.LiveBoard } || festivalId !in current.liveBoardFestivalIds) return false
        intent {
            reduce {
                state.copy(
                    routes = state.routes.filterNot { it is FestivalRoute.NotificationInbox },
                    pendingBoardFestivalId = festivalId,
                )
            }
        }
        return true
    }

    /** 딥링크로 축제 목록을 연다(iOS `showFestivalList`). 목록 위 화면을 모두 뺀다. */
    fun onDeeplinkFestivalListOpened() = intent {
        reduce { state.copy(routes = listOf(FestivalRoute.List), isLiveBoardPresentingModal = false) }
    }

    /**
     * 딥링크로 축제 라이브 보드를 연다(iOS `openWidgetFestival` → `widgetPackValidated`). 목록 위 화면을 빼고 그 축제 보드를 쌓는다.
     * 같은 보드가 이미 열려 있으면 보드가 다른 축제로 바뀌었을 수 있어 대기 축제로 넘긴다(같은 축제면 보드가 무시한다).
     * 저장된 팩이 없는 축제 · 캐시 오류 안내(iOS `widgetPackLookupFailed`)는 P0b에서 옮긴다.
     */
    fun onDeeplinkFestivalOpened(festivalId: String) = intent {
        val routes = listOf(FestivalRoute.List, FestivalRoute.LiveBoard(festivalId))
        reduce {
            state.copy(
                routes = routes,
                selectedFestivalId = festivalId,
                isLiveBoardPresentingModal = false,
                pendingBoardFestivalId = if (state.routes == routes) festivalId else null,
            )
        }
    }

    /** 보드가 대기 축제를 받았다. */
    fun onPendingBoardFestivalConsumed() = intent { reduce { state.copy(pendingBoardFestivalId = null) } }

    private fun onAccountChanged() = intent {
        reduce {
            state.copy(
                routes = state.routes.filterNot { it is FestivalRoute.ZoneMap || it is FestivalRoute.NotificationInbox },
                pendingBoardFestivalId = null,
            )
        }
    }
}
