package io.festicore.app.navigation.bearer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerSet
import io.festicore.feature.bearer.BearerRoute
import io.festicore.feature.bearer.board.BearerBoardScreen
import io.festicore.feature.bearer.claim.BearerClaimScreen
import io.festicore.feature.bearer.claim.BearerClaimViewModel
import io.festicore.feature.bearer.plan.BearerPlanPickScreen
import io.festicore.feature.bearer.plan.BearerPlanPickViewModel
import io.festicore.feature.bearer.profile.BearerProfileScreen
import io.festicore.feature.bearer.profile.BearerProfileViewModel
import io.festicore.feature.bearer.register.BearerRegisterScreen
import io.festicore.feature.bearer.register.BearerRegisterViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** 앱이 소유하는 마이 탭 기수 등록 · 인증 · 프로필 · 보드 경로. */
@Composable
fun BearerEntryHost(
    startRoute: BearerRoute,
    viewerUserId: String?,
    onRegistered: (festivalId: String) -> Unit,
    onPackRequested: (festivalId: String) -> Unit,
    onClose: () -> Unit,
) {
    val routes = remember(startRoute) { mutableStateListOf<NavKey>(startRoute) }
    var claimedProfile by remember(startRoute) { mutableStateOf<BearerFlagProfile?>(null) }
    var planSets by remember(startRoute) { mutableStateOf(emptyList<BearerSet>()) }
    var initialPlan by remember(startRoute) { mutableStateOf(emptyList<String>()) }
    var selectedPlan by remember(startRoute) { mutableStateOf<List<String>?>(null) }

    fun push(next: BearerRoute) {
        routes.add(next)
    }

    fun closeOrPop() {
        if (routes.size > 1) routes.removeAt(routes.lastIndex) else onClose()
    }

    val entries =
        androidx.navigation3.runtime.rememberDecoratedNavEntries(
            backStack = routes,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    entry<BearerRoute.Register> { route ->
                        val viewModel: BearerRegisterViewModel = koinViewModel { parametersOf(route.preferredFestivalId) }
                        BearerRegisterScreen(
                            viewModel = viewModel,
                            onRegistered = { flagId, festivalId ->
                                claimedProfile = null
                                onRegistered(festivalId)
                                push(BearerRoute.Profile(festivalId, flagId, isNewBearer = true))
                            },
                            onPackRequested = onPackRequested,
                            onFinished = { closeOrPop() },
                            onClaimRequested = { push(BearerRoute.Claim(it)) },
                            onBackRequested = ::closeOrPop,
                            onCloseRequested = onClose,
                        )
                    }

                    entry<BearerRoute.Claim> { route ->
                        val viewModel: BearerClaimViewModel = koinViewModel { parametersOf(route.festivalId) }
                        BearerClaimScreen(
                            viewModel = viewModel,
                            onClaimed = { festivalId, claim ->
                                claimedProfile = claim.profile
                                push(BearerRoute.Profile(festivalId, claim.flagId, claim.isNew))
                            },
                            onCloseRequested = ::closeOrPop,
                        )
                    }

                    entry<BearerRoute.Profile> { route ->
                        val viewModel: BearerProfileViewModel =
                            koinViewModel { parametersOf(route.festivalId, route.flagId, claimedProfile, route.isNewBearer) }
                        LaunchedEffect(viewModel, selectedPlan) {
                            selectedPlan?.let { plan ->
                                viewModel.onPlanChanged(plan)
                                selectedPlan = null
                            }
                        }
                        BearerProfileScreen(
                            viewModel = viewModel,
                            onFinished = { flagId ->
                                routes.clear()
                                routes.add(BearerRoute.Board(route.festivalId, flagId))
                            },
                            onPlanRequested = { festivalId ->
                                val current = viewModel.container.stateFlow.value
                                planSets = current.sets
                                initialPlan = current.plan
                                push(BearerRoute.PlanPick(festivalId))
                            },
                            onCloseRequested = ::closeOrPop,
                        )
                    }

                    entry<BearerRoute.PlanPick> { route ->
                        val viewModel: BearerPlanPickViewModel = koinViewModel { parametersOf(planSets, initialPlan) }
                        BearerPlanPickScreen(
                            viewModel = viewModel,
                            onPicked = { setIds ->
                                selectedPlan = setIds
                                closeOrPop()
                            },
                            onCloseRequested = ::closeOrPop,
                        )
                    }

                    entry<BearerRoute.Board> { route ->
                        BearerBoardScreen(route = route, viewerUserId = viewerUserId, onClose = ::closeOrPop)
                    }

                    entry<BearerRoute.RoleChoice> { onClose() }
                },
        )

    NavDisplay(
        entries = entries,
        onBack = ::closeOrPop,
    )
}
