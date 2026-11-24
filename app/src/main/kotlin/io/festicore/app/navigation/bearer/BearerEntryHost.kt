package io.festicore.app.navigation.bearer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.festicore.domain.bearer.entity.BearerFlagProfile
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
    val routes = remember(startRoute) { mutableStateListOf(startRoute) }
    var claimedProfile by remember(startRoute) { mutableStateOf<BearerFlagProfile?>(null) }
    var newBearer by remember(startRoute) { mutableStateOf(false) }
    var profileViewModel by remember { mutableStateOf<BearerProfileViewModel?>(null) }
    var planSets by remember(startRoute) { mutableStateOf(emptyList<io.festicore.domain.bearer.entity.BearerSet>()) }
    val route = routes.last()
    fun push(next: BearerRoute) {
        routes.add(next)
    }
    fun closeOrPop() {
        if (routes.size > 1) routes.removeAt(routes.lastIndex) else onClose()
    }

    when (route) {
        is BearerRoute.Register -> {
            val viewModel: BearerRegisterViewModel = koinViewModel { parametersOf(route.preferredFestivalId) }
            BearerRegisterScreen(
                viewModel = viewModel,
                onRegistered = { flagId, festivalId ->
                    claimedProfile = null
                    newBearer = true
                    onRegistered(festivalId)
                    push(BearerRoute.Profile(festivalId, flagId, isNewBearer = true))
                },
                onPackRequested = onPackRequested,
                onFinished = { closeOrPop() },
                onClaimRequested = { push(BearerRoute.Claim(it)) },
                onBackRequested = { closeOrPop() },
                onCloseRequested = onClose,
            )
        }

        is BearerRoute.Claim -> {
            val viewModel: BearerClaimViewModel = koinViewModel { parametersOf(route.festivalId) }
            BearerClaimScreen(
                viewModel = viewModel,
                onClaimed = { festivalId, claim ->
                    claimedProfile = claim.profile
                    newBearer = claim.isNew
                    push(BearerRoute.Profile(festivalId, claim.flagId, claim.isNew))
                },
                onCloseRequested = ::closeOrPop,
            )
        }

        is BearerRoute.Profile -> {
            val viewModel: BearerProfileViewModel =
                koinViewModel { parametersOf(route.festivalId, route.flagId, claimedProfile, route.isNewBearer || newBearer) }
            profileViewModel = viewModel
            BearerProfileScreen(
                viewModel = viewModel,
                onFinished = { flagId ->
                    routes.clear()
                    routes.add(BearerRoute.Board(route.festivalId, flagId))
                },
                onPlanRequested = { festivalId ->
                    val current = viewModel.container.stateFlow.value
                    push(BearerRoute.PlanPick(festivalId))
                    planSets = current.sets
                    claimedProfile = BearerFlagProfile(id = current.flagId, name = current.name, plan = current.plan)
                },
                onCloseRequested = ::closeOrPop,
            )
        }

        is BearerRoute.PlanPick -> {
            val profile = claimedProfile
            val viewModel: BearerPlanPickViewModel =
                koinViewModel { parametersOf(planSets, profile?.plan.orEmpty()) }
            BearerPlanPickScreen(
                viewModel = viewModel,
                onPicked = { setIds ->
                    profileViewModel?.onPlanChanged(setIds)
                    closeOrPop()
                },
                onCloseRequested = ::closeOrPop,
            )
        }

        is BearerRoute.Board -> BearerBoardScreen(route = route, viewerUserId = viewerUserId, onClose = ::closeOrPop)

        BearerRoute.RoleChoice -> onClose()
    }
}
