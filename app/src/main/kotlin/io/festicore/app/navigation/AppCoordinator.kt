package io.festicore.app.navigation

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.core.logger.LogCategory
import io.festicore.core.storage.onboarding.FirstInstallOnboardingStore
import io.festicore.core.utility.deeplink.FestiCoreDeeplink
import io.festicore.domain.auth.auth.AuthRepository
import io.festicore.domain.auth.entity.AuthDomainError
import io.festicore.domain.auth.entity.AuthProfile
import io.festicore.domain.auth.entity.AuthUser
import io.festicore.feature.auth.AuthRoute
import io.festicore.feature.auth.LaunchLoginResult
import io.festicore.feature.auth.LoginContext
import io.festicore.feature.onboarding.OnboardingRoute
import io.festicore.feature.profile.AccountState
import io.festicore.service.auth.expiry.SessionExpiry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * 루트 화면 상태. [routes]는 항상 한 화면(Splash · Onboarding · Home)이고 다음 화면으로 갈 때 대체한다.
 * [launchLogin]은 Home 위 첫 진입 로그인 덮개 표시 여부다(iOS `Home(launchLogin:)`). 키와 분리해 덮개가 바뀌어도 메인 탭 상태를 유지한다.
 * [signUp]은 로그인 상태 프로필 재확인에서 가입 조건을 채우지 못했을 때 Home 위에 띄우는 가입 정보 덮개다.
 * [coverEpoch]는 덮개 세대다. 오르면 덮개 종류가 같아도 덮개 흐름을 시작 화면부터 새로 만든다(로그인 직후 · 첫 진입 가입 중 세션 만료).
 * [coverTop]은 덮개 흐름이 알린 맨 위 화면이다. 첫 진입 덮개 안에서 가입 화면이 보이는지 판단한다.
 * [loginSheet]는 메인 탭 위 로그인 시트의 문맥이다(iOS `presentation == .login`). 덮개가 아니라 딥링크 처리에는 영향이 없다.
 * [loginSheetId]는 시트를 새로 열 때마다 오른다. 시트가 매번 새 로그인 상태로 시작하게 한다.
 * [directLogin]은 마이 로그인 카드의 Google 바로 로그인이 시트 없이 진행 중인지다(iOS `presentation == .directLogin`, 설계 쟁점 M12).
 * 바로 로그인은 [loginSheetId]의 로그인 상태를 시트를 띄우지 않고 쓰고, 실패 문구가 있으면 같은 상태로 시트를 띄운다.
 */
data class AppCoordinatorState(
    val routes: List<NavKey> = listOf(AppRoute.Splash),
    val launchLogin: Boolean = false,
    val signUp: AuthRoute.SignUp? = null,
    val coverEpoch: Int = 0,
    val coverTop: AuthRoute? = null,
    val loginSheet: LoginContext? = null,
    val loginSheetId: Int = 0,
    val directLogin: Boolean = false,
) {
    /** Home 위 덮개의 시작 화면. 없으면 덮개를 그리지 않는다. */
    val authCover: AuthRoute?
        get() = if (launchLogin) AuthRoute.LaunchLogin else signUp

    /** 가입 화면이 보인다(재확인 가입 덮개, 또는 첫 진입 덮개 안의 가입 화면). */
    val showsSignUp: Boolean
        get() = signUp != null || (launchLogin && coverTop is AuthRoute.SignUp)

    fun withoutCover(): AppCoordinatorState = copy(launchLogin = false, signUp = null, coverTop = null)

    /** 대기 딥링크를 열 수 있다: 홈이고 덮개(로그인 · 가입)가 없다(iOS `deeplinkReceived`의 `home` · `!isMainTabCovered`). */
    val canOpenDeeplink: Boolean
        get() = routes == listOf(AppRoute.Home) && authCover == null
}

/** 루트 밖으로 나가는 일. 메인 탭이 처리한다. */
sealed interface AppCoordinatorSideEffect {
    /** 대기에서 꺼낸 딥링크를 메인 탭에서 연다(iOS `openDeeplink`). */
    data class OpenDeeplink(val deeplink: FestiCoreDeeplink) : AppCoordinatorSideEffect
}

