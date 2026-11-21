package io.festicore.feature.festival

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

/**
 * 라이브 탭 루트(iOS `FestivalCoordinatorView`). [onAtRootChanged]로 스택이 첫 화면(목록)뿐인지 알린다.
 * 상위(메인 탭)는 그 값으로 탭 바를 숨긴다(iOS `MainTabCoordinator.shouldHideTabBar`의 `festivalState.routes.count == 1`).
 * Coordinator와 목록 ViewModel은 이 흐름을 그린 범위(메인 탭 항목)에 둔다. 탭을 바꿔 흐름이 화면에서 빠져도 목록 상태가 남는다.
 */
@Composable
fun FestivalFlow(
    onAtRootChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    coordinator: FestivalCoordinator = koinViewModel(),
    listViewModel: FestivalListViewModel = koinViewModel(),
) {
    val state by coordinator.collectAsState()
    val atRoot = state.routes.size == 1
    LaunchedEffect(atRoot) { onAtRootChanged(atRoot) }

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
            },
    )
}
