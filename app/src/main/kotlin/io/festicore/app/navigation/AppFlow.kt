package io.festicore.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.feature.auth.AuthRoute
import io.festicore.feature.auth.LaunchLoginFlow
import io.festicore.feature.auth.LoginSheet
import io.festicore.feature.festival.FestivalCoordinator
import io.festicore.feature.festival.FestivalFlow
import io.festicore.feature.festival.flag.FlagCoordinator
import io.festicore.feature.festival.flag.FlagFlow
import io.festicore.feature.festival.flag.FlagViewModel
import io.festicore.feature.festival.flag.flagDetailContext
import io.festicore.feature.festival.flag.flagTabInput
import io.festicore.feature.onboarding.OnboardingResult
import io.festicore.feature.onboarding.OnboardingRoute
import io.festicore.feature.onboarding.OnboardingScreen
import io.festicore.feature.profile.ProfileCoordinator
import io.festicore.feature.profile.ProfileFlow
import io.festicore.feature.web.WebPage
import io.festicore.feature.web.WebScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * 루트 흐름. 스택은 항상 한 화면이라 시스템 뒤로 가기는 앱을 닫는다(스플래시 · 온보딩으로 돌아가지 않는다).
 * 화면 바꿈은 [LaunchTransitions.root](iOS `AppCoordinatorView` 스프링)로 그린다. 스택을 통째로 바꿔 pop이 생기지 않으므로 `transitionSpec`만 넘긴다.
 * Home 위 덮개(첫 진입 로그인 · 가입 정보)는 feature/auth 흐름이 그린다. 덮개가 있으면 아래 메인 탭은 읽기 도구에서 숨긴다.
 * [onLaunchContentReady]는 첫 화면을 그릴 수 있을 때 부른다(스플래시면 Lottie를 읽은 뒤, 아니면 바로). 시스템 스플래시를 그때 걷는다.
 */
@Composable
fun AppFlow(coordinator: AppCoordinator = koinViewModel(), onLaunchContentReady: () -> Unit = {}) {
    val state by coordinator.collectAsState()
    val currentOnLaunchContentReady by rememberUpdatedState(onLaunchContentReady)
    LaunchedEffect(Unit) { if (state.routes.lastOrNull() != AppRoute.Splash) currentOnLaunchContentReady() }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { coordinator.onForegrounded() }

    NavDisplay(
        backStack = state.routes,
        onBack = {},
        transitionSpec = {
            LaunchTransitions.root(
                initial = initialState.metadata[LaunchTransitions.ROOT_SCREEN_KEY] as? RootScreen,
                target = targetState.metadata[LaunchTransitions.ROOT_SCREEN_KEY] as? RootScreen,
                isLaunchLoginPresented = state.launchLogin,
            )
        },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<AppRoute.Splash>(metadata = rootScreen(RootScreen.Splash)) {
                    SplashScreen(
                        onAppear = {
                            currentOnLaunchContentReady()
                            coordinator.onSplashAppeared()
                        },
                    )
                }
                entry<OnboardingRoute>(metadata = rootScreen(RootScreen.Onboarding)) {
                    OnboardingScreen(
                        onResult = { result ->
                            when (result) {
                                OnboardingResult.Completed -> coordinator.onOnboardingCompleted()
                            }
                        },
                    )
                }
                entry<AppRoute.Home>(metadata = rootScreen(RootScreen.Home)) { HomeWithCovers(state, coordinator) }
            },
    )
}

private fun rootScreen(screen: RootScreen): Map<String, Any> = mapOf(LaunchTransitions.ROOT_SCREEN_KEY to screen)

