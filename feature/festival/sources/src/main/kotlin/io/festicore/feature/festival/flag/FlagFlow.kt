package io.festicore.feature.festival.flag

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.festicore.domain.festival.entity.FestivalFlagFollowResult
import io.festicore.feature.festival.FlagLoginContext
import io.festicore.feature.festival.FlagRoute
import io.festicore.feature.festival.FlagTabInput
import io.festicore.feature.festival.common.FestivalTransitions
import io.festicore.feature.festival.flagdetail.FlagDetailContext
import io.festicore.feature.festival.flagdetail.FlagDetailScreen
import io.festicore.feature.festival.flagdetail.FlagDetailViewModel
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * 깃발 탭 루트(iOS `FlagCoordinatorView`). 목록 위에 상세 하나를 쌓는다. 탭 바는 항상 보여 숨김 콜백이 없다.
 * [input]은 목록에, 상세 문맥은 열린 상세에 넘긴다(iOS `festivalChanged` · `accountChanged` · `zoneNamesChanged` · `updateDetailContext`).
 * 로그인 · 약관 요청은 경계로만 올린다(로그인 시트는 L1). Coordinator와 목록 ViewModel은 이 흐름을 그린 범위에 둔다.
 * 스택 항목([entries])은 탭을 바꿔도 남는 범위에서 [rememberFlagFlowEntries]로 만들어 받는다.
 * 깃발 링크는 상위가 [coordinator]의 [FlagCoordinator.openDetail]을 부른다.
 *
 */
@Composable
fun FlagFlow(
    entries: List<NavEntry<NavKey>>,
    input: FlagTabInput,
    modifier: Modifier = Modifier,
    coordinator: FlagCoordinator = koinViewModel(),
    flagViewModel: FlagViewModel = koinViewModel(),
) {
    val state by coordinator.collectAsState()
    coordinator.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is FlagCoordinatorSideEffect.FollowChangedElsewhere ->
                flagViewModel.onFollowChangedElsewhere(sideEffect.result, sideEffect.baseCount)
        }
    }
    // 깃발 탭을 고를 때마다 흐름이 다시 그려져 목록 문맥을 다시 맞춘다(iOS `MTCSy` 깃발 탭 동기화). 값 변화는 [rememberFlagFlowEntries]가 맞춘다.
    LaunchedEffect(Unit) { flagViewModel.onFestivalChanged(input.selectedFestivalId) }

    NavDisplay(
        entries = entries,
        modifier = modifier,
        onBack = coordinator::goBack,
        transitionSpec = { FestivalTransitions.push() },
        popTransitionSpec = { FestivalTransitions.pop() },
        predictivePopTransitionSpec = { FestivalTransitions.pop() },
    )
}

/**
 * 깃발 스택 항목. 탭을 바꿔도 남는 범위(메인 탭 항목)에서 부른다. 항목 범위(상세 ViewModel · 저장 상태)는 탭을 바꿔도 남고
 * 스택에서 빠질 때만 정리된다(Navigation 3 `rememberDecoratedNavEntries` 여러 백스택 방식). [input]은 목록에, [detailContext]는 열린 상세에 넘긴다.
 *
 * @param onLoginRequested 로그인 요청. 문맥이 `null`이면 기본 문구(목록), [FlagLoginContext.Follow]는 상세 팔로우, [FlagLoginContext.Report]는 신고
 * @param onTermsAcceptanceRequested 약관 재동의 요청(iOS `session.retryProfile`)
 * @param festivalTransferContent 깃발 축제 이동 화면. 기수 기능 화면이라 상위(app)가 넘긴다. 옮기면 `onTransferred`, 뒤로는 `onClose`.
 */
@Composable
fun rememberFlagFlowEntries(
    input: FlagTabInput,
    detailContext: StateFlow<FlagDetailContext>,
    onLoginRequested: (FlagLoginContext?) -> Unit,
    onTermsAcceptanceRequested: () -> Unit,
    coordinator: FlagCoordinator = koinViewModel(),
    flagViewModel: FlagViewModel = koinViewModel(),
    festivalTransferContent: @Composable (flagId: String, onTransferred: () -> Unit, onClose: () -> Unit) -> Unit = { _, _, _ -> },
): List<NavEntry<NavKey>> {
    val state by coordinator.collectAsState()
    val context by detailContext.collectAsState()
    // 깃발 탭이 안 보여도 축제 · 존 이름 · 계정 변화를 바로 반영한다(iOS `TabView`의 숨은 탭도 동기화를 받는다).
    LaunchedEffect(input.selectedFestivalId) { flagViewModel.onFestivalChanged(input.selectedFestivalId) }
    LaunchedEffect(input.zoneNames) { flagViewModel.onZoneNamesChanged(input.zoneNames) }
    LaunchedEffect(input.accountId) {
        if (coordinator.onAccountChanged(input.accountId)) flagViewModel.onAccountChanged()
    }
    return rememberDecoratedNavEntries(
        backStack = state.routes,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<FlagRoute.Directory> {
                    FlagScreen(
                        onDetailRequested = coordinator::openDetail,
                        onLoginRequested = { onLoginRequested(null) },
                        onTermsAcceptanceRequested = onTermsAcceptanceRequested,
                        viewModel = flagViewModel,
                    )
                }
                entry<FlagRoute.Detail> { key ->
                    val detailViewModel =
                        koinViewModel<FlagDetailViewModel>(key = "flagDetail-${key.flagId}") { parametersOf(key.flagId, context) }
                    LaunchedEffect(detailViewModel, context) { detailViewModel.onContextChanged(context) }
                    LaunchedEffect(detailViewModel, state.transferredFlagId) {
                        if (state.transferredFlagId != key.flagId) return@LaunchedEffect
                        detailViewModel.onFestivalTransferred()
                        coordinator.onFestivalTransferConsumed()
                    }
                    FlagDetailScreen(
                        viewModel = detailViewModel,
                        onBack = coordinator::goBack,
                        onPinChanged = { flagViewModel.refresh() },
                        onLoginRequested = { onLoginRequested(FlagLoginContext.Follow) },
                        onReportLoginRequested = { onLoginRequested(FlagLoginContext.Report) },
                        onTermsAcceptanceRequested = onTermsAcceptanceRequested,
                        onFollowChanged = { changed ->
                            coordinator.onDetailFollowChanged(
                                FestivalFlagFollowResult(changed.flagId, changed.isFollowing, changed.followerCount),
                                flagViewModel.container.stateFlow.value.snapshot,
                            )
                        },
                        onFestivalTransferRequested = coordinator::openFestivalTransfer,
                    )
                }
                entry<FlagRoute.FestivalTransfer> { key ->
                    festivalTransferContent(key.flagId, { coordinator.onFestivalTransferred(key.flagId) }, coordinator::goBack)
                }
            },
    )
}
