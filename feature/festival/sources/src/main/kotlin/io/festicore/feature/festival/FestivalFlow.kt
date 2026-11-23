package io.festicore.feature.festival
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.festicore.feature.festival.common.FestivalTransitions
import io.festicore.feature.festival.crew.CrewScreen
import io.festicore.feature.festival.crewcreate.CrewCreateScreen
import io.festicore.feature.festival.crewjoin.CrewJoinScreen
import io.festicore.feature.festival.festivallist.FestivalListScreen
import io.festicore.feature.festival.festivallist.FestivalListViewModel
import io.festicore.feature.festival.festivallist.ListForegroundEffect
import io.festicore.feature.festival.flag.FlagBoard
import io.festicore.feature.festival.liveboard.LiveBoardScreen
import io.festicore.feature.festival.liveboard.LiveBoardViewModel
import io.festicore.feature.festival.notificationinbox.NotificationInboxScreen
import io.festicore.feature.festival.zonemap.ZoneMapScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * 라이브 탭 루트(iOS `FestivalCoordinatorView`). [onHidesTabBarChanged]로 탭 바를 숨길지, [onFlagTabRequested]로 깃발 탭 전환을,
 * [onLoginRequested]로 로그인 시트 요청(문맥이 `null`이면 탭 기본 문맥)을 알린다.
 * 보드는 깃발 탭이 따를 값([FlagBoard])을 Coordinator에 알린다. 보드가 화면에 있는 동안만 갱신한다.
 * 상위(메인 탭)는 그 값으로 탭 바를 숨긴다(iOS `MainTabCoordinator.shouldHideTabBar`: 스택이 목록뿐이거나 보드에 시트 · 알림창이 떠 있음).
 * Coordinator와 목록 ViewModel은 이 흐름을 그린 범위(메인 탭 항목)에 둔다. 탭을 바꿔 흐름이 화면에서 빠져도 목록 상태가 남는다.
 * 보드 ViewModel은 스택 항목 범위라 pop하면 정리된다.
 * 포그라운드는 스택 바깥 수명 주기로 본다. 스택 항목 수명 주기는 위에 화면이 쌓이면 내려가므로, 목록 위에 보드를 쌓아도
 * 목록에서 시작한 팩 받기가 취소되지 않는다.
 */
@Composable
fun FestivalFlow(
    onHidesTabBarChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onFlagTabRequested: () -> Unit = {},
    onLoginRequested: (FestivalLoginContext?) -> Unit = {},
    coordinator: FestivalCoordinator = koinViewModel(),
    listViewModel: FestivalListViewModel = koinViewModel(),
) {
    val state by coordinator.collectAsState()
    coordinator.collectSideEffect { sideEffect ->
        when (sideEffect) {
            FestivalCoordinatorSideEffect.FlagTabRequested -> onFlagTabRequested()
            is FestivalCoordinatorSideEffect.LoginRequested -> onLoginRequested(sideEffect.context)
        }
    }
    LaunchedEffect(state.hidesTabBar) { onHidesTabBarChanged(state.hidesTabBar) }
    // 라이브 탭을 고를 때마다 흐름이 다시 그려진다(iOS `MainTabCoordinator` `.live` → `reloadCachedPacks`).
    LaunchedEffect(Unit) { listViewModel.onReloadCachedPacks() }
    val flowLifecycleOwner = LocalLifecycleOwner.current
    ListForegroundEffect(
        lifecycleOwner = flowLifecycleOwner,
        onForegroundChanged = listViewModel::onForegroundChanged,
        onBackgrounded = listViewModel::onBackgrounded,
    )

    NavDisplay(
        backStack = state.routes,
        modifier = modifier,
        onBack = coordinator::goBack,
        transitionSpec = { FestivalTransitions.push() },
        popTransitionSpec = { FestivalTransitions.pop() },
        predictivePopTransitionSpec = { FestivalTransitions.pop() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<FestivalRoute.List> {
                    FestivalListScreen(viewModel = listViewModel, onFestivalSelected = coordinator::onFestivalSelected)
                }
                entry<FestivalRoute.LiveBoard> { key ->
                    val boardViewModel: LiveBoardViewModel =
                        koinViewModel {
                            parametersOf(key.festivalId, listViewModel.container.stateFlow.value.catalog)
                        }
                    LaunchedEffect(boardViewModel) {
                        boardViewModel.container.stateFlow
                            .map(FlagBoard::of)
                            .distinctUntilChanged()
                            .collect { coordinator.onLiveBoardChanged(key, it) }
                    }
                    LiveBoardScreen(
                        viewModel = boardViewModel,
                        foregroundLifecycleOwner = flowLifecycleOwner,
                        onBack = coordinator::goBack,
                        onBoundary = coordinator::onLiveBoardBoundary,
                        onPresentingModalChanged = coordinator::onLiveBoardPresentingModalChanged,
                        onFestivalsChanged = coordinator::onLiveBoardFestivalsChanged,
                        pendingFestivalId = state.pendingBoardFestivalId,
                        onPendingFestivalConsumed = coordinator::onPendingBoardFestivalConsumed,
                    )
                }
                entry<FestivalRoute.ZoneMap> { key ->
                    ZoneMapScreen(
                        viewModel = koinViewModel { parametersOf(key.festivalId, key.zoneId) },
                        foregroundLifecycleOwner = flowLifecycleOwner,
                        onBack = coordinator::goBack,
                        onFlagDirectoryRequested = coordinator::onZoneMapFlagDirectoryRequested,
                    )
                }
                entry<FestivalRoute.CrewCreate> { key ->
                    CrewCreateScreen(
                        viewModel = koinViewModel { parametersOf(key.festivalId) },
                        onClose = coordinator::onCrewBack,
                        onOpenCrew = { crewId -> coordinator.onCrewCreateOpenCrew(key.festivalId, crewId) },
                        onLoginRequested = coordinator::onCrewCreateLoginRequested,
                    )
                }
                entry<FestivalRoute.Crew> { key ->
                    CrewScreen(
                        viewModel = koinViewModel { parametersOf(key.festivalId, key.crewId) },
                        onBack = coordinator::onCrewBack,
                        onCrewClosed = coordinator::onCrewClosed,
                    )
                }
                entry<FestivalRoute.CrewJoin> { key ->
                    CrewJoinScreen(
                        viewModel = koinViewModel { parametersOf(key.code) },
                        onClose = coordinator::onCrewJoinClosed,
                        onLoginRequested = coordinator::onCrewJoinLoginRequested,
                        onJoined = { crewId, festivalId -> coordinator.onCrewJoined(festivalId, crewId) },
                    )
                }
                entry<FestivalRoute.NotificationInbox> {
                    NotificationInboxScreen(
                        viewModel = koinViewModel(),
                        onBack = coordinator::goBack,
                        onFestivalOpened = coordinator::onNotificationFestivalOpened,
                    )
                }
            },
    )
}
