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
import io.festicore.feature.festival.inbox.NotificationInboxScreen
import io.festicore.feature.festival.liveboard.LiveBoardScreen
import io.festicore.feature.festival.zonemap.ZoneMapScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * 라이브 탭 루트(iOS `FestivalCoordinatorView`). [onHidesTabBarChanged]로 탭 바를 숨길지, [onFlagTabRequested]로 깃발 탭 전환을 알린다.
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
    coordinator: FestivalCoordinator = koinViewModel(),
    listViewModel: FestivalListViewModel = koinViewModel(),
) {
    val state by coordinator.collectAsState()
    coordinator.collectSideEffect { sideEffect ->
        when (sideEffect) {
            FestivalCoordinatorSideEffect.FlagTabRequested -> onFlagTabRequested()
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
                    LiveBoardScreen(
                        viewModel =
                            koinViewModel {
                                parametersOf(key.festivalId, listViewModel.container.stateFlow.value.catalog)
                            },
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
