package io.festicore.app.navigation.main

import io.festicore.core.storage.onboarding.FirstInstallOnboardingStore
import io.festicore.core.utility.deeplink.FestiCoreDeeplink
import io.festicore.domain.auth.auth.AuthRepository
import io.festicore.domain.auth.entity.AuthDomainError
import io.festicore.domain.auth.entity.AuthLoginResult
import io.festicore.domain.auth.entity.AuthProfile
import io.festicore.domain.auth.entity.AuthUser
import io.festicore.domain.auth.sources.auth.CurrentUserStore
import io.festicore.feature.auth.AuthRoute
import io.festicore.feature.auth.LaunchLoginResult
import io.festicore.feature.auth.LoginContext
import io.festicore.feature.onboarding.OnboardingRoute
import io.festicore.service.auth.expiry.SessionExpiry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

class AppCoordinatorTest {
    private class FakeOnboardingStore(
        private var completed: Boolean,
        private val readError: Exception? = null,
        private val writeError: Exception? = null,
    ) : FirstInstallOnboardingStore {
        var markCount = 0
        var readCount = 0

        override suspend fun hasCompleted(): Boolean {
            readCount++
            return readError?.let { throw it } ?: completed
        }

        override suspend fun markCompleted() {
            markCount++
            writeError?.let { throw it }
            completed = true
        }
    }

    /** 프로필 재확인용 가짜 저장소. [profile]이 결과를 정하고, 부를 때마다 [sessionId]를 바꿀 수 있다. */
    private class FakeAuthRepository(var profile: suspend () -> AuthProfile = { eligible }) : AuthRepository {
        var sessionId: String? = "s1"
        var fetchCount = 0
        var clearCount = 0
        var expiredSessionId: String? = null

        override val sessionExpired: Flow<Unit> = emptyFlow()

        override suspend fun login(idToken: String): AuthLoginResult = throw AuthDomainError.Unavailable()

        override suspend fun fetchProfile(expectedSessionId: String?): AuthProfile {
            fetchCount++
            return profile()
        }

        override suspend fun signUp(
            nickname: String,
            termsVersion: String,
            expectedUserId: String,
            expectedSessionId: String?,
        ): AuthUser = throw AuthDomainError.Unavailable()

        override suspend fun updateNickname(nickname: String): AuthUser = throw AuthDomainError.Unavailable()

        override suspend fun signOut() = Unit

        override suspend fun withdraw() = Unit

        override suspend fun isLoggedIn(): Boolean = sessionId != null

        override fun hasSessionExpired(sessionId: String): Boolean = expiredSessionId == sessionId

        override suspend fun clearSession() {
            clearCount++
            sessionId = null
        }

        override suspend fun clearSessionIf(sessionId: String): Boolean {
            if (this.sessionId != sessionId) return false
            clearCount++
            this.sessionId = null
            return true
        }
    }

    /** 만료 팩 정리를 부른 횟수. */
    private var packCleanupCount = 0

    private fun coordinator(
        store: FirstInstallOnboardingStore,
        loggedIn: Boolean,
        expiry: Flow<SessionExpiry> = emptyFlow(),
        auth: FakeAuthRepository = FakeAuthRepository().apply { sessionId = null },
        users: CurrentUserStore = CurrentUserStore(),
        deeplinks: FakeDeeplinks? = null,
    ) = AppCoordinator(
        store,
        { loggedIn },
        expiry,
        auth,
        { auth.sessionId },
        users::update,
        deeplinks?.received ?: emptyFlow(),
        { deeplinks?.consume() },
        { packCleanupCount++ },
        {},
    )

    /** 대기 딥링크 하나와 새 딥링크 신호(앱의 `AppDeeplinkBridge` 대신). */
    private class FakeDeeplinks {
        val received = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var pending: FestiCoreDeeplink? = null
        var consumeCount = 0

        fun receive(deeplink: FestiCoreDeeplink) {
            pending = deeplink
            received.tryEmit(Unit)
        }

        fun consume(): FestiCoreDeeplink? {
            consumeCount++
            return pending.also { pending = null }
        }
    }

    private val home = AppCoordinatorState(routes = listOf(AppRoute.Home))

    private fun expiryFlow(generation: Long = 0) = MutableStateFlow(SessionExpiry(generation = generation, sessionId = null))

    private fun MutableStateFlow<SessionExpiry>.expire(sessionId: String) {
        value = SessionExpiry(generation = value.generation + 1, sessionId = sessionId)
    }

    private fun TestScope.assertSplashLasted2Seconds() = assertEquals(2_000L, testScheduler.currentTime)

