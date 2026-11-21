package io.festicore.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.festicore.feature.onboarding.OnboardingResult
import io.festicore.feature.onboarding.OnboardingRoute
import io.festicore.feature.onboarding.OnboardingScreen
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

/**
 * 루트 흐름. 스택은 항상 한 화면이라 시스템 뒤로 가기는 앱을 닫는다(스플래시 · 온보딩으로 돌아가지 않는다).
 *
 * @param launchLoginCover Home 위에 그리는 첫 진입 로그인 덮개. feature/auth가 채운다(설계 문서 구현 순서 L6).
 */
@Composable
fun AppFlow(coordinator: AppCoordinator = koinViewModel(), launchLoginCover: @Composable () -> Unit = {}) {
    val state by coordinator.collectAsState()

    NavDisplay(
        backStack = state.routes,
        onBack = {},
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<AppRoute.Splash> { SplashScreen() }
                entry<OnboardingRoute> {
                    OnboardingScreen(
                        onResult = { result ->
                            when (result) {
                                OnboardingResult.Completed -> coordinator.onOnboardingCompleted()
                            }
                        },
                    )
                }
                entry<AppRoute.Home> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainTabScreen()
                        if (state.launchLogin) launchLoginCover()
                    }
                }
            },
    )
}
