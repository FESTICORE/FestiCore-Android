package io.festicore.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.festicore.feature.auth.LaunchLoginFlow
import io.festicore.feature.onboarding.OnboardingResult
import io.festicore.feature.onboarding.OnboardingRoute
import io.festicore.feature.onboarding.OnboardingScreen
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState

/**
 * 루트 흐름. 스택은 항상 한 화면이라 시스템 뒤로 가기는 앱을 닫는다(스플래시 · 온보딩으로 돌아가지 않는다).
 * Home 위 덮개(첫 진입 로그인 · 가입 정보)는 feature/auth 흐름이 그린다. 덮개가 있으면 아래 메인 탭은 읽기 도구에서 숨긴다.
 */
@Composable
fun AppFlow(coordinator: AppCoordinator = koinViewModel()) {
    val state by coordinator.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { coordinator.onForegrounded() }

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
                entry<AppRoute.Splash> { SplashScreen(onAppear = coordinator::onSplashAppeared) }
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
                    val cover = state.authCover
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = if (cover != null) Modifier.clearAndSetSemantics {} else Modifier) {
                            MainTabScreen()
                        }
                        // 시작 화면이 바뀌거나(예: 가입 덮개 중 세션 만료) 덮개 세대가 오르면(로그인 직후 세션 만료) 새 흐름으로 다시 만든다.
                        if (cover !=
                            null
                        ) {
                            key(cover, state.coverEpoch) {
                                LaunchLoginFlow(start = cover, onResult = coordinator::onLaunchLoginFinished)
                            }
                        }
                    }
                }
            },
    )
}
