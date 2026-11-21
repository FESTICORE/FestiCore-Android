package io.festicore.app.navigation

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import io.festicore.core.datastore.FirstInstallOnboardingStore
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.core.logger.LogCategory
import io.festicore.domain.auth.AuthDomainError
import io.festicore.domain.auth.AuthProfile
import io.festicore.domain.auth.AuthRepository
import io.festicore.feature.auth.AuthRoute
import io.festicore.feature.auth.LaunchLoginResult
import io.festicore.feature.onboarding.OnboardingRoute
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * 루트 화면 상태. [routes]는 항상 한 화면(Splash · Onboarding · Home)이고 다음 화면으로 갈 때 대체한다.
 * [launchLogin]은 Home 위 첫 진입 로그인 덮개 표시 여부다(iOS `Home(launchLogin:)`). 키와 분리해 덮개가 바뀌어도 메인 탭 상태를 유지한다.
 * [signUp]은 로그인 상태 프로필 재확인에서 가입 조건을 채우지 못했을 때 Home 위에 띄우는 가입 정보 덮개다.
 */
data class AppCoordinatorState(
    val routes: List<NavKey> = listOf(AppRoute.Splash),
    val launchLogin: Boolean = false,
    val signUp: AuthRoute.SignUp? = null,
) {
    /** Home 위 덮개의 시작 화면. 없으면 덮개를 그리지 않는다. */
    val authCover: AuthRoute?
        get() = if (launchLogin) AuthRoute.LaunchLogin else signUp
}

/**
 * iOS `AppCoordinator`. 스플래시 → 온보딩 → 홈(비로그인이면 launchLogin 덮개) 전환을 정한다.
 * 로그인 상태로 홈에 들어갈 때와 포그라운드마다 프로필을 다시 확인한다(iOS `SessionCoordinator.foregrounded`).
 */
class AppCoordinator(
    private val onboardingStore: FirstInstallOnboardingStore,
    private val isLoggedIn: suspend () -> Boolean,
    private val sessionExpiry: Flow<Unit>,
    private val authRepository: AuthRepository,
    private val currentSessionId: suspend () -> String?,
) : ViewModel(),
    OrbitContainerHost<AppCoordinatorState, AppCoordinatorState, Nothing> {
    override val container = orbitContainer<AppCoordinatorState, Nothing>(AppCoordinatorState()) { presentHome() }

    private val isCheckingProfile = AtomicBoolean(false)

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

    /** 덮개(로그인 · 가입)를 마치거나 건너뛰었다. */
    fun onLaunchLoginFinished(result: LaunchLoginResult) = intent {
        when (result) {
            LaunchLoginResult.Completed, LaunchLoginResult.Skipped -> reduce { state.copy(launchLogin = false, signUp = null) }
        }
    }

    /** `ON_START`(포그라운드 진입). 홈이고 덮개가 없을 때만 프로필을 다시 확인한다. */
    fun onForegrounded() = intent {
        if (state.routes.lastOrNull() == AppRoute.Home) recheckProfile()
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
            sessionExpiry.collect { reduce { state.copy(launchLogin = true, signUp = null) } }
        }
        if (!launchLogin) intent { recheckProfile() }
    }

    /**
     * `GET api/v1/me`를 한 번 부른다. 진행 중이면 겹쳐 부르지 않는다. 그 사이 세션이 바뀌었으면 결과를 버린다.
     * 성공 ∧ 가입 조건 미달 → 가입 덮개, 401 → 세션 만료 경로(launchLogin), 오프라인 · 서버 오류 → 그대로(자동 재시도 없음).
     */
    private suspend fun recheckProfile() = subIntent {
        if (state.authCover != null || !isCheckingProfile.compareAndSet(false, true)) return@subIntent
        try {
            val sessionId = currentSessionId() ?: return@subIntent
            val result =
                try {
                    Result.success(authRepository.fetchProfile())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure<AuthProfile>(e)
                }
            onProfileRechecked(sessionId, result)
        } finally {
            isCheckingProfile.set(false)
        }
    }

    private suspend fun onProfileRechecked(sessionId: String, result: Result<AuthProfile>) = subIntent {
        val current = currentSessionId()
        if (result.exceptionOrNull() is AuthDomainError.NotAuthenticated && (current == null || current == sessionId)) {
            // 토큰 갱신이 401로 끝났으면 저장소는 이미 비었고 만료 이벤트도 온다. 남아 있으면 여기서 지운다.
            if (current != null) clearSession()
            reduce { state.copy(launchLogin = true, signUp = null) }
            return@subIntent
        }
        val profile = result.getOrNull()
        if (current != sessionId || profile == null || profile.isEligible || state.authCover != null) return@subIntent
        val route =
            AuthRoute.SignUp(
                userId = profile.user.id,
                nickname = profile.user.nickname,
                currentTermsVersion = profile.currentTermsVersion,
                sessionId = sessionId,
            )
        reduce { state.copy(signUp = route) }
    }

    private suspend fun clearSession() {
        try {
            authRepository.clearSession()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FestiCoreLogger.error("세션을 지울 수 없음: ${e::class.simpleName}", LogCategory.AUTH)
        }
    }

    private companion object {
        val SplashDuration = 2.seconds
    }
}