/**
 * 메인 탭과 그 위 덮개(iOS `MainTabView`의 overlay).
 * - 첫 진입 덮개(N4 · N5): 덮는 동안 메인 탭은 화면 오른쪽 밖에 두고, 걷히면 메인 탭이 오른쪽에서 들어오고 덮개는 왼쪽으로 나간다(0.35초).
 * - 재확인 가입 덮개(N8): 오른쪽에서 들어오고 오른쪽으로 나간다(0.3초). 메인 탭은 제자리.
 * 루트가 꺼낸 딥링크는 [MainTabCoordinator]가 탭을 고르고, 라이브 경로(축제 · 크루 초대)는 라이브 흐름([FestivalCoordinator])에,
 * 깃발 경로는 깃발 흐름([FlagCoordinator])에 넘긴다.
 * 로그인 시트(iOS `LoginModalView`)는 메인 탭 위에 띄운다. 로그인 결과 · 가입 이어짐은 [AppCoordinator]가 정한다.
 * 라이브 · 깃발 흐름의 로그인 요청은 메인 탭이 문맥을 정해(문맥이 없으면 탭 기본) 시트로 올린다(iOS `MTCSc`). 시트가 떠 있으면 탭 바를 숨긴다.
 * 깃발 흐름 Coordinator와 목록은 탭 밖(이 범위)에 두어 다른 탭에 다녀와도 깃발 스택을 유지한다(설계 "탭 바 규칙").
 * 깃발 탭은 라이브 스택의 보드([FestivalCoordinator]의 `flagBoard`)와 현재 사용자로 목록 입력 · 상세 문맥을 맞춘다(iOS `MTCSy` `syncFlag`).
 * 마이 흐름 Coordinator([ProfileCoordinator])도 탭 밖에 두어 마이 스택을 유지한다. 계정 상태는 [AppCoordinator.account]를 넘기고(쟁점 M13),
 * 로그인 · 계정 작업은 [AppCoordinator]로, 축제 목록은 라이브 탭으로 올린다(iOS `handleProfileScope`).
 * 라이브 보드 "가는 길"은 [MainTabCoordinator]가 마이 탭을 고르고 마이 흐름에 팩 상세를 넘긴다(iOS `packDirectionsRequested`).
 */
