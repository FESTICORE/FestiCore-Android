package io.festicore.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import io.festicore.feature.auth.AuthRoute
import io.festicore.feature.auth.LaunchLoginFlow
import io.festicore.feature.festival.FestivalFlow
import io.festicore.feature.onboarding.OnboardingResult
import io.festicore.feature.onboarding.OnboardingRoute
import io.festicore.feature.onboarding.OnboardingScreen
import io.festicore.feature.web.WebPage
import io.festicore.feature.web.WebScreen
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

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
 */
@Composable
private fun HomeWithCovers(state: AppCoordinatorState, coordinator: AppCoordinator) {
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
                liveContent = { onHidesTabBarChanged, onFlagTabRequested ->
                    FestivalFlow(onHidesTabBarChanged = onHidesTabBarChanged, onFlagTabRequested = onFlagTabRequested)
                },
            )
        }
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