/**
 * iOS `AppCoordinator`. 스플래시 → 온보딩 → 홈(비로그인이면 launchLogin 덮개) 전환을 정한다.
 * 로그인 상태로 홈에 들어갈 때와 포그라운드마다 프로필을 다시 확인한다(iOS `SessionCoordinator.foregrounded`).
 * [updateCurrentUserId]로 확인한 현재 사용자(iOS `verifiedUserID`)를 채우고 비운다.
 * 채움: 재확인 성공 · 로그인 완료 · 가입 완료. 비움: 세션 만료 · 재확인 401 · 로그인 직후 401 · 로그아웃 · 탈퇴.
 * 계정 상태([account])를 소유해 마이 탭에 입력으로 넘기고(설계 쟁점 M13), 로그아웃 · 탈퇴는 [accountCoordinator]로 실행한다(iOS `AccountCoordinator`).
 */
class AppCoordinator(
    private val onboardingStore: FirstInstallOnboardingStore,
    private val isLoggedIn: suspend () -> Boolean,
    private val sessionExpired: Flow<SessionExpiry>,
    private val authRepository: AuthRepository,
    private val currentSessionId: suspend () -> String?,
    private val updateCurrentUserId: (String?) -> Unit,
    private val deeplinkReceived: Flow<Unit>,
    private val consumePendingDeeplink: suspend () -> FestiCoreDeeplink?,
    /** 페스티벌이 끝나고 30일이 지난 팩을 지운다(iOS `deleteExpiredPacks`, 설계 쟁점 M8). */
    private val deleteExpiredPacks: suspend () -> Unit,
    private val accountCoordinator: AccountCoordinator = AccountCoordinator(authRepository),
) : ViewModel(),
    OrbitContainerHost<AppCoordinatorState, AppCoordinatorState, AppCoordinatorSideEffect> {
    override val container: Container<AppCoordinatorState, AppCoordinatorSideEffect> =
        orbitContainer(AppCoordinatorState()) {
            intent { observeSessionExpiry() }
            intent { observeDeeplinks() }
        }

    private val hasSplashStarted = AtomicBoolean(false)
    private val isCheckingProfile = AtomicBoolean(false)
    private val isRecheckPending = AtomicBoolean(false)

    private val accountState = MutableStateFlow(AccountState())

    /**
     * 마이 탭에 넘기는 계정 상태(iOS `ProfileFeature.View.accountChanged` 입력, 설계 쟁점 M13).
     * 루트 화면 상태와 따로 두어 계정 값이 바뀌어도 루트 흐름을 다시 그리지 않는다.
     */
    val account: StateFlow<AccountState> = accountState.asStateFlow()

    /** 계정 작업 세대(iOS `accountOperationGeneration`). 세션 만료로 작업을 끊으면 올려 늦은 결과를 버린다. */
    private val accountOperationGeneration = AtomicInteger(0)

    @Volatile private var runningAccountOperation: AccountOperation? = null

    private var accountOperationJob: Job? = null

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
     * 완료는 이미 계정을 확인했고(확인한 프로필로 마이 탭 계정 상태를 채운다), 가입 닫기는 iOS에서 첫 진입 화면이 닫히는 전이가 아니라 재확인하지 않는다.
     */
    fun onLaunchLoginFinished(result: LaunchLoginResult) = intent {
        when (result) {
            is LaunchLoginResult.Completed -> {
                // 그 사이 세션이 바뀌었거나 없어졌으면 다른 세션의 사용자라 현재 사용자로 두지 않는다.
                onLoginCompleted(result)
                closeCover()
            }

            LaunchLoginResult.SignUpCancelled -> closeCover()

            LaunchLoginResult.Skipped -> {
                closeCover()
                recheckProfile()
            }

            is LaunchLoginResult.SessionLost -> onLaunchSessionLost(result)
        }
    }

    /**
     * 로그인이 필요한 동작을 눌렀다(iOS `AuthCoordinator.loginRequested`). 홈이고 덮개가 없을 때만 시트를 연다.
     * 이미 떠 있으면 문맥만 바꾸고 진행 중인 로그인 상태는 그대로 둔다.
     */
    fun onLoginRequested(context: LoginContext) = intent {
        if (state.routes.lastOrNull() != AppRoute.Home || state.authCover != null) return@intent
        // 바로 로그인이 진행 중이면 그 로그인 상태를 시트로 보인다(iOS `loginRequested`가 `directLogin`을 `login`으로 바꾼다).
        val id = if (state.loginSheet == null && !state.directLogin) state.loginSheetId + 1 else state.loginSheetId
        reduce { state.copy(loginSheet = context, loginSheetId = id, directLogin = false) }
    }

    /**
     * 마이 로그인 카드의 Google 버튼(iOS `startDirectLogin`, `AuC:161-172`, 설계 쟁점 M12). 시트 없이 새 로그인 상태로 바로 로그인을 시작한다.
     * 홈이고 덮개 · 시트 · 진행 중인 바로 로그인이 없을 때만 시작한다. 결과는 시트 로그인과 같은 [onLoginSheetFinished] · [onLoginSheetSignUpRequired]로 온다.
     */
    fun onGoogleLoginRequested() = intent {
        if (state.routes.lastOrNull() != AppRoute.Home || state.authCover != null) return@intent
        if (state.loginSheet != null || state.directLogin) return@intent
        reduce { state.copy(directLogin = true, loginSheetId = state.loginSheetId + 1) }
    }

    /**
     * 바로 로그인이 실패했다(iOS `AuthCoordinator` `signInFailed`, `AuC:298-309`). 취소 · 중복 요청([showsMessage] false)이면 조용히 끝내고,
     * 오류 문구가 있으면 같은 로그인 상태로 시트(문맥 `Profile`)를 띄워 문구를 보인다. 바로 로그인 중이 아니면 무시한다.
     */
    fun onDirectLoginFailed(showsMessage: Boolean) = intent {
        if (!state.directLogin) return@intent
        reduce { state.copy(directLogin = false, loginSheet = if (showsMessage) LoginContext.Profile else state.loginSheet) }
    }

    /** 시트를 "나중에 할게요" · 딤 탭 · 끌기 · 뒤로 닫았다(iOS `dismissFlow`). 세션은 건드리지 않고 재확인도 하지 않는다. */
    fun onLoginSheetDismissed() = intent { reduce { state.copy(loginSheet = null) } }

    /**
     * 시트 로그인이 끝났다(iOS `acceptLogin`). 완료면 그 세션이 현재 세션일 때만 현재 사용자로 두고 시트를 닫는다.
     * 계정 확인 401이면 다른 세션의 결과가 아닐 때 현재 사용자를 비우고 시트를 닫는다. 시트 흐름은 첫 진입 덮개를 띄우지 않는다.
     */
    fun onLoginSheetFinished(result: LaunchLoginResult) = intent {
        when (result) {
            is LaunchLoginResult.Completed -> {
                onLoginCompleted(result)
            }

            is LaunchLoginResult.SessionLost -> {
                val current = currentSessionId()
                if (current != null && current != result.sessionId) {
                    if (state.directLogin) reduce { state.copy(directLogin = false) }
                    return@intent
                }
                updateCurrentUserId(null)
                forgetAccount()
            }

            LaunchLoginResult.Skipped, LaunchLoginResult.SignUpCancelled -> Unit
        }
        reduce { state.copy(loginSheet = null, directLogin = false) }
    }

    /**
     * 시트 로그인 계정이 가입 조건을 채우지 못했다(신규 계정 등). 시트를 닫고 재확인과 같은 가입 정보 덮개를 띄운다(iOS `presentation = .onboarding`).
     * iOS `acceptLogin`과 같이 확인한 사용자를 둔다. 그 사이 세션이 바뀌었거나 다른 덮개가 떴으면 시트만 닫는다.
     * 계정 상태는 [onLoginCompleted]와 같이 같은 사용자의 이전 프로필만 남긴다(다른 계정의 닉네임이 보이지 않게).
     */
    fun onLoginSheetSignUpRequired(route: AuthRoute.SignUp) = intent {
        if (currentSessionId() != route.sessionId || state.authCover != null) {
            reduce { state.copy(loginSheet = null, directLogin = false) }
            return@intent
        }
        updateCurrentUserId(route.userId)
        accountState.update { account ->
            account.copy(profile = account.profile?.takeIf { it.user.id == route.userId }, hasSession = true)
        }
        reduce { state.copy(loginSheet = null, directLogin = false, signUp = route) }
    }

    /** 덮개 흐름의 맨 위 화면이 바뀌었다. 덮개가 없으면(이미 닫힘) 무시한다. */
    fun onAuthCoverTopChanged(route: AuthRoute) = intent {
        if (state.authCover == null) return@intent
        reduce { state.copy(coverTop = route) }
    }

    /**
     * `ON_START`(포그라운드 진입). 홈이고 첫 진입 로그인 화면이 보이지 않을 때 프로필을 다시 확인한다.
     * 홈이면 만료된 팩도 지운다. 이전 정리가 돌고 있으면 취소하고 새로 시작한다(iOS `cancelInFlight`).
     */
    fun onForegrounded() = intent {
        if (state.routes.lastOrNull() != AppRoute.Home) return@intent
        expiredPackCleanup?.cancel()
        expiredPackCleanup = intent { cleanUpExpiredPacks() }
        recheckProfile()
    }

    private var expiredPackCleanup: Job? = null

    private suspend fun cleanUpExpiredPacks() {
        try {
            deleteExpiredPacks()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FestiCoreLogger.error("만료된 팩을 지우지 못함: ${e::class.simpleName}", LogCategory.APP)
        }
    }

    /**
     * 깃발 팔로우 · 신고가 약관 재동의(403 `TERMS_REQUIRED`)를 알렸다(iOS `session.retryProfile`, 설계 쟁점 F6).
     * 홈이면 프로필을 다시 확인한다. 약관이 필요하면 재확인 결과로 가입 정보 덮개가 뜬다.
     */
    fun onTermsAcceptanceRequested() = intent {
        if (state.routes.lastOrNull() == AppRoute.Home) recheckProfile()
    }

    /** 계정 화면 "계정 정보 다시 확인"(iOS `profileRefreshRequested` → `retryProfile`). 홈이면 프로필을 다시 읽는다. */
    fun onProfileRefreshRequested() = intent {
        if (state.routes.lastOrNull() == AppRoute.Home) recheckProfile()
    }

    /** 계정 화면 로그아웃 확인(iOS `AccountCoordinator.signOutRequested`). */
    fun onLogoutRequested() = startAccountOperation(AccountOperation.SignOut)

    /** 계정 화면 회원 탈퇴 확인(iOS `AccountCoordinator.withdrawRequested`). */
    fun onWithdrawRequested() = startAccountOperation(AccountOperation.Withdraw)

    /** 닉네임을 바꿨다(iOS `session.profileUpdated`). 지금 계정과 같은 사용자일 때만 계정 상태의 사용자를 바꾼다. */
    fun onNicknameUpdated(user: AuthUser) {
        accountState.update { account ->
            val profile = account.profile
            if (profile == null || profile.user.id != user.id) account else account.copy(profile = profile.copy(user = user))
        }
    }

    /**
     * 계정 작업을 시작한다(iOS `startOperation`, `AcC:140-153`). 세션이 있고 처리 중이 아닐 때만 세대를 올리고 문구를 비운 뒤 실행한다.
     * 결과는 [onAccountOperationFinished]가 세대와 시작 세션을 비교해 반영한다.
     */
    private fun startAccountOperation(operation: AccountOperation) {
        val current = accountState.value
        if (!current.hasSession || current.isLoading) return
        val generation = accountOperationGeneration.incrementAndGet()
        runningAccountOperation = operation
        accountState.update { it.copy(isLoading = true, message = null) }
        accountOperationJob =
            intent {
                val sessionId = currentSessionId()
                val result = accountCoordinator.run(operation)
                onAccountOperationFinished(generation, sessionId, operation, result)
            }
    }

    /**
     * 계정 작업 결과(iOS `handleMyAccountFinished`, `AcC:196-228`). 세대가 다르면(세션 만료로 끊김) 버린다.
     * 끝났을 때 시작 세션([sessionId])과 다른 세션이 저장돼 있으면(만료 알림 전에 다시 로그인함) 새 계정을 건드리지 않고 처리 중 표시만 끈다.
     * 성공 · 로그인 잃음이면 현재 사용자와 계정 상태를 비우고(탈퇴 중 로그인을 잃었으면 결과 불명 문구) 가입 정보 덮개가 있으면 닫는다.
     * 로그인 시트 상태는 건드리지 않는다(`AcC:231-242`). 그 외 실패면 계정을 두고 실패 문구만 보인다.
     */
    private suspend fun onAccountOperationFinished(
        generation: Int,
        sessionId: String?,
        operation: AccountOperation,
        result: AccountOperationResult,
    ) = subIntent {
        if (accountOperationGeneration.get() != generation) return@subIntent
        runningAccountOperation = null
        accountOperationJob = null
        val current = currentSessionId()
        if (current != null && current != sessionId) {
            accountState.update { it.copy(isLoading = false) }
            return@subIntent
        }
        when (result) {
            AccountOperationResult.Failed -> {
                val message =
                    when (operation) {
                        AccountOperation.SignOut -> AccountCoordinator.SIGN_OUT_FAILED
                        AccountOperation.Withdraw -> AccountCoordinator.WITHDRAW_FAILED
                    }
                accountState.update { it.copy(isLoading = false, message = message) }
            }

            AccountOperationResult.Succeeded, AccountOperationResult.SessionLost -> {
                val message =
                    AccountCoordinator.SESSION_EXPIRED_DURING_WITHDRAW.takeIf {
                        result == AccountOperationResult.SessionLost && operation == AccountOperation.Withdraw
                    }
                updateCurrentUserId(null)
                accountState.value = AccountState(message = message)
                if (state.signUp != null && !state.launchLogin) closeCover()
            }
        }
    }

    /**
     * 로그인을 잃었다(세션 만료 · 401). 진행 중인 계정 작업을 끊고(iOS `accountOperationInterrupted` → `cancelOperation`) 계정 상태를 비운다.
     * 탈퇴 중이었으면 결과 불명 문구를 두고, 아니면 이전 문구를 그대로 둔다(iOS `handleSessionExpired`).
     */
    private fun forgetAccount() {
        val interrupted = runningAccountOperation
        runningAccountOperation = null
        accountOperationGeneration.incrementAndGet()
        accountOperationJob?.cancel()
        accountOperationJob = null
        accountState.update {
            AccountState(
                message = if (interrupted ==
                    AccountOperation.Withdraw
                ) {
                    AccountCoordinator.SESSION_EXPIRED_DURING_WITHDRAW
                } else {
                    it.message
                },
            )
        }
    }

    /**
     * 로그인 완료가 현재 세션의 결과면 그 사용자를 현재 사용자로 두고 계정 상태를 확인한 프로필로 채운다.
     * 확인이 실패해 프로필이 없으면 같은 사용자의 이전 프로필만 남기고, 다음 재확인에 맡긴다.
     */
    private suspend fun onLoginCompleted(result: LaunchLoginResult.Completed) {
        if (currentSessionId() != result.sessionId) return
        updateCurrentUserId(result.userId)
        accountState.update { account ->
            val profile = result.profile ?: account.profile?.takeIf { it.user.id == result.userId }
            account.copy(profile = profile, hasSession = true)
        }
    }

    /**
     * 로그인 직후 계정 확인 401(iOS `SessionCoordinator.swift:196-223,341-351`). 그 사이 다른 로그인이 세션을 저장했으면 오래된 결과라 버린다.
     * 이 세션의 만료 이벤트가 있었으면 첫 진입 흐름이라 launchLogin을 유지하고 덮개 세대를 올려 LaunchLogin부터 새로(문구 없이) 만든다.
     * 만료 이벤트가 없으면(세션은 로그인 화면이 조건 삭제했다) 덮개를 닫아 비로그인 Home으로 간다. 재확인은 하지 않는다.
     */
    private suspend fun onLaunchSessionLost(result: LaunchLoginResult.SessionLost) = subIntent {
        val current = currentSessionId()
        if (current != null && current != result.sessionId) return@subIntent
        updateCurrentUserId(null)
        forgetAccount()
        if (result.expired) {
            reduce { state.copy(launchLogin = true, signUp = null, coverEpoch = state.coverEpoch + 1, coverTop = null) }
        } else {
            closeCover()
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
        val loggedIn = isLoggedIn()
        accountState.update { it.copy(hasSession = loggedIn) }
        enterHome(launchLogin = !loggedIn)
    }

    /**
     * 새 딥링크 신호를 받으면 대기 값을 꺼내 본다. 열 수 있게 되는 순간(홈 진입 [enterHome] · 덮개 닫힘 [closeCover])에도 다시 꺼낸다
     * (iOS `deeplinkReceived` · `AppCoordinator.swift:146-158` · `AppCoordinator+Route.swift:86-89`).
     */
    private suspend fun observeDeeplinks(): Unit = subIntent {
        deeplinkReceived.collect { onDeeplinkReceived() }
    }

    /** 덮개를 닫는다. 메인 탭이 보이게 되면 대기 딥링크를 꺼낸다. */
    private suspend fun closeCover() = subIntent {
        reduce { state.withoutCover() }
        onDeeplinkReceived()
    }

    /** 열 수 없으면 대기 값을 남겨 두고, 열 수 있게 되면 다시 꺼낸다. */
    private fun onDeeplinkReceived() = intent {
        if (!state.canOpenDeeplink) return@intent
        val deeplink =
            try {
                consumePendingDeeplink()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FestiCoreLogger.error("대기 딥링크를 꺼낼 수 없음: ${e::class.simpleName}", LogCategory.APP)
                null
            } ?: return@intent
        postSideEffect(AppCoordinatorSideEffect.OpenDeeplink(deeplink))
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
        // 지금 세션이 없거나 만료된 세션이면 현재 사용자를 비운다. 그 사이 저장된 다른 세션의 사용자는 남긴다.
        val current = currentSessionId()
        if (current == null || current == sessionId) {
            updateCurrentUserId(null)
            forgetAccount()
        }
        when {
            state.routes.lastOrNull() != AppRoute.Home -> reduce { state.copy(launchLogin = true, signUp = null) }

            state.launchLogin -> {
                val top = state.coverTop
                if (top is AuthRoute.SignUp && top.sessionId == sessionId) {
                    reduce { state.copy(coverEpoch = state.coverEpoch + 1, coverTop = null) }
                }
            }

            else -> closeCover()
        }
    }

    /** 홈으로 바꾼다. 첫 진입 덮개는 건너뛰기를 저장하지 않아 실행마다 다시 뜬다. */
    private suspend fun enterHome(launchLogin: Boolean) = subIntent {
        if (state.routes.lastOrNull() == AppRoute.Home) return@subIntent
        // 홈 전에 받은 만료(launchLogin)는 유지한다.
        reduce { state.copy(routes = listOf(AppRoute.Home), launchLogin = launchLogin || state.launchLogin) }
        onDeeplinkReceived()
        if (!state.launchLogin) intent { recheckProfile() }
    }

    /**
     * `GET api/v1/me`를 한 번 부른다. 진행 중이면 겹쳐 부르지 않는다. 그 사이 세션이 바뀌었으면 결과를 버린다.
     * 첫 진입 로그인 화면이 보이면 하지 않는다(iOS `acceptsLogin` 검사). 재확인은 로그인 화면을 덮어쓰지 않는다.
     * 로그아웃 · 탈퇴 중이면 하지 않는다(계정 상태의 처리 중 표시를 작업이 가진다). 읽는 동안 계정 상태를 처리 중으로 둔다.
     */
    private suspend fun recheckProfile(): Unit = subIntent {
        if (state.launchLogin && state.coverTop !is AuthRoute.SignUp) return@subIntent
        if (runningAccountOperation != null) return@subIntent
        if (!isCheckingProfile.compareAndSet(false, true)) {
            // 진행 중에 온 요청은 하나로 모아 끝난 뒤 한 번만 다시 확인한다.
            isRecheckPending.set(true)
            return@subIntent
        }
        try {
            val sessionId = currentSessionId()
            if (sessionId == null) {
                accountState.update { it.copy(profile = null, hasSession = false, isLoading = false) }
            } else {
                accountState.update { it.copy(hasSession = true, isLoading = true) }
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
            updateCurrentUserId(null)
            forgetAccount()
            if (authRepository.hasSessionExpired(sessionId)) return@subIntent
            if (current != null) clearSessionIf(sessionId)
            closeCover()
            return@subIntent
        }
        val profile = result.getOrNull()
        if (current != sessionId || profile == null) {
            // 오프라인 · 서버 오류 · 다른 세션의 결과면 계정 값은 두고 처리 중 표시만 끈다.
            accountState.update { it.copy(hasSession = current != null, isLoading = false) }
            return@subIntent
        }
        // iOS `handleProfileLoaded`와 같이 가입 조건과 관계없이 확인한 사용자를 둔다.
        updateCurrentUserId(profile.user.id)
        accountState.update { it.copy(profile = profile, hasSession = true, isLoading = false) }
        if (profile.isEligible) {
            if (state.showsSignUp) closeCover()
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
