package io.festicore.feature.festival

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.feature.festival.flag.FlagBoard
import io.festicore.feature.festival.liveboard.LiveBoardBoundary
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

data class FestivalCoordinatorState(
    val routes: List<NavKey> = listOf(FestivalRoute.List),
    /** 목록에서 고른 축제. */
    val selectedFestivalId: String? = null,
    /** 맨 위 라이브 보드에 시트 · 다이얼로그가 떠 있다. */
    val isLiveBoardPresentingModal: Boolean = false,
    /** 보드가 마지막으로 알린, 아직 대상 화면이 없는 경계 이동(기수). 존 지도 · 알림함 · 크루 · 크루 만들기는 화면을 쌓고 로그인 · 깃발 찾기 · 가는 길은 상위에 알린다. */
    val lastBoundary: LiveBoardBoundary? = null,
    /** 맨 위 보드가 바꿀 수 있는 축제 ID(보드 카탈로그). 알림이 연 축제가 여기 없으면 알림함에 팝업을 띄운다(iOS `board.catalog`). */
    val liveBoardFestivalIds: Set<String> = emptySet(),
    /** 알림함에서 연 축제. 보드가 받아 그 축제로 바꾼 뒤 비운다(설계 E8). */
    val pendingBoardFestivalId: String? = null,
    /** 보드가 마지막으로 알린 깃발 탭 기준 값. 알린 보드 경로가 지금 맨 위 보드가 아니면 쓰지 않는다([flagBoard]). */
    val liveBoard: FlagBoard? = null,
    /** [liveBoard]를 알린 보드 경로. */
    val liveBoardRoute: FestivalRoute.LiveBoard? = null,
) {
    /** 깃발 탭이 따르는 보드(iOS `flagBoard`). 존 지도 · 알림함 · 크루가 위에 있어도 그 아래 보드를 기준으로 한다. 보드가 없으면 `null`. */
    val flagBoard: FlagBoard?
        get() = liveBoard?.takeIf { liveBoardRoute != null && routes.lastOrNull { it is FestivalRoute.LiveBoard } == liveBoardRoute }

    /**
     * 탭 바를 숨길지(iOS `MainTabCoordinator.shouldHideTabBar`의 라이브 탭 부분). 스택이 목록뿐이면 숨기고,
     * 맨 위 보드에 바꾸기 시트나 알림창이 떠 있거나 알림함 · 크루 · 크루 만들기 · 참여 확인이 맨 위면 숨긴다(설계 D9 · "탭 바 표시"). 존 지도는 탭 바를 보인다.
     */
    val hidesTabBar: Boolean
        get() {
            val top = routes.lastOrNull()
            return routes.size == 1 ||
                (top is FestivalRoute.LiveBoard && isLiveBoardPresentingModal) ||
                top is FestivalRoute.NotificationInbox ||
                // 크루 · 만들기 · 참여 확인은 아래 고정 영역이 있어 탭 바 없이 보인다(iOS `isCrewOnTop`).
                top is FestivalRoute.Crew ||
                top is FestivalRoute.CrewCreate ||
                top is FestivalRoute.CrewJoin
        }
}

/** 라이브 흐름 밖으로 나가는 이동. 상위(메인 탭)가 처리한다. */
sealed interface FestivalCoordinatorSideEffect {
    /** 깃발 탭으로 바꾼다(iOS `delegate(.flagTabRequested)`, 설계 E6). */
    data object FlagTabRequested : FestivalCoordinatorSideEffect

    /** 로그인 시트를 요청한다(iOS `delegate(.loginRequested)` 등). [context]가 `null`이면 상위가 탭 기본 문맥을 쓴다. */
    data class LoginRequested(val context: FestivalLoginContext?) : FestivalCoordinatorSideEffect

    /** 귀가 "가는 길". 마이 탭으로 바꿔 그 축제의 팩 상세를 연다(iOS `delegate(.packDirectionsRequested)`, 설계 "화면 전환"). */
    data class PackDirectionsRequested(
        val festivalId: String,
        val title: String,
    ) : FestivalCoordinatorSideEffect
}