@Composable
private fun HomeWithCovers(
    state: AppCoordinatorState,
    coordinator: AppCoordinator,
    mainTab: MainTabCoordinator = koinViewModel(),
    festivalCoordinator: FestivalCoordinator = koinViewModel(),
    flagCoordinator: FlagCoordinator = koinViewModel(),
    flagViewModel: FlagViewModel = koinViewModel(),
    profileCoordinator: ProfileCoordinator = koinViewModel(),
    currentUserProvider: CurrentUserProvider = koinInject(),
) {
    val mainTabState by mainTab.collectAsState()
    val festivalState by festivalCoordinator.collectAsState()
    val flagState by flagViewModel.collectAsState()
    val accountId by currentUserProvider.currentUserId.collectAsState()
    val flagBoard = festivalState.flagBoard
    val flagDetailContext = remember { MutableStateFlow(flagBoard.flagDetailContext(flagState.snapshot, accountId)) }
    LaunchedEffect(flagBoard, flagState.snapshot, accountId) {
        flagDetailContext.value = flagBoard.flagDetailContext(flagState.snapshot, accountId)
    }
    val flagRequest = mainTabState.flagRequest
    LaunchedEffect(flagRequest) {
        if (flagRequest == null) return@LaunchedEffect
        flagCoordinator.openDetail(flagRequest)
        mainTab.onFlagRequestConsumed()
    }
    val packDirectionsRequest = mainTabState.packDirectionsRequest
    LaunchedEffect(packDirectionsRequest) {
        if (packDirectionsRequest == null) return@LaunchedEffect
        profileCoordinator.onOpenPackDirections(packDirectionsRequest.festivalId, packDirectionsRequest.title)
        mainTab.onPackDirectionsRequestConsumed()
    }
    coordinator.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is AppCoordinatorSideEffect.OpenDeeplink -> mainTab.open(sideEffect.deeplink)
        }
    }
    val mainTabOffset by animateFloatAsState(
        targetValue = if (state.launchLogin) 1f else 0f,
        animationSpec = LaunchTransitions.coverTween(),
        label = "mainTabOffset",
    )
    val legalDocument: @Composable (
        WebPage,
        () -> Unit,
    ) -> Unit = { page, onBackToRoot -> WebScreen(page = page, onBackToRoot = onBackToRoot) }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .graphicsLayer { translationX = mainTabOffset * size.width }
                    .then(if (state.authCover != null) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            MainTabScreen(
                selection = mainTabState.selectedTab,
                onSelectionChange = mainTab::onTabSelected,
                onFlagTabRequested = mainTab::onFlagTabRequested,
                isLoginSheetPresented = state.loginSheet != null,
                liveContent = { onHidesTabBarChanged, onFlagTabRequested ->
                    val liveRequest = mainTabState.liveRequest
                    LaunchedEffect(liveRequest) {
                        when (liveRequest) {
                            null -> return@LaunchedEffect
                            LiveTabRequest.FestivalList -> festivalCoordinator.onDeeplinkFestivalListOpened()
                            is LiveTabRequest.Festival -> festivalCoordinator.onDeeplinkFestivalOpened(liveRequest.festivalId)
                            is LiveTabRequest.CrewInvite -> festivalCoordinator.onCrewInviteOpened(liveRequest.code)
                        }
                        mainTab.onLiveRequestConsumed()
                    }
                    FestivalFlow(
                        onHidesTabBarChanged = onHidesTabBarChanged,
                        onFlagTabRequested = onFlagTabRequested,
                        onLoginRequested = { context ->
                            coordinator.onLoginRequested(mainTabState.liveLoginContext(context))
                        },
                        onPackDirectionsRequested = mainTab::onPackDirectionsRequested,
                        coordinator = festivalCoordinator,
                    )
                },
                flagContent = {
                    FlagFlow(
                        input = flagBoard.flagTabInput(accountId),
                        detailContext = flagDetailContext,
                        onLoginRequested = { context ->
                            coordinator.onLoginRequested(mainTabState.flagLoginContext(context))
                        },
                        onTermsAcceptanceRequested = coordinator::onTermsAcceptanceRequested,
                        coordinator = flagCoordinator,
                        flagViewModel = flagViewModel,
                    )
                },
                myContent = { onHidesTabBarChanged ->
                    ProfileFlow(
                        account = coordinator.account,
                        onHidesTabBarChanged = onHidesTabBarChanged,
                        onLoginRequested = { coordinator.onLoginRequested(mainTabState.loginContext(null)) },
                        onGoogleLoginRequested = coordinator::onGoogleLoginRequested,
                        onFestivalListRequested = mainTab::onFestivalListRequested,
                        onLogoutRequested = coordinator::onLogoutRequested,
                        onWithdrawRequested = coordinator::onWithdrawRequested,
                        onRefreshRequested = coordinator::onProfileRefreshRequested,
                        onNicknameUpdated = coordinator::onNicknameUpdated,
                        legalDocument = { page, onBack -> WebScreen(page = page, onBackToRoot = onBack) },
                        coordinator = profileCoordinator,
                    )
                },
            )
        }
        LoginSheet(
            context = state.loginSheet,
            presentationId = state.loginSheetId,
            onResult = coordinator::onLoginSheetFinished,
            onSignUpRequired = coordinator::onLoginSheetSignUpRequired,
            onDismiss = coordinator::onLoginSheetDismissed,
            startsSignIn = state.directLogin,
            onSignInFailed = coordinator::onDirectLoginFailed,
        )
        AnimatedVisibility(
            visible = state.launchLogin,
            enter = LaunchTransitions.launchCoverEnter(),
            exit = LaunchTransitions.launchCoverExit(),
        ) {
            // 로그인 직후 세션 만료로 덮개 세대가 오르면 새 흐름으로 다시 만든다.
            key(state.coverEpoch) {
                LaunchLoginFlow(
                    start = AuthRoute.LaunchLogin,
                    onResult = coordinator::onLaunchLoginFinished,
                    onTopChanged = coordinator::onAuthCoverTopChanged,
                    legalDocument = legalDocument,
                )
            }
        }
        AnimatedContent(
            targetState = state.signUp.takeUnless { state.launchLogin },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { LaunchTransitions.signUpCover() },
            // 재확인으로 같은 사용자 · 세션의 프로필만 바뀌면 덮개를 다시 띄우지 않고 열린 가입 화면에 반영한다.
            contentKey = { it?.let { route -> route.userId to route.sessionId } },
            label = "signUpCover",
        ) { signUp ->
            if (signUp != null) {
                LaunchLoginFlow(
                    start = signUp,
                    onResult = coordinator::onLaunchLoginFinished,
                    onTopChanged = coordinator::onAuthCoverTopChanged,
                    legalDocument = legalDocument,
                    signUpProfile = state.signUp ?: signUp,
                )
            }
        }
    }
}
