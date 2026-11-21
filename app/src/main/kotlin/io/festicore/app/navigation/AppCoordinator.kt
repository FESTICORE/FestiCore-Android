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
import io.festicore.service.auth.SessionExpiry
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
 * [coverEpoch]는 덮개 세대다. 오르면 덮개 종류가 같아도 덮개 흐름을 시작 화면부터 새로 만든다(로그인 직후 · 첫 진입 가입 중 세션 만료).
 * [coverTop]은 덮개 흐름이 알린 맨 위 화면이다. 첫 진입 덮개 안에서 가입 화면이 보이는지 판단한다.
 */
data class AppCoordinatorState(
    val routes: List<NavKey> = listOf(AppRoute.Splash),
    val launchLogin: Boolean = false,
    val signUp: AuthRoute.SignUp? = null,
    val coverEpoch: Int = 0,
    val coverTop: AuthRoute? = null,
) {
    /** Home 위 덮개의 시작 화면. 없으면 덮개를 그리지 않는다. */
    val authCover: AuthRoute?
        get() = if (launchLogin) AuthRoute.LaunchLogin else signUp

    /** 가입 화면이 보인다(재확인 가입 덮개, 또는 첫 진입 덮개 안의 가입 화면). */
    val showsSignUp: Boolean
        get() = signUp != null || (launchLogin && coverTop is AuthRoute.SignUp)

    fun withoutCover(): AppCoordinatorState = copy(launchLogin = false, signUp = null, coverTop = null)
}

/**
 * iOS `AppCoordinator`. 스플래시 → 온보딩 → 홈(비로그인이면 launchLogin 덮개) 전환을 정한다.
 * 로그인 상태로 홈에 들어갈 때와 포그라운드마다 프로필을 다시 확인한다(iOS `SessionCoordinator.foregrounded`).
 */
