package io.festicore.app.navigation

import io.festicore.core.datastore.FirstInstallOnboardingStore
import io.festicore.domain.auth.AuthDomainError
import io.festicore.domain.auth.AuthLoginResult
import io.festicore.domain.auth.AuthProfile
import io.festicore.domain.auth.AuthRepository
import io.festicore.domain.auth.AuthUser
import io.festicore.feature.auth.AuthRoute
import io.festicore.feature.auth.LaunchLoginResult
import io.festicore.feature.onboarding.OnboardingRoute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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

        override suspend fun isLoggedIn(): Boolean = sessionId != null

        override suspend fun clearSession() {
            clearCount++
            sessionId = null
        }
    }

    private fun coordinator(
        store: FirstInstallOnboardingStore,
        loggedIn: Boolean,
        expiry: Flow<Long> = emptyFlow(),
        auth: FakeAuthRepository = FakeAuthRepository().apply { sessionId = null },
    ) = AppCoordinator(store, { loggedIn }, expiry, auth, { auth.sessionId })

    private val home = AppCoordinatorState(routes = listOf(AppRoute.Home))

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
    fun `홈에서 세션 만료 이벤트를 받으면 launchLogin 덮개를 띄운다`() = runTest {
        val expiry = MutableStateFlow(0L)
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, expiry = expiry).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = false) }

            expiry.subscriptionCount.first { it > 0 }
            expiry.value = 1
            expectState { copy(launchLogin = true) }
            // 만료 구독은 홈에 있는 동안 계속 돈다.
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `홈에 들어가기 전에 세션이 만료되면 홈에서 launchLogin 덮개를 띄운다`() = runTest {
        val expiry = MutableStateFlow(0L)
        val auth = FakeAuthRepository()
        val isLoggedIn = suspend {
            // 로그인으로 확인한 직후, 홈에 들어가기 전에 만료된다.
            expiry.value = 1
            delay(1)
            true
        }
        AppCoordinator(FakeOnboardingStore(completed = true), isLoggedIn, expiry, auth, { auth.sessionId }).test(this) {
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
    fun `초기 프로필 재확인 중 세션이 만료되면 launchLogin 덮개를 띄우고 같은 만료는 한 번만 반영한다`() = runTest {
        // 구독 전에 쌓인 값은 이미 반영된 것으로 본다.
        val expiry = MutableStateFlow(3L)
        val auth = FakeAuthRepository()
        auth.profile = {
            expiry.value = 4
            delay(1)
            eligible
        }
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, expiry = expiry, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
            expectState { copy(launchLogin = true) }

            containerHost.onLaunchLoginFinished(LaunchLoginResult.Skipped)
            expectState { copy(launchLogin = false) }
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(1, auth.fetchCount)
    }

    @Test
    fun `첫 진입 덮개를 마치거나 건너뛰면 덮개를 닫는다`() = runTest {
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }

            containerHost.onLaunchLoginFinished(LaunchLoginResult.Skipped)
            expectState { copy(launchLogin = false) }
        }
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
    fun `재확인 결과가 가입 조건 미달이면 가입 덮개를 띄우고 마치면 닫는다`() = runTest {
        val auth = FakeAuthRepository(profile = { notEligible })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
            expectState {
                copy(signUp = AuthRoute.SignUp(userId = "u1", nickname = "roy", currentTermsVersion = "2026-11", sessionId = "s1"))
            }

            // 덮개가 떠 있으면 포그라운드 재확인을 하지 않는다.
            containerHost.onForegrounded()
            containerHost.onLaunchLoginFinished(LaunchLoginResult.Completed)
            expectState(home)
        }
        assertEquals(1, auth.fetchCount)
    }

    @Test
    fun `재확인이 401이면 세션을 지우고 launchLogin 덮개를 띄운다`() = runTest {
        val auth = FakeAuthRepository(profile = { throw AuthDomainError.NotAuthenticated() })
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, auth = auth).test(this) {
            runOnCreate()
            containerHost.onSplashAppeared()
            expectState(home)
            expectState { copy(launchLogin = true) }
        }
        assertEquals(1, auth.clearCount)
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
    }
}
