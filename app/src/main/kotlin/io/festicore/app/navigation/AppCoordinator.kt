package io.festicore.app.navigation

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import io.festicore.core.datastore.FirstInstallOnboardingStore
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.core.logger.LogCategory
import io.festicore.feature.onboarding.OnboardingRoute
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * 루트 화면 상태. [routes]는 항상 한 화면(Splash · Onboarding · Home)이고 다음 화면으로 갈 때 대체한다.
 * [launchLogin]은 Home 위 첫 진입 로그인 덮개 표시 여부다(iOS `Home(launchLogin:)`). 키와 분리해 덮개가 바뀌어도 메인 탭 상태를 유지한다.
 */
data class AppCoordinatorState(
    val routes: List<NavKey> = listOf(AppRoute.Splash),
    val launchLogin: Boolean = false,
)

/** iOS `AppCoordinator`. 스플래시 → 온보딩 → 홈(비로그인이면 launchLogin 덮개) 전환을 정한다. */
class AppCoordinator(
    private val onboardingStore: FirstInstallOnboardingStore,
    private val isLoggedIn: suspend () -> Boolean,
    private val sessionExpiry: Flow<Unit>,
) : ViewModel(),
    OrbitContainerHost<AppCoordinatorState, AppCoordinatorState, Nothing> {
    override val container = orbitContainer<AppCoordinatorState, Nothing>(AppCoordinatorState()) { presentHome() }

    fun onOnboardingCompleted() = intent {
        if (state.routes.lastOrNull() != OnboardingRoute) return@intent
        try {
            onboardingStore.markCompleted()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FestiCoreLogger.error("첫 설치 기록을 저장할 수 없음: ${e::class.simpleName}", LogCategory.APP)
        }
        checkSession()
    }

    private suspend fun presentHome() = subIntent {
        delay(SplashDuration)
        val hasCompleted =
            try {
                onboardingStore.hasCompleted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FestiCoreLogger.error("첫 설치 기록을 확인할 수 없음: ${e::class.simpleName}", LogCategory.APP)
                true
            }
        if (hasCompleted) {
            // 세션을 확인하는 동안 스플래시를 유지해 홈이 먼저 보였다가 가려지지 않게 한다.
            checkSession()
        } else {
            reduce { state.copy(routes = listOf(OnboardingRoute)) }
        }
    }

    private suspend fun checkSession() = subIntent {
        enterHome(launchLogin = !isLoggedIn())
    }

    /** 홈으로 바꾸고 세션 만료 구독을 시작한다. 첫 진입 덮개는 건너뛰기를 저장하지 않아 실행마다 다시 뜬다. */
    private suspend fun enterHome(launchLogin: Boolean) = subIntent {
        if (state.routes.lastOrNull() == AppRoute.Home) return@subIntent
        reduce { state.copy(routes = listOf(AppRoute.Home), launchLogin = launchLogin) }
        intent {
            sessionExpiry.collect { reduce { state.copy(launchLogin = true) } }
        }
    }

    private companion object {
        val SplashDuration = 2.seconds
    }
}