    @Test
    fun `첫 설치면 스플래시 2초 뒤 온보딩으로 바꾼다`() = runTest {
        val scope = this
        coordinator(FakeOnboardingStore(completed = false), loggedIn = true).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(OnboardingRoute)) }
            scope.assertSplashLasted2Seconds()
        }
    }

    @Test
    fun `스플래시가 보이기 전에는 넘어가지 않고 보인 때부터 2초 뒤 이동한다`() = runTest {
        val scope = this
        val store = FakeOnboardingStore(completed = false)
        coordinator(store, loggedIn = true).test(this) {
            runOnCreate()
            scope.testScheduler.advanceTimeBy(5_000)
            scope.testScheduler.runCurrent()
            assertEquals(0, store.readCount)

            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(OnboardingRoute)) }
            assertEquals(7_000L, scope.testScheduler.currentTime)
        }
    }

    @Test
    fun `스플래시가 보였다는 두 번째 호출은 무시한다`() = runTest {
        val scope = this
        val store = FakeOnboardingStore(completed = false)
        coordinator(store, loggedIn = true).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            scope.testScheduler.advanceTimeBy(1_000)
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(OnboardingRoute)) }
            scope.assertSplashLasted2Seconds()

            scope.testScheduler.advanceTimeBy(5_000)
            scope.testScheduler.runCurrent()
        }
        assertEquals(1, store.readCount)
    }

    @Test
    fun `온보딩을 마치면 완료를 저장하고 비로그인이면 launchLogin 덮개로 홈에 간다`() = runTest {
        val store = FakeOnboardingStore(completed = false)
        coordinator(store, loggedIn = false).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(OnboardingRoute)) }

            containerHost.onOnboardingCompleted()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }
        }
        assertEquals(1, store.markCount)
    }

    @Test
    fun `온보딩을 마치고 로그인 상태면 덮개 없이 홈에 간다`() = runTest {
        val store = FakeOnboardingStore(completed = false)
        coordinator(store, loggedIn = true).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(OnboardingRoute)) }

            containerHost.onOnboardingCompleted()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = false) }
        }
        assertEquals(1, store.markCount)
    }

    @Test
    fun `완료 저장이 실패해도 홈에 간다`() = runTest {
        val store = FakeOnboardingStore(completed = false, writeError = IllegalStateException("disk"))
        coordinator(store, loggedIn = true).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(OnboardingRoute)) }

            containerHost.onOnboardingCompleted()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = false) }
        }
        assertEquals(1, store.markCount)
    }

    @Test
    fun `첫 설치 기록을 읽지 못하면 완료로 보고 세션을 확인한다`() = runTest {
        val scope = this
        val store = FakeOnboardingStore(completed = false, readError = IllegalStateException("read"))
        coordinator(store, loggedIn = false).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }
            scope.assertSplashLasted2Seconds()
        }
        assertEquals(0, store.markCount)
    }

    @Test
    fun `완료 기록이 있고 로그인 상태면 스플래시 뒤 덮개 없이 홈에 간다`() = runTest {
        val scope = this
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = false) }
            scope.assertSplashLasted2Seconds()
        }
    }

    @Test
    fun `완료 기록이 있고 비로그인이면 스플래시 뒤 launchLogin 덮개로 홈에 간다`() = runTest {
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }
        }
    }

    @Test
    fun `시나리오 11 - 홈(덮개 없음)에서 세션 만료 이벤트를 받아도 launchLogin을 띄우지 않고 비로그인 메인 탭에 둔다`() = runTest {
        val expiry = expiryFlow()
        var profile = eligible
        val auth = FakeAuthRepository(profile = { profile })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, expiry = expiry, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = false) }

            expiry.subscriptionCount.first { it > 0 }
            expiry.expire("s1")
            // 만료가 launchLogin을 켰다면 재확인은 건너뛰고 다음 상태에 launchLogin이 남는다.
            profile = notEligible
            containerHost.onForegrounded()
            expectState { copy(signUp = recheckSignUp) }
            // 만료 구독은 홈에 있는 동안 계속 돈다.
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `홈에 들어가기 전에 세션이 만료되면 홈에서 launchLogin 덮개를 띄운다`() = runTest {
        val expiry = expiryFlow()
        val auth = FakeAuthRepository()
        val isLoggedIn = suspend {
            // 로그인으로 확인한 직후, 홈에 들어가기 전에 만료된다.
            expiry.expire("s1")
            delay(1)
            true
        }
        AppCoordinator(
            FakeOnboardingStore(completed = true),
            isLoggedIn,
            expiry,
            auth,
            { auth.sessionId },
            {},
            emptyFlow(),
            { null },
            {},
            {},
        ).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(launchLogin = true) }
            expectState { copy(routes = listOf(AppRoute.Home)) }
            cancelAndIgnoreRemainingItems()
        }
        // 덮개가 떠 있으므로 프로필 재확인을 하지 않는다.
        assertEquals(0, auth.fetchCount)
    }

    @Test
    fun `시나리오 11 - 초기 프로필 재확인이 401이고 이 세션이 만료됐으면 launchLogin 없이 비로그인 메인 탭에 둔다`() = runTest {
        val auth = FakeAuthRepository()
        auth.profile = {
            // 토큰 갱신 401: 저장소를 지우고 만료 이벤트를 보낸 뒤 실패한다(만료 이벤트 처리는 위 시나리오 11).
            auth.sessionId = null
            auth.expiredSessionId = "s1"
            delay(1)
            throw AuthDomainError.NotAuthenticated()
        }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
        }
        assertEquals(1, auth.fetchCount)
        assertEquals(0, auth.clearCount)
    }

    @Test
    fun `시나리오 1 - 비로그인 launchLogin을 건너뛰면 덮개를 닫고 세션이 없어 프로필을 부르지 않는다`() = runTest {
        val auth = FakeAuthRepository().apply { sessionId = null }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }
            containerHost.onAuthCoverTopChanged(AuthRoute.LaunchLogin)
            expectState { copy(coverTop = AuthRoute.LaunchLogin) }

            containerHost.onLaunchLoginFinished(LaunchLoginResult.Skipped)
            expectState { copy(launchLogin = false, coverTop = null) }
        }
        assertEquals(0, auth.fetchCount)
    }

    @Test
    fun `시나리오 2 · 3 - 로그인 뒤 가입에서 뒤로 나와 건너뛰면 세션을 유지한 채 한 번 재확인해 가입 덮개를 띄우고 닫으면 다음 포그라운드에 한 번 다시 띄운다`() = runTest {
        val auth = FakeAuthRepository(profile = { notEligible }).apply { sessionId = null }
        val launchSignUp = recheckSignUp.copy(sessionId = "s1")
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            // 로그인이 세션 s1을 저장하고 가입 화면으로 갔다가 뒤로 나왔다(세션 유지).
            auth.sessionId = "s1"
            containerHost.onAuthCoverTopChanged(launchSignUp)
            expectState { copy(coverTop = launchSignUp) }
            containerHost.onAuthCoverTopChanged(AuthRoute.LaunchLogin)
            expectState { copy(coverTop = AuthRoute.LaunchLogin) }

            containerHost.onLaunchLoginFinished(LaunchLoginResult.Skipped)
            expectState { copy(launchLogin = false, coverTop = null) }
            expectState { copy(signUp = recheckSignUp) }
            assertEquals(1, auth.fetchCount)

            containerHost.onLaunchLoginFinished(LaunchLoginResult.SignUpCancelled)
            expectState { copy(signUp = null) }
            assertEquals(1, auth.fetchCount)

            containerHost.onForegrounded()
            expectState { copy(signUp = recheckSignUp) }
        }
        assertEquals(2, auth.fetchCount)
        assertEquals(0, auth.clearCount)
        assertEquals("s1", auth.sessionId)
    }

    @Test
    fun `홈에서만 포그라운드마다 만료 팩을 지운다`() = runTest {
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false).test(this) {
            runOnCreate()
            containerHost.onForegrounded()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            containerHost.onForegrounded()
        }
        // 스플래시 중 포그라운드는 지우지 않는다.
        assertEquals(1, packCleanupCount)
    }

    @Test
    fun `시나리오 4 - 로그인 직후 조건을 채워 완료하면 덮개를 닫고 다시 확인하지 않는다`() = runTest {
        val auth = FakeAuthRepository().apply { sessionId = null }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            auth.sessionId = "s1"
            containerHost.onLaunchLoginFinished(completed)
            expectState { copy(launchLogin = false) }
        }
        assertEquals(0, auth.fetchCount)
    }

    @Test
    fun `시나리오 6 - 건너뛰기 재확인은 덮개를 지운 뒤 실행된다`() = runTest {
        val auth = FakeAuthRepository().apply { sessionId = null }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            auth.sessionId = "s1"
            containerHost.onLaunchLoginFinished(LaunchLoginResult.Skipped)
            expectState { copy(launchLogin = false) }
        }
        assertEquals(1, auth.fetchCount)
    }

    @Test
    fun `시나리오 7 - 로그인 직후 401에 이 세션의 만료 이벤트가 있으면 launchLogin을 유지하고 덮개를 새로 만든다`() = runTest {
        val expiry = expiryFlow()
        // 토큰 갱신 401로 저장소는 이미 비었다.
        val auth = FakeAuthRepository().apply { sessionId = null }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, expiry = expiry, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            expiry.subscriptionCount.first { it > 0 }
            // 만료 이벤트는 로그인 화면이 떠 있으므로 상태를 바꾸지 않는다.
            expiry.expire("a")
            containerHost.onLaunchLoginFinished(LaunchLoginResult.SessionLost(sessionId = "a", expired = true))
            expectState { copy(coverEpoch = 1) }
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(0, auth.fetchCount)
    }

    @Test
    fun `시나리오 8 - 로그인 직후 401에 만료 이벤트가 없고 세션이 지워졌으면 덮개를 닫고 재확인하지 않는다`() = runTest {
        val auth = FakeAuthRepository().apply { sessionId = null }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            containerHost.onLaunchLoginFinished(LaunchLoginResult.SessionLost(sessionId = "a", expired = false))
            expectState { copy(launchLogin = false) }
        }
        assertEquals(0, auth.fetchCount)
    }

    @Test
    fun `시나리오 8b - 그 사이 다른 로그인이 세션을 저장했으면 이전 세션의 401 결과를 버린다`() = runTest {
        val auth = FakeAuthRepository().apply { sessionId = null }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            auth.sessionId = "b"
            containerHost.onLaunchLoginFinished(LaunchLoginResult.SessionLost(sessionId = "a", expired = false))
            containerHost.onLaunchLoginFinished(LaunchLoginResult.SessionLost(sessionId = "a", expired = true))
            // 덮개 전이는 세션 b의 결과만 정한다.
            containerHost.onLaunchLoginFinished(completed)
            expectState { copy(launchLogin = false) }
        }
        assertEquals("b", auth.sessionId)
        assertEquals(0, auth.clearCount)
    }

    @Test
    fun `시나리오 8c - 다른 세션의 만료 이벤트는 로그인 직후 만료 이벤트 없는 401 결과를 바꾸지 않는다`() = runTest {
        val expiry = expiryFlow()
        val auth = FakeAuthRepository().apply { sessionId = null }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, expiry = expiry, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            expiry.subscriptionCount.first { it > 0 }
            containerHost.onAuthCoverTopChanged(AuthRoute.LaunchLogin)
            expectState { copy(coverTop = AuthRoute.LaunchLogin) }
            // 세션 a의 만료. 로그인 화면이 떠 있어 덮개 세대를 올리지 않는다(진행 중인 b 로그인을 지우지 않음).
            expiry.expire("a")
            containerHost.onLaunchLoginFinished(LaunchLoginResult.SessionLost(sessionId = "b", expired = false))
            expectState { copy(launchLogin = false, coverTop = null) }
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `시나리오 8d - 세션이 아예 없어졌으면 이전 세션의 401 결과로 덮개를 닫는다`() = runTest {
        val auth = FakeAuthRepository().apply { sessionId = null }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            containerHost.onLaunchLoginFinished(LaunchLoginResult.SessionLost(sessionId = "a", expired = false))
            expectState { copy(launchLogin = false) }
        }
        assertEquals(0, auth.fetchCount)
        assertEquals(0, auth.clearCount)
    }

    @Test
    fun `로그인 상태로 홈에 들어가면 프로필을 확인하고 가입 조건을 채웠으면 그대로 둔다`() = runTest {
        val auth = FakeAuthRepository()
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
        }
        assertEquals(1, auth.fetchCount)
    }

    @Test
    fun `시나리오 5 - 재확인 결과가 가입 조건 미달이면 재동의 가입 덮개를 띄우고 가입을 마치면 다시 확인하지 않고 닫는다`() = runTest {
        val auth = FakeAuthRepository(profile = { notEligible })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
            expectState { copy(signUp = recheckSignUp) }

            containerHost.onLaunchLoginFinished(completed)
            expectState(home)
        }
        assertEquals(1, auth.fetchCount)
    }

    @Test
    fun `시나리오 10 - 재확인 가입 덮개가 떠 있을 때 포그라운드에서 여전히 미달이면 같은 덮개를 두고 충족이면 닫는다`() = runTest {
        // 처음 진입 · 첫 포그라운드는 미달, 두 번째 포그라운드는 충족.
        val results = ArrayDeque(listOf(notEligible, notEligible, eligible))
        val auth = FakeAuthRepository(profile = { results.removeFirst() })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
            expectState { copy(signUp = recheckSignUp) }
            containerHost.onAuthCoverTopChanged(recheckSignUp)
            expectState { copy(coverTop = recheckSignUp) }

            // 같은 덮개(같은 route 객체)를 그대로 둔다. 가입 초안은 덮개 흐름에 남는다.
            // 미달 재확인이 상태를 바꿨다면 다음 기대 상태 앞에 그 상태가 먼저 온다.
            containerHost.onForegrounded()
            containerHost.onForegrounded()
            expectState(home)
        }
        assertEquals(3, auth.fetchCount)
    }

    @Test
    fun `재확인 가입 덮개가 떠 있을 때 약관 버전 · 닉네임이 바뀌면 같은 덮개의 route만 새 프로필로 바꾼다`() = runTest {
        val newer = AuthProfile(user.copy(nickname = "roy2"), "2026-12")
        val results = ArrayDeque(listOf(notEligible, newer))
        val auth = FakeAuthRepository(profile = { results.removeFirst() })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
            expectState { copy(signUp = recheckSignUp) }
            containerHost.onAuthCoverTopChanged(recheckSignUp)
            expectState { copy(coverTop = recheckSignUp) }

            containerHost.onForegrounded()
            expectState { copy(signUp = recheckSignUp.copy(nickname = "roy2", currentTermsVersion = "2026-12")) }
        }
        assertEquals(2, auth.fetchCount)
    }

    @Test
    fun `포그라운드 때 첫 진입 덮개 안의 가입 화면이 보이면 재확인하고 충족이면 덮개를 닫는다`() = runTest {
        val auth = FakeAuthRepository().apply { sessionId = null }
        val launchSignUp = recheckSignUp.copy(sessionId = "s1")
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            // 로그인 화면이 보이는 동안에는 재확인하지 않는다.
            containerHost.onForegrounded()
            assertEquals(0, auth.fetchCount)

            auth.sessionId = "s1"
            containerHost.onAuthCoverTopChanged(launchSignUp)
            expectState { copy(coverTop = launchSignUp) }
            containerHost.onForegrounded()
            expectState { copy(launchLogin = false, coverTop = null) }
        }
        assertEquals(1, auth.fetchCount)
    }

    @Test
    fun `시나리오 9 - 재확인 가입 중 세션이 만료되면 덮개를 닫고 launchLogin을 띄우지 않는다`() = runTest {
        val expiry = expiryFlow()
        val auth = FakeAuthRepository(profile = { notEligible })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, expiry = expiry, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
            expectState { copy(signUp = recheckSignUp) }
            containerHost.onAuthCoverTopChanged(recheckSignUp)
            expectState { copy(coverTop = recheckSignUp) }

            expiry.subscriptionCount.first { it > 0 }
            expiry.expire("s1")
            expectState(home)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `시나리오 9 - 첫 진입 가입 중 그 세션이 만료되면 덮개를 로그인 화면부터 새로 만들고 다른 세션 만료는 무시한다`() = runTest {
        val scope = this
        val expiry = expiryFlow()
        val auth = FakeAuthRepository().apply { sessionId = null }
        val launchSignUp = recheckSignUp.copy(sessionId = "a")
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, expiry = expiry, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }
            containerHost.onAuthCoverTopChanged(launchSignUp)
            expectState { copy(coverTop = launchSignUp) }

            expiry.subscriptionCount.first { it > 0 }
            // 다른 세션 만료는 무시한다. 무시하지 않았다면 다음 상태에 덮개 세대가 올라 있다.
            expiry.expire("b")
            containerHost.onAuthCoverTopChanged(AuthRoute.LaunchLogin)
            expectState { copy(coverTop = AuthRoute.LaunchLogin) }
            containerHost.onAuthCoverTopChanged(launchSignUp)
            expectState { copy(coverTop = launchSignUp) }

            expiry.expire("a")
            expectState { copy(coverEpoch = 1, coverTop = null) }
            // 새 흐름이 로그인 화면을 알린다.
            containerHost.onAuthCoverTopChanged(AuthRoute.LaunchLogin)
            expectState { copy(coverTop = AuthRoute.LaunchLogin) }
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(0, auth.fetchCount)
    }

    @Test
    fun `시나리오 11 - 재확인이 401이고 만료 이벤트가 없으면 세션을 지우고 launchLogin 없이 비로그인 메인 탭에 둔다`() = runTest {
        val auth = FakeAuthRepository(profile = { throw AuthDomainError.NotAuthenticated() })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
        }
        assertEquals(1, auth.clearCount)
        assertEquals(null, auth.sessionId)
    }

    @Test
    fun `재확인이 오프라인이나 서버 오류면 로그인과 화면을 유지하고 포그라운드에서 다시 확인한다`() = runTest {
        var error: Exception = AuthDomainError.Offline()
        val auth = FakeAuthRepository(profile = { throw error })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)

            error = AuthDomainError.Unavailable()
            containerHost.onForegrounded()
        }
        assertEquals(2, auth.fetchCount)
        assertEquals(0, auth.clearCount)
    }

    @Test
    fun `재확인 중 세션이 바뀌면 결과를 버린다`() = runTest {
        val auth = FakeAuthRepository()
        auth.profile = {
            auth.sessionId = "s2"
            notEligible
        }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
        }
        assertEquals(1, auth.fetchCount)
    }

    @Test
    fun `재확인이 진행 중이면 포그라운드에서 겹쳐 부르지 않고 끝난 뒤 한 번만 다시 확인한다`() = runTest {
        val scope = this
        val pending = CompletableDeferred<AuthProfile>()
        val auth = FakeAuthRepository(profile = { pending.await() })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)

            containerHost.onForegrounded()
            containerHost.onForegrounded()
            // 두 포그라운드 요청이 첫 확인이 끝나기 전에 처리되게 한다.
            scope.testScheduler.runCurrent()
            assertEquals(1, auth.fetchCount)
            pending.complete(eligible)
        }
        assertEquals(2, auth.fetchCount)
    }

    @Test
    fun `홈이 아니면 포그라운드 재확인을 하지 않는다`() = runTest {
        val auth = FakeAuthRepository()
        coordinator(FakeOnboardingStore(completed = false), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(OnboardingRoute)) }

            containerHost.onForegrounded()
        }
        assertEquals(0, auth.fetchCount)
    }

    @Test
    fun `현재 사용자 - 진입 재확인이 성공하면 가입 조건과 관계없이 확인한 사용자를 채운다`() = runTest {
        for (profile in listOf(eligible, notEligible)) {
            val users = CurrentUserStore()
            val auth = FakeAuthRepository(profile = { profile })
            coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth, users = users).test(this) {
                runOnCreate()
                containerHost.onSplashAppeared()
                expectState(home)
                if (!profile.isEligible) expectState { copy(signUp = recheckSignUp) }
            }
            assertEquals("u1", users.currentUserId.value)
        }
    }

    @Test
    fun `현재 사용자 - 재확인이 오프라인이면 그대로 두고 그 사이 세션이 바뀐 결과로 채우지 않는다`() = runTest {
        val scope = this
        val users = CurrentUserStore()
        val auth = FakeAuthRepository(profile = { throw AuthDomainError.Offline() })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth, users = users).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
            scope.testScheduler.runCurrent()
            assertEquals(1, auth.fetchCount)

            auth.profile = {
                auth.sessionId = "s2"
                eligible
            }
            containerHost.onForegrounded()
        }
        assertEquals(null, users.currentUserId.value)
    }

    @Test
    fun `현재 사용자 - 로그인 완료는 현재 세션의 결과일 때만 그 사용자를 채운다`() = runTest {
        val scope = this
        val users = CurrentUserStore()
        val auth = FakeAuthRepository().apply { sessionId = null }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth, users = users).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            // 다른 세션(a)의 완료는 버린다.
            auth.sessionId = "s1"
            containerHost.onLaunchLoginFinished(LaunchLoginResult.Completed(userId = "u-old", sessionId = "a"))
            expectState { copy(launchLogin = false) }
            assertEquals(null, users.currentUserId.value)

            containerHost.onLaunchLoginFinished(completed)
            scope.testScheduler.runCurrent()
        }
        assertEquals("u1", users.currentUserId.value)
    }

    @Test
    fun `현재 사용자 - 현재 세션이 없으면 로그인 완료도 사용자를 채우지 않는다`() = runTest {
        val scope = this
        val users = CurrentUserStore()
        val auth = FakeAuthRepository().apply { sessionId = null }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth, users = users).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            containerHost.onLaunchLoginFinished(completed)
            expectState { copy(launchLogin = false) }
            scope.testScheduler.runCurrent()
        }
        assertEquals(null, users.currentUserId.value)
    }

    @Test
    fun `현재 사용자 - 재확인 가입 덮개에서 가입을 마치면 가입한 사용자를 둔다`() = runTest {
        val users = CurrentUserStore()
        val auth = FakeAuthRepository(profile = { notEligible })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth, users = users).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
            expectState { copy(signUp = recheckSignUp) }
            users.update(null)

            containerHost.onLaunchLoginFinished(completed)
            expectState(home)
        }
        assertEquals("u1", users.currentUserId.value)
    }

    @Test
    fun `현재 사용자 - 세션 만료는 비우고 다른 세션의 만료는 무시한다`() = runTest {
        val scope = this
        val users = CurrentUserStore()
        val expiry = expiryFlow()
        val auth = FakeAuthRepository()
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, expiry = expiry, auth = auth, users = users).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
            scope.testScheduler.runCurrent()
            assertEquals("u1", users.currentUserId.value)

            expiry.subscriptionCount.first { it > 0 }
            expiry.expire("old")
            scope.testScheduler.runCurrent()
            assertEquals("u1", users.currentUserId.value)

            // 토큰 갱신 401로 저장소가 비고 만료 이벤트가 온다.
            auth.sessionId = null
            expiry.expire("s1")
            scope.testScheduler.runCurrent()
            assertEquals(null, users.currentUserId.value)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `현재 사용자 - 재확인 401이면 만료 이벤트 유무와 관계없이 비운다`() = runTest {
        for (expired in listOf(false, true)) {
            val users = CurrentUserStore().apply { update("u1") }
            val auth = FakeAuthRepository()
            auth.profile = {
                if (expired) {
                    auth.sessionId = null
                    auth.expiredSessionId = "s1"
                }
                throw AuthDomainError.NotAuthenticated()
            }
            coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth, users = users).test(this) {
                runOnCreate()
                containerHost.onSplashAppeared()
                expectState(home)
            }
            assertEquals(null, users.currentUserId.value)
        }
    }

    @Test
    fun `현재 사용자 - 로그인 직후 401 두 갈래는 비우고 오래된 세션의 401은 무시한다`() = runTest {
        val scope = this
        for (expired in listOf(false, true)) {
            val users = CurrentUserStore().apply { update("u1") }
            val auth = FakeAuthRepository().apply { sessionId = null }
            coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth, users = users).test(this) {
                runOnCreate()
                containerHost.onSplashAppeared()
                expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

                // 그 사이 다른 로그인(b)이 세션을 저장했으면 a의 결과는 버린다.
                auth.sessionId = "b"
                containerHost.onLaunchLoginFinished(LaunchLoginResult.SessionLost(sessionId = "a", expired = expired))
                scope.testScheduler.runCurrent()
                assertEquals("u1", users.currentUserId.value)

                auth.sessionId = null
                containerHost.onLaunchLoginFinished(LaunchLoginResult.SessionLost(sessionId = "a", expired = expired))
                if (expired) expectState { copy(coverEpoch = 1) } else expectState { copy(launchLogin = false) }
            }
            assertEquals(null, users.currentUserId.value)
        }
    }

    private companion object {
        val user =
            AuthUser(
                id = "u1",
                provider = "google",
                email = null,
                nickname = "roy",
                nicknameConfirmed = true,
                termsAcceptedAt = "2026-10-01T00:00:00Z",
                termsVersion = "2026-10",
                pushOptIn = true,
                profileImageUrl = null,
            )
        val eligible = AuthProfile(user, "2026-10")

        /** 약관 버전이 올라 다시 동의해야 한다. */
        val notEligible = AuthProfile(user, "2026-11")

        /** [notEligible] 재확인으로 뜨는 가입 덮개. 예전 약관에 동의한 회원이라 재동의다. */
        val recheckSignUp =
            AuthRoute.SignUp(userId = "u1", nickname = "roy", currentTermsVersion = "2026-11", sessionId = "s1", isReconsent = true)

        /** 로그인 시트로 로그인한 신규 계정(약관 미동의)의 가입 정보. */
        val newAccountSignUp =
            AuthRoute.SignUp(userId = "u1", nickname = null, currentTermsVersion = "2026-11", sessionId = "s1")

        /** 세션 s1에서 사용자 u1로 로그인(또는 가입)을 마쳤다. */
        val completed = LaunchLoginResult.Completed(userId = "u1", sessionId = "s1")
    }

    @Test
    fun `홈 전에 받은 딥링크는 대기했다가 홈에 들어가면 연다`() = runTest {
        val deeplinks = FakeDeeplinks()
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, deeplinks = deeplinks).test(this) {
            runOnCreate()
            deeplinks.receive(FestiCoreDeeplink.Festival("fst_a"))
            containerHost.onSplashAppeared()

            expectState { copy(routes = listOf(AppRoute.Home)) }
            expectSideEffect(AppCoordinatorSideEffect.OpenDeeplink(FestiCoreDeeplink.Festival("fst_a")))
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `첫 진입 덮개가 있으면 대기했다가 덮개가 닫히면 연다`() = runTest {
        val deeplinks = FakeDeeplinks()
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false, deeplinks = deeplinks).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            deeplinks.receive(FestiCoreDeeplink.FestivalList)
            containerHost.onLaunchLoginFinished(LaunchLoginResult.Skipped)

            expectState { copy(launchLogin = false) }
            expectSideEffect(AppCoordinatorSideEffect.OpenDeeplink(FestiCoreDeeplink.FestivalList))
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(null, deeplinks.pending)
    }

    @Test
    fun `덮개 없는 홈에서 받은 딥링크는 바로 연다`() = runTest {
        val deeplinks = FakeDeeplinks()
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, deeplinks = deeplinks).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home)) }

            deeplinks.receive(FestiCoreDeeplink.Festival("fst_b"))

            expectSideEffect(AppCoordinatorSideEffect.OpenDeeplink(FestiCoreDeeplink.Festival("fst_b")))
            cancelAndIgnoreRemainingItems()
        }
    }

    // 로그인 시트(iOS `AuthCoordinator.loginRequested` · `dismissFlow` · `acceptLogin`, `AppCoordinatorTests` 로그인 시트 흐름)

    /** 비로그인 홈에서 시작한다. 시트 결과만 보므로 생성 시 구독(만료 · 딥링크)은 돌리지 않는다. */
    private fun signedOutHome(
        auth: FakeAuthRepository = FakeAuthRepository().apply { sessionId = null },
        users: CurrentUserStore = CurrentUserStore(),
    ) = coordinator(FakeOnboardingStore(completed = true), loggedIn = false, auth = auth, users = users)

    @Test
    fun `로그인 시트 - 홈에서 요청하면 문맥으로 시트를 열고 떠 있으면 문맥만 바꾼다`() = runTest {
        signedOutHome().test(this, home) {
            containerHost.onLoginRequested(LoginContext.Crew)
            expectState { copy(loginSheet = LoginContext.Crew, loginSheetId = 1) }

            containerHost.onLoginRequested(LoginContext.CrewJoin("우리 크루"))
            expectState { copy(loginSheet = LoginContext.CrewJoin("우리 크루")) }
        }
    }

    /** iOS `FlagRoutingTests.detailRequestsReachAppSheets`의 약관 요청 → `session.retryProfile`. 홈 전이면 재확인하지 않는다. */
    @Test
    fun `깃발 약관 재동의 요청은 홈에서 프로필을 다시 확인해 가입 정보 덮개를 띄운다`() = runTest {
        val auth = FakeAuthRepository(profile = { notEligible })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this, AppCoordinatorState()) {
            containerHost.onTermsAcceptanceRequested()
        }
        assertEquals(0, auth.fetchCount)

        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this, home) {
            containerHost.onTermsAcceptanceRequested()
            expectState { copy(signUp = recheckSignUp) }
        }
        assertEquals(1, auth.fetchCount)
    }

    @Test
    fun `로그인 시트 - 덮개가 있거나 홈 전이면 열지 않는다`() = runTest {
        signedOutHome().test(this, home.copy(launchLogin = true)) {
            containerHost.onLoginRequested(LoginContext.Follow)
        }
        signedOutHome().test(this, AppCoordinatorState()) {
            containerHost.onLoginRequested(LoginContext.Follow)
        }
    }

    @Test
    fun `로그인 시트 - 닫으면 세션 재확인 없이 시트만 닫고 다시 열면 새 시트로 시작한다`() = runTest {
        val auth = FakeAuthRepository()
        signedOutHome(auth = auth).test(this, home) {
            containerHost.onLoginRequested(LoginContext.Report)
            expectState { copy(loginSheet = LoginContext.Report, loginSheetId = 1) }

            containerHost.onLoginSheetDismissed()
            expectState { copy(loginSheet = null) }

            containerHost.onLoginRequested(LoginContext.Report)
            expectState { copy(loginSheet = LoginContext.Report, loginSheetId = 2) }
        }
        assertEquals(0, auth.fetchCount)
        assertEquals(0, auth.clearCount)
    }

    @Test
    fun `로그인 시트 - 로그인 완료면 현재 세션의 사용자를 두고 시트를 닫는다`() = runTest {
        val users = CurrentUserStore()
        signedOutHome(auth = FakeAuthRepository(), users = users).test(this, home) {
            containerHost.onLoginRequested(LoginContext.Follow)
            expectState { copy(loginSheet = LoginContext.Follow, loginSheetId = 1) }

            containerHost.onLoginSheetFinished(completed)
            expectState { copy(loginSheet = null) }
        }
        assertEquals("u1", users.currentUserId.value)
    }

    @Test
    fun `로그인 시트 - 다른 세션의 완료는 사용자를 두지 않고 시트만 닫는다`() = runTest {
        val users = CurrentUserStore()
        signedOutHome(auth = FakeAuthRepository().apply { sessionId = "s2" }, users = users).test(this, home) {
            containerHost.onLoginRequested(LoginContext.Flag)
            expectState { copy(loginSheet = LoginContext.Flag, loginSheetId = 1) }

            containerHost.onLoginSheetFinished(completed)
            expectState { copy(loginSheet = null) }
        }
        assertEquals(null, users.currentUserId.value)
    }

    @Test
    fun `로그인 시트 - 계정 확인 401이면 사용자를 비우고 시트를 닫으며 첫 진입 덮개는 띄우지 않는다`() = runTest {
        val users = CurrentUserStore().apply { update("u-old") }
        for (expired in listOf(false, true)) {
            signedOutHome(users = users).test(this, home) {
                containerHost.onLoginRequested(LoginContext.Crew)
                expectState { copy(loginSheet = LoginContext.Crew, loginSheetId = 1) }

                containerHost.onLoginSheetFinished(LaunchLoginResult.SessionLost(sessionId = "s1", expired = expired))
                expectState { copy(loginSheet = null) }
            }
            assertEquals(null, users.currentUserId.value)
        }
    }

    @Test
    fun `로그인 시트 - 신규 계정이면 시트를 닫고 가입 정보 덮개로 이어진다`() = runTest {
        val users = CurrentUserStore()
        signedOutHome(auth = FakeAuthRepository(), users = users).test(this, home) {
            containerHost.onLoginRequested(LoginContext.Crew)
            expectState { copy(loginSheet = LoginContext.Crew, loginSheetId = 1) }

            containerHost.onLoginSheetSignUpRequired(newAccountSignUp)
            expectState { copy(loginSheet = null, signUp = newAccountSignUp) }

            // 가입을 마치면 가입 덮개를 닫는다.
            containerHost.onLaunchLoginFinished(completed)
            expectState { copy(signUp = null) }
        }
        assertEquals("u1", users.currentUserId.value)
    }

    @Test
    fun `로그인 시트 - 가입 정보를 뒤로 닫으면 세션을 지우지 않고 덮개만 닫는다`() = runTest {
        val auth = FakeAuthRepository()
        signedOutHome(auth = auth).test(this, home) {
            containerHost.onLoginRequested(LoginContext.Follow)
            expectState { copy(loginSheet = LoginContext.Follow, loginSheetId = 1) }

            containerHost.onLoginSheetSignUpRequired(newAccountSignUp)
            expectState { copy(loginSheet = null, signUp = newAccountSignUp) }

            containerHost.onLaunchLoginFinished(LaunchLoginResult.SignUpCancelled)
            expectState { copy(signUp = null) }
        }
        assertEquals("s1", auth.sessionId)
        assertEquals(0, auth.fetchCount)
    }

    @Test
    fun `로그인 시트 - 그 사이 세션이 바뀐 가입 요청은 가입 덮개를 띄우지 않고 시트만 닫는다`() = runTest {
        val users = CurrentUserStore()
        signedOutHome(auth = FakeAuthRepository().apply { sessionId = "s2" }, users = users).test(this, home) {
            containerHost.onLoginRequested(LoginContext.Crew)
            expectState { copy(loginSheet = LoginContext.Crew, loginSheetId = 1) }

            containerHost.onLoginSheetSignUpRequired(newAccountSignUp)
            expectState { copy(loginSheet = null) }
        }
        assertEquals(null, users.currentUserId.value)
    }
}