class AppCoordinator(
    private val onboardingStore: FirstInstallOnboardingStore,
    private val isLoggedIn: suspend () -> Boolean,
    private val sessionExpired: Flow<SessionExpiry>,
    private val authRepository: AuthRepository,
    private val currentSessionId: suspend () -> String?,
) : ViewModel(),
    OrbitContainerHost<AppCoordinatorState, AppCoordinatorState, Nothing> {
    override val container = orbitContainer<AppCoordinatorState, Nothing>(AppCoordinatorState()) {
        intent { observeSessionExpiry() }
    }

    private val hasSplashStarted = AtomicBoolean(false)
    private val isCheckingProfile = AtomicBoolean(false)
    private val isRecheckPending = AtomicBoolean(false)

    /** 스플래시가 처음 보였다(iOS `SplashFeature` `onAppear`). 그때부터 2초를 잰다. 두 번째 호출은 무시한다(iOS `hasStarted`). */
    fun onSplashAppeared() = intent {
        if (state.routes.lastOrNull() != AppRoute.Splash || !hasSplashStarted.compareAndSet(false, true)) return@intent
        presentHome()
    }

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

    /**
     * 덮개(로그인 · 가입)를 마치거나 건너뛰었거나 닫았거나 로그인 직후 세션을 잃었다.
     * 덮개 상태를 먼저 바꾸고 그다음 재확인한다. 재확인은 첫 진입 건너뛰기에서만 한 번 한다(iOS `AppCoordinator.swift:146-153`).
     * 완료는 이미 계정을 확인했고, 가입 닫기는 iOS에서 첫 진입 화면이 닫히는 전이가 아니라 재확인하지 않는다.
     */
    fun onLaunchLoginFinished(result: LaunchLoginResult) = intent {
        when (result) {
            LaunchLoginResult.Completed,
            LaunchLoginResult.SignUpCancelled,
            -> reduce { state.withoutCover() }

            LaunchLoginResult.Skipped -> {
                reduce { state.withoutCover() }
                recheckProfile()
            }

            is LaunchLoginResult.SessionLost -> onLaunchSessionLost(result)
        }
    }

    /** 덮개 흐름의 맨 위 화면이 바뀌었다. 덮개가 없으면(이미 닫힘) 무시한다. */
    fun onAuthCoverTopChanged(route: AuthRoute) = intent {
        if (state.authCover == null) return@intent
        reduce { state.copy(coverTop = route) }
    }

    /** `ON_START`(포그라운드 진입). 홈이고 첫 진입 로그인 화면이 보이지 않을 때 프로필을 다시 확인한다. */
    fun onForegrounded() = intent {
        if (state.routes.lastOrNull() == AppRoute.Home) recheckProfile()
    }

    /**
     * 로그인 직후 계정 확인 401(iOS `SessionCoordinator.swift:196-223,341-351`). 그 사이 다른 로그인이 세션을 저장했으면 오래된 결과라 버린다.
     * 이 세션의 만료 이벤트가 있었으면 첫 진입 흐름이라 launchLogin을 유지하고 덮개 세대를 올려 LaunchLogin부터 새로(문구 없이) 만든다.
     * 만료 이벤트가 없으면(세션은 로그인 화면이 조건 삭제했다) 덮개를 닫아 비로그인 Home으로 간다. 재확인은 하지 않는다.
     */
    private suspend fun onLaunchSessionLost(result: LaunchLoginResult.SessionLost) = subIntent {
        val current = currentSessionId()
        if (current != null && current != result.sessionId) return@subIntent
        if (result.expired) {
            reduce { state.copy(launchLogin = true, signUp = null, coverEpoch = state.coverEpoch + 1, coverTop = null) }
        } else {
            reduce { state.withoutCover() }
        }
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

    /** 생성 때부터 만료를 구독한다. 구독 시점 세대는 이미 반영된 것으로 보고, 커질 때마다 한 번 처리한다. */
    private suspend fun observeSessionExpiry() = subIntent {
        var handled: Long? = null
        sessionExpired.collect { expiry ->
            val previous = handled
            handled = expiry.generation
            if (previous != null && expiry.generation > previous) onSessionExpired(expiry.sessionId)
        }
    }

    /**
     * 세션 만료(iOS `SessionCoordinator.handleSessionExpired`). launchLogin은 첫 진입 흐름에서만 띄운다.
     * - 홈 전: 홈에서 launchLogin(시작 때 비로그인과 같다). 상태에 남았다가 [enterHome]에서 쓰인다.
     * - 첫 진입 덮개: 만료된 세션의 가입 화면이 보이면 덮개 세대를 올려 LaunchLogin부터 새로 만든다.
     *   로그인 화면이 보이면 진행 중인 다른 세션의 로그인 시도를 지우지 않도록 그대로 둔다.
     * - 재확인 가입 덮개: 덮개를 닫아 비로그인 메인 탭으로. 덮개가 없으면 그대로(launchLogin을 켜지 않는다).
     */
    private suspend fun onSessionExpired(sessionId: String?) = subIntent {
        when {
            state.routes.lastOrNull() != AppRoute.Home -> reduce { state.copy(launchLogin = true, signUp = null) }

            state.launchLogin -> {
                val top = state.coverTop
                if (top is AuthRoute.SignUp && (top.sessionId == null || top.sessionId == sessionId)) {
                    reduce { state.copy(coverEpoch = state.coverEpoch + 1, coverTop = null) }
                }
            }

            else -> reduce { state.withoutCover() }
        }
    }

    /** 홈으로 바꾼다. 첫 진입 덮개는 건너뛰기를 저장하지 않아 실행마다 다시 뜬다. */
    private suspend fun enterHome(launchLogin: Boolean) = subIntent {
        if (state.routes.lastOrNull() == AppRoute.Home) return@subIntent
        // 홈 전에 받은 만료(launchLogin)는 유지한다.
        reduce { state.copy(routes = listOf(AppRoute.Home), launchLogin = launchLogin || state.launchLogin) }
        if (!state.launchLogin) intent { recheckProfile() }
    }

    /**
     * `GET api/v1/me`를 한 번 부른다. 진행 중이면 겹쳐 부르지 않는다. 그 사이 세션이 바뀌었으면 결과를 버린다.
     * 첫 진입 로그인 화면이 보이면 하지 않는다(iOS `acceptsLogin` 검사). 재확인은 로그인 화면을 덮어쓰지 않는다.
     */
    private suspend fun recheckProfile(): Unit = subIntent {
        if (state.launchLogin && state.coverTop !is AuthRoute.SignUp) return@subIntent
        if (!isCheckingProfile.compareAndSet(false, true)) {
            // 진행 중에 온 요청은 하나로 모아 끝난 뒤 한 번만 다시 확인한다.
            isRecheckPending.set(true)
            return@subIntent
        }
        try {
            val sessionId = currentSessionId()
            if (sessionId != null) {
                val result =
                    try {
                        Result.success(authRepository.fetchProfile())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure<AuthProfile>(e)
                    }
                onProfileRechecked(sessionId, result)
            }
        } finally {
            isCheckingProfile.set(false)
        }
        if (isRecheckPending.getAndSet(false)) recheckProfile()
    }

    /**
     * 재확인 결과(iOS `SessionCoordinator.handleProfileLoaded`).
     * - 401: 이 세션의 만료 이벤트가 있었으면 만료 처리에 맡긴다(iOS는 세대를 올려 늦은 결과를 버린다).
     *   없으면 이 세션이 남아 있을 때 지우고 덮개를 닫아 비로그인 메인 탭으로 간다(launchLogin을 켜지 않는다).
     * - 조건 충족: 가입 화면이 떠 있으면 닫는다. 미달: 같은 사용자 · 세션의 재확인 가입 덮개가 떠 있으면 새로 만들지 않고
     *   프로필(닉네임 · 약관 버전 · 재동의)만 바꿔 열린 화면에 반영한다(초안 유지, 값이 같으면 그대로).
     *   그 밖의 덮개가 있으면 두고, 덮개가 없을 때만 재확인 가입 덮개를 띄운다. 오프라인 · 서버 오류는 그대로(자동 재시도 없음).
     */
    private suspend fun onProfileRechecked(sessionId: String, result: Result<AuthProfile>) = subIntent {
        val current = currentSessionId()
        if (result.exceptionOrNull() is AuthDomainError.NotAuthenticated && (current == null || current == sessionId)) {
            if (authRepository.hasSessionExpired(sessionId)) return@subIntent
            if (current != null) clearSessionIf(sessionId)
            reduce { state.withoutCover() }
            return@subIntent
        }
        val profile = result.getOrNull()
        if (current != sessionId || profile == null) return@subIntent
        if (profile.isEligible) {
            if (state.showsSignUp) reduce { state.withoutCover() }
            return@subIntent
        }
        val route =
            AuthRoute.SignUp(
                userId = profile.user.id,
                nickname = profile.user.nickname,
                currentTermsVersion = profile.currentTermsVersion,
                sessionId = sessionId,
                isReconsent = profile.user.termsAcceptedAt != null,
            )
        val open = state.signUp
        if (!state.launchLogin && open != null && open.userId == route.userId && open.sessionId == sessionId) {
            if (open != route) reduce { state.copy(signUp = route) }
            return@subIntent
        }
        if (state.authCover != null) return@subIntent
        reduce { state.copy(signUp = route) }
    }

    private suspend fun clearSessionIf(sessionId: String) {
        try {
            authRepository.clearSessionIf(sessionId)
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