/**
 * 라이브 탭 흐름(iOS `FestivalCoordinator`). 첫 화면은 목록(`routes = [.festivalList]`)이다.
 * 목록에서 축제를 고르면 라이브 보드를 쌓고(iOS `routes.push(.liveBoard)`), 보드의 뒤로 · "전체 페스티벌 보기"는 pop이다.
 * 보드 크루 존 칩은 존 지도를, 종 아이콘 · 새 알림 배너는 알림함을 쌓는다(이미 있으면 무시).
 * 보드 "크루 만들기"는 만들기를, 크루 카드는 크루 화면을 쌓는다. 초대 코드는 참여 확인을 쌓는다(spec `crew.md`).
 * 계정이 바뀌면 계정의 팔로우 · 크루를 보여 주는 존 지도 · 크루 · 만들기와 이전 계정의 알림함을 스택에서 뺀다(iOS `clearAccountData`, 설계 E9).
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
                // 새 보드 화면이면 그 보드가 값을 알리기 전까지 깃발 탭이 이전 보드 값을 따르지 않게 비운다.
                liveBoard = if (state.routes.lastOrNull() == route) state.liveBoard else null,
            )
        }
    }

    /** 보드 시트 · 다이얼로그 표시가 바뀌었다(탭 바 숨김). */
    fun onLiveBoardPresentingModalChanged(isPresenting: Boolean) = intent {
        reduce { state.copy(isLiveBoardPresentingModal = isPresenting) }
    }

    /**
     * 보드의 경계 이동. 존 지도 · 알림함은 쌓고(iOS `zoneMapRequested` · `notificationInboxRequested`),
     * 로그인 경계는 문맥을 담아 상위에 로그인을 요청하고(iOS `loginRequested` · `crewLoginRequested` · `followLoginRequested`),
     * "깃발 찾기"는 깃발 탭을(iOS `flagDirectoryRequested`), "가는 길"은 마이 팩 상세를 요청한다(iOS `packDirectionsRequested`).
     * 대상 화면이 아직 없는 기수는 받아 두기만 한다.
     */
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

            // iOS `crewCreateRequested`. 이미 열려 있으면 다시 쌓지 않는다.
            is LiveBoardBoundary.CrewCreate -> {
                if (state.routes.any { it is FestivalRoute.CrewCreate }) return@intent
                reduce { state.copy(routes = state.routes + FestivalRoute.CrewCreate(boundary.festivalId)) }
            }

            is LiveBoardBoundary.Crew -> pushCrew(boundary.festivalId, boundary.crewId)

            LiveBoardBoundary.Login -> postSideEffect(FestivalCoordinatorSideEffect.LoginRequested(null))

            LiveBoardBoundary.CrewLogin -> postSideEffect(FestivalCoordinatorSideEffect.LoginRequested(FestivalLoginContext.Crew))

            is LiveBoardBoundary.CrewJoinLogin ->
                postSideEffect(FestivalCoordinatorSideEffect.LoginRequested(FestivalLoginContext.CrewJoin(boundary.crewName)))

            LiveBoardBoundary.FollowLogin -> postSideEffect(FestivalCoordinatorSideEffect.LoginRequested(FestivalLoginContext.Follow))

            LiveBoardBoundary.FlagDirectory -> postSideEffect(FestivalCoordinatorSideEffect.FlagTabRequested)

            is LiveBoardBoundary.PackDirections ->
                postSideEffect(FestivalCoordinatorSideEffect.PackDirectionsRequested(boundary.festivalId, boundary.title))

            is LiveBoardBoundary.Bearer -> reduce { state.copy(lastBoundary = boundary) }
        }
    }

    /** 만들기 · 크루 화면의 뒤로 · 닫기(iOS `crewCreate(.delegate(.closeRequested))` · `crew(.delegate(.backRequested))`). */
    fun onCrewBack() = goBack()

    /** 만들기 "크루 보기"(iOS `openCrewRequested`): 만들기 화면을 크루 화면으로 바꾼다. 크루에서 뒤로 가면 보드다. */
    fun onCrewCreateOpenCrew(festivalId: String, crewId: String) = intent {
        reduce { state.copy(routes = state.routes.filterNot { it is FestivalRoute.CrewCreate }) }
        pushCrew(festivalId, crewId)
    }

    /** 만들기 중 세션이 끊겼다(iOS `crewCreate(.delegate(.loginRequested))`). 화면을 닫고 크루 문맥으로 로그인을 요청한다(iOS `crewLoginRequested`). */
    fun onCrewCreateLoginRequested() = intent {
        reduce { state.copy(routes = state.routes.filterNot { it is FestivalRoute.CrewCreate }) }
        postSideEffect(FestivalCoordinatorSideEffect.LoginRequested(FestivalLoginContext.Crew))
    }

    /** 해산했거나 크루에서 빠졌다(iOS `crewClosed`). 크루 화면을 뺀다. 보드는 다시 보일 때 크루 카드를 새로 받는다. */
    fun onCrewClosed() = intent {
        reduce { state.copy(routes = state.routes.filterNot { it is FestivalRoute.Crew }) }
    }

    /**
     * 초대 코드로 참여 확인을 연다(iOS `openCrewInvite(code)`). 기존 참여 확인 · 크루 화면을 빼고 쌓는다.
     * 초대 딥링크는 메인 탭이 라이브 탭을 고른 뒤 이 함수로 넘긴다(iOS `MTC:379-384`).
     */
    fun onCrewInviteOpened(code: String) = intent {
        val routes = state.routes.filterNot { it is FestivalRoute.CrewJoin || it is FestivalRoute.Crew }
        reduce { state.copy(routes = routes + FestivalRoute.CrewJoin(code)) }
    }

    /** 참여 확인 닫기(iOS `crewJoin(.delegate(.closeRequested))` → `goBack`). */
    fun onCrewJoinClosed() = goBack()

    /**
     * 참여 확인에서 로그인이 필요하다(iOS `crewJoin(.delegate(.loginRequested))` → `crewJoinLoginRequested(crewName:)`).
     * 화면은 그대로 두고 참여 문맥(크루 이름)으로 로그인을 요청한다. 로그인 뒤 자동 참여하지 않는다.
     */
    fun onCrewJoinLoginRequested(crewName: String?) = intent {
        postSideEffect(FestivalCoordinatorSideEffect.LoginRequested(FestivalLoginContext.CrewJoin(crewName)))
    }

    /**
     * 참여했다(iOS `crewJoin(.delegate(.joined))`). 참여 확인을 빼고 크루 화면을 쌓는다. 보드 크루 카드는 보드가 다시 보일 때 새로 받는다
     * (iOS는 `reloadLiveBoard`로 바로 다시 받는다, spec `crew.md`).
     */
    fun onCrewJoined(festivalId: String, crewId: String) = intent {
        val routes = state.routes.filterNot { it is FestivalRoute.CrewJoin || it is FestivalRoute.Crew }
        reduce { state.copy(routes = routes + FestivalRoute.Crew(festivalId, crewId)) }
    }

    /** iOS `pushCrew`. 이미 크루 화면이 있거나 보드가 없으면 쌓지 않는다. */
    private suspend fun pushCrew(festivalId: String, crewId: String) = subIntent {
        if (state.routes.any { it is FestivalRoute.Crew } || state.routes.none { it is FestivalRoute.LiveBoard }) return@subIntent
        reduce { state.copy(routes = state.routes + FestivalRoute.Crew(festivalId, crewId)) }
    }

    /** 존 지도 "깃발을 팔로우하면…" 띠(iOS `zoneMap(.delegate(.flagDirectoryRequested))`): 보드로 돌아가 깃발 탭을 요청한다. */
    fun onZoneMapFlagDirectoryRequested() = intent {
        reduce { state.copy(routes = state.routes.dropLast(1).ifEmpty { state.routes }) }
        postSideEffect(FestivalCoordinatorSideEffect.FlagTabRequested)
    }

    /** 보드의 깃발 탭 기준 값이 바뀌었다(iOS 깃발 탭 동기화가 읽는 `flagBoard`). */
    fun onLiveBoardChanged(route: FestivalRoute.LiveBoard, board: FlagBoard) = intent {
        // 빠진 보드 화면의 늦은 값은 받지 않는다. 맨 위 보드 경로가 알린 값만 받는다.
        if (state.routes.lastOrNull { it is FestivalRoute.LiveBoard } != route) return@intent
        if (state.liveBoard != board || state.liveBoardRoute != route) {
            reduce { state.copy(liveBoard = board, liveBoardRoute = route) }
        }
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
                // 경로를 새로 만들면 새 보드 화면이다. 그 보드가 알리기 전까지 이전 값을 비운다.
                liveBoard = if (state.routes == routes) state.liveBoard else null,
            )
        }
    }

    /** 보드가 대기 축제를 받았다. */
    fun onPendingBoardFestivalConsumed() = intent { reduce { state.copy(pendingBoardFestivalId = null) } }

    private fun onAccountChanged() = intent {
        reduce {
            state.copy(
                routes =
                    state.routes.filterNot {
                        it is FestivalRoute.ZoneMap ||
                            it is FestivalRoute.NotificationInbox ||
                            it is FestivalRoute.Crew ||
                            it is FestivalRoute.CrewCreate
                    },
                pendingBoardFestivalId = null,
            )
        }
    }
}
