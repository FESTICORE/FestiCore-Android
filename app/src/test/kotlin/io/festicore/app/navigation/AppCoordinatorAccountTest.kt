package io.festicore.app.navigation

import io.festicore.core.storage.onboarding.FirstInstallOnboardingStore
import io.festicore.domain.auth.auth.AuthRepository
import io.festicore.domain.auth.entity.AuthDomainError
import io.festicore.domain.auth.entity.AuthLoginResult
import io.festicore.domain.auth.entity.AuthProfile
import io.festicore.domain.auth.entity.AuthUser
import io.festicore.domain.auth.sources.CurrentUserStore
import io.festicore.feature.auth.AuthRoute
import io.festicore.feature.auth.LaunchLoginResult
import io.festicore.feature.auth.LoginContext
import io.festicore.feature.profile.AccountState
import io.festicore.service.auth.expiry.SessionExpiry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** 계정 상태 공급 · 로그아웃 · 탈퇴 · 바로 로그인. iOS `AppCoordinatorTests` 계정 사례(설계 PR 묶음 A1). 서버 · Google 로그인은 가짜다. */
class AppCoordinatorAccountTest {
    private object CompletedOnboarding : FirstInstallOnboardingStore {
        override suspend fun hasCompleted(): Boolean = true

        override suspend fun markCompleted() = Unit
    }

    /** 로그아웃 · 탈퇴 결과를 정할 수 있는 가짜 저장소. [calls]에 부른 순서를 남긴다. */
    private class FakeAuth : AuthRepository {
        var sessionId: String? = "s1"
        var profile: AuthProfile = eligible
        val calls = mutableListOf<String>()

        /** 기본: 서버 성공 → 요청한 세션을 지운다. */
        var signOutResult: suspend () -> Unit = { sessionId = null }
        var withdrawResult: suspend () -> Unit = { sessionId = null }

        override val sessionExpired: Flow<Unit> = emptyFlow()

        override fun hasSessionExpired(sessionId: String): Boolean = false

        override suspend fun login(idToken: String): AuthLoginResult = throw AuthDomainError.Unavailable()

        override suspend fun fetchProfile(expectedSessionId: String?): AuthProfile = profile

        override suspend fun signUp(
            nickname: String,
            termsVersion: String,
            expectedUserId: String,
            expectedSessionId: String?,
        ): AuthUser = throw AuthDomainError.Unavailable()

        override suspend fun updateNickname(nickname: String): AuthUser = throw AuthDomainError.Unavailable()

        override suspend fun signOut() {
            calls += "signOut"
            signOutResult()
        }

        override suspend fun withdraw() {
            calls += "withdraw"
            withdrawResult()
        }

        var isLoggedInError: Exception? = null

        override suspend fun isLoggedIn(): Boolean {
            isLoggedInError?.let { throw it }
            return sessionId != null
        }

        override suspend fun clearSession() {
            sessionId = null
        }

        override suspend fun clearSessionIf(sessionId: String): Boolean {
            if (this.sessionId != sessionId) return false
            this.sessionId = null
            return true
        }
    }

    private fun coordinator(
        auth: FakeAuth,
        users: CurrentUserStore = CurrentUserStore(),
        expiry: Flow<SessionExpiry> = emptyFlow(),
        failUnbind: Boolean = false,
    ) = AppCoordinator(
        CompletedOnboarding,
        { auth.sessionId != null },
        expiry,
        auth,
        { auth.sessionId },
        users::update,
        emptyFlow(),
        { null },
        {},
        AccountCoordinator(
            authRepository = auth,
            unbindDevice = {
                auth.calls += "unbind"
                if (failUnbind) throw IllegalStateException("unbind")
            },
            linkDevice = { auth.calls += "link" },
        ),
    )

    private val home = AppCoordinatorState(routes = listOf(AppRoute.Home))

    private val signedIn = AccountState(profile = eligible, hasSession = true)

    private fun expiryFlow() = MutableStateFlow(SessionExpiry(generation = 0, sessionId = null))

    private fun MutableStateFlow<SessionExpiry>.expire(sessionId: String) {
        value = SessionExpiry(generation = value.generation + 1, sessionId = sessionId)
    }

    private fun TestScope.settle() = testScheduler.runCurrent()

    @Test
    fun `로그아웃은 성공한 뒤에만 보이는 계정을 비운다`() = runTest {
        val scope = this
        val users = CurrentUserStore()
        val auth = FakeAuth()
        val pending = CompletableDeferred<Unit>()
        auth.signOutResult = {
            pending.await()
            auth.sessionId = null
        }
        coordinator(auth, users).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()
            assertEquals(signedIn, containerHost.account.value)
            assertEquals("u1", users.currentUserId.value)

            containerHost.onLogoutRequested()
            scope.settle()
            // 요청 중에는 계정을 그대로 두고 처리 중만 표시한다.
            assertEquals(signedIn.copy(isLoading = true), containerHost.account.value)

            pending.complete(Unit)
            scope.settle()
            assertEquals(AccountState(), containerHost.account.value)
        }
        assertNull(users.currentUserId.value)
    }

    @Test
    fun `기기 연결 해제가 실패해도 로그아웃을 계속한다`() = runTest {
        val scope = this
        val auth = FakeAuth()
        coordinator(auth, failUnbind = true).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()
            containerHost.onLogoutRequested()
            scope.settle()
            assertFalse(containerHost.account.value.hasSession)
        }
        assertEquals(listOf("unbind", "signOut"), auth.calls)
    }

    @Test
    fun `기기 연결 해제가 실패해도 탈퇴를 계속한다`() = runTest {
        val scope = this
        val auth = FakeAuth()
        coordinator(auth, failUnbind = true).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()
            containerHost.onWithdrawRequested()
            scope.settle()
            assertFalse(containerHost.account.value.hasSession)
        }
        assertEquals(listOf("unbind", "withdraw"), auth.calls)
    }

    @Test
    fun `탈퇴가 실패하고 로그인이 남아 있으면 계정을 두고 기기 연결을 복원하며 실패 문구를 보인다`() = runTest {
        val scope = this
        val users = CurrentUserStore()
        val auth = FakeAuth().apply { withdrawResult = { throw AuthDomainError.Offline() } }
        coordinator(auth, users).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()
            containerHost.onWithdrawRequested()
            scope.settle()
            assertEquals(signedIn.copy(message = "회원 탈퇴에 실패했습니다. 다시 시도해 주세요."), containerHost.account.value)
        }
        assertEquals(listOf("unbind", "withdraw", "link"), auth.calls)
        assertEquals("u1", users.currentUserId.value)
        assertEquals("s1", auth.sessionId)
    }

    @Test
    fun `로그아웃이 실패하면 로그아웃 실패 문구를 보이고 다시 시도할 수 있다`() = runTest {
        val scope = this
        val auth = FakeAuth().apply { signOutResult = { throw AuthDomainError.Unavailable() } }
        coordinator(auth).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()
            containerHost.onLogoutRequested()
            scope.settle()
            assertEquals(signedIn.copy(message = "로그아웃에 실패했습니다. 다시 시도해 주세요."), containerHost.account.value)

            auth.signOutResult = { auth.sessionId = null }
            containerHost.onLogoutRequested()
            scope.settle()
            assertEquals(AccountState(), containerHost.account.value)
        }
        assertEquals(listOf("unbind", "signOut", "link", "unbind", "signOut"), auth.calls)
    }

    @Test
    fun `탈퇴 401이면 계정을 비우고 결과를 확인할 수 없다는 문구를 보인다`() = runTest {
        val scope = this
        val users = CurrentUserStore()
        val auth = FakeAuth()
        auth.withdrawResult = {
            auth.sessionId = null
            throw AuthDomainError.NotAuthenticated()
        }
        coordinator(auth, users).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()
            containerHost.onWithdrawRequested()
            scope.settle()
            assertEquals(AccountState(message = AccountCoordinator.SESSION_EXPIRED_DURING_WITHDRAW), containerHost.account.value)
        }
        assertNull(users.currentUserId.value)
        // 로그인을 잃었으면 기기 연결을 복원하지 않는다.
        assertEquals(listOf("unbind", "withdraw"), auth.calls)
    }

    @Test
    fun `세션이 만료되면 진행 중인 탈퇴를 취소하고 결과 불명 문구로 계정을 비운다`() = runTest {
        val scope = this
        val expiry = expiryFlow()
        val auth = FakeAuth()
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        auth.withdrawResult = {
            started.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        coordinator(auth, expiry = expiry).test(this, home) {
            runOnCreate()
            containerHost.onProfileRefreshRequested()
            scope.settle()
            containerHost.onWithdrawRequested()
            scope.settle()
            assertTrue(started.isCompleted)

            auth.sessionId = null
            expiry.expire("s1")
            scope.settle()
            assertEquals(AccountState(message = AccountCoordinator.SESSION_EXPIRED_DURING_WITHDRAW), containerHost.account.value)
            cancelAndIgnoreRemainingItems()
        }
        assertTrue(cancelled)
        assertEquals(listOf("unbind", "withdraw"), auth.calls)
    }

    @Test
    fun `작업 중 만료 뒤 새로 로그인하면 늦게 끝난 이전 작업은 새 계정을 비우지 않는다`() = runTest {
        val scope = this
        val expiry = expiryFlow()
        val users = CurrentUserStore()
        val auth = FakeAuth()
        val pending = CompletableDeferred<Unit>()
        auth.withdrawResult = { pending.await() }
        coordinator(auth, users, expiry).test(this, home) {
            runOnCreate()
            containerHost.onProfileRefreshRequested()
            scope.settle()
            containerHost.onWithdrawRequested()
            scope.settle()

            auth.sessionId = null
            expiry.expire("s1")
            scope.settle()
            assertFalse(containerHost.account.value.hasSession)

            // 다른 세션으로 다시 로그인한다.
            auth.sessionId = "s2"
            containerHost.onLoginRequested(LoginContext.Profile)
            expectState { copy(loginSheet = LoginContext.Profile, loginSheetId = 1) }
            containerHost.onLoginSheetFinished(LaunchLoginResult.Completed(userId = "u1", sessionId = "s2", profile = eligible))
            expectState { copy(loginSheet = null) }
            scope.settle()

            pending.complete(Unit)
            scope.settle()
            assertTrue(containerHost.account.value.hasSession)
            assertEquals(eligible, containerHost.account.value.profile)
            cancelAndIgnoreRemainingItems()
        }
        assertEquals("u1", users.currentUserId.value)
        assertEquals("s2", auth.sessionId)
    }

    @Test
    fun `만료 뒤 늦게 실패한 탈퇴는 이전 계정을 되살리지 않는다`() = runTest {
        val scope = this
        val expiry = expiryFlow()
        val auth = FakeAuth()
        val pending = CompletableDeferred<Unit>()
        auth.withdrawResult = { pending.await() }
        coordinator(auth, expiry = expiry).test(this, home) {
            runOnCreate()
            containerHost.onProfileRefreshRequested()
            scope.settle()
            containerHost.onWithdrawRequested()
            scope.settle()

            auth.sessionId = null
            expiry.expire("s1")
            scope.settle()
            pending.completeExceptionally(AuthDomainError.Offline())
            scope.settle()
            assertEquals(AccountState(message = AccountCoordinator.SESSION_EXPIRED_DURING_WITHDRAW), containerHost.account.value)
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(listOf("unbind", "withdraw"), auth.calls)
    }

    @Test
    fun `가입 정보 덮개를 닫아도 진행 중인 탈퇴 결과는 반영된다`() = runTest {
        val scope = this
        val auth = FakeAuth()
        val pending = CompletableDeferred<Unit>()
        auth.withdrawResult = {
            pending.await()
            auth.sessionId = null
        }
        val signUp = AuthRoute.SignUp(userId = "u1", nickname = "roy", currentTermsVersion = "2026-10", sessionId = "s1")
        coordinator(auth).test(this, home.copy(signUp = signUp)) {
            containerHost.onProfileRefreshRequested()
            // 조건을 채운 계정이라 재확인이 가입 덮개를 닫는다.
            expectState(home)
            scope.settle()
            containerHost.onLoginSheetSignUpRequired(signUp)
            expectState { copy(signUp = signUp) }

            containerHost.onWithdrawRequested()
            scope.settle()
            containerHost.onLaunchLoginFinished(LaunchLoginResult.SignUpCancelled)
            expectState(home)

            pending.complete(Unit)
            scope.settle()
            assertEquals(AccountState(), containerHost.account.value)
        }
    }

    @Test
    fun `탈퇴가 끝날 때 가입 정보 덮개가 떠 있으면 닫는다`() = runTest {
        val scope = this
        val auth = FakeAuth()
        val pending = CompletableDeferred<Unit>()
        auth.withdrawResult = {
            pending.await()
            auth.sessionId = null
        }
        val signUp = AuthRoute.SignUp(userId = "u1", nickname = "roy", currentTermsVersion = "2026-10", sessionId = "s1")
        coordinator(auth).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()
            containerHost.onWithdrawRequested()
            scope.settle()
            containerHost.onLoginSheetSignUpRequired(signUp)
            expectState { copy(signUp = signUp) }

            pending.complete(Unit)
            expectState(home)
            assertEquals(AccountState(), containerHost.account.value)
        }
    }

    @Test
    fun `로그인 상태를 읽지 못하면 iOS와 같이 로그인을 잃은 것으로 보고 계정을 비운다`() = runTest {
        val scope = this
        val auth = FakeAuth().apply { withdrawResult = { throw AuthDomainError.Offline() } }
        coordinator(auth).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()
            auth.isLoggedInError = IllegalStateException("read")
            containerHost.onWithdrawRequested()
            scope.settle()
            assertEquals(AccountState(message = AccountCoordinator.SESSION_EXPIRED_DURING_WITHDRAW), containerHost.account.value)
        }
        assertEquals(listOf("unbind", "withdraw"), auth.calls)
    }

    @Test
    fun `작업 중 만료 알림 전에 다른 세션으로 바뀌었으면 끝난 결과로 새 계정을 비우지 않는다`() = runTest {
        val scope = this
        val users = CurrentUserStore()
        val auth = FakeAuth()
        val pending = CompletableDeferred<Unit>()
        auth.signOutResult = { pending.await() }
        coordinator(auth, users).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()
            containerHost.onLogoutRequested()
            scope.settle()

            // 로그아웃 응답 전에 다른 세션이 저장됐다(만료 알림은 아직 오지 않음).
            auth.sessionId = "s2"
            pending.complete(Unit)
            scope.settle()
            assertEquals(signedIn, containerHost.account.value)
        }
        assertEquals("u1", users.currentUserId.value)
    }

    @Test
    fun `시트 로그인 계정이 가입 정보로 가면 다른 사용자의 이전 프로필을 남기지 않는다`() = runTest {
        val scope = this
        val signUp = AuthRoute.SignUp(userId = "u2", nickname = null, currentTermsVersion = "2026-10", sessionId = "s1")
        coordinator(FakeAuth()).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()
            assertEquals(signedIn, containerHost.account.value)

            containerHost.onLoginSheetSignUpRequired(signUp)
            expectState { copy(signUp = signUp) }
            assertEquals(AccountState(hasSession = true), containerHost.account.value)
        }
    }

    @Test
    fun `바꾼 닉네임은 같은 사용자일 때만 계정 상태에 반영한다`() = runTest {
        val scope = this
        coordinator(FakeAuth()).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()

            containerHost.onNicknameUpdated(user.copy(id = "u2", nickname = "다른계정"))
            assertEquals(signedIn, containerHost.account.value)

            val renamed = user.copy(nickname = "새이름")
            containerHost.onNicknameUpdated(renamed)
            assertEquals(signedIn.copy(profile = eligible.copy(user = renamed)), containerHost.account.value)
        }
    }

    @Test
    fun `계정 변화는 마이 탭 입력 StateFlow로 전해진다`() = runTest {
        val scope = this
        val expiry = expiryFlow()
        val auth = FakeAuth()
        coordinator(auth, expiry = expiry).test(this, AppCoordinatorState()) {
            runOnCreate()
            assertEquals(AccountState(), containerHost.account.value)

            containerHost.onSplashAppeared()
            expectState(home)
            scope.settle()
            assertEquals(signedIn, containerHost.account.value)

            auth.sessionId = null
            expiry.expire("s1")
            scope.settle()
            assertEquals(AccountState(), containerHost.account.value)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `로그아웃은 이전 계정의 현재 사용자를 바로 비운다`() = runTest {
        val scope = this
        val users = CurrentUserStore()
        val auth = FakeAuth()
        coordinator(auth, users).test(this, home) {
            containerHost.onProfileRefreshRequested()
            scope.settle()
            assertEquals("u1", users.currentUserId.value)

            containerHost.onLogoutRequested()
            scope.settle()
            assertNull(users.currentUserId.value)
            assertNull(containerHost.account.value.profile)
        }
    }

    @Test
    fun `세션이 없거나 처리 중이면 계정 작업을 시작하지 않는다`() = runTest {
        val scope = this
        val auth = FakeAuth().apply { sessionId = null }
        coordinator(auth).test(this, home) {
            containerHost.onLogoutRequested()
            containerHost.onWithdrawRequested()
            scope.settle()

            auth.sessionId = "s1"
            val pending = CompletableDeferred<Unit>()
            auth.signOutResult = { pending.await() }
            containerHost.onProfileRefreshRequested()
            scope.settle()
            containerHost.onLogoutRequested()
            containerHost.onWithdrawRequested()
            scope.settle()
            pending.complete(Unit)
            scope.settle()
        }
        assertEquals(listOf("unbind", "signOut"), auth.calls)
    }

    @Test
    fun `Google 바로 로그인 - 시트 없이 새 로그인 상태로 시작하고 취소면 조용히 끝낸다`() = runTest {
        coordinator(FakeAuth().apply { sessionId = null }).test(this, home) {
            containerHost.onGoogleLoginRequested()
            expectState { copy(directLogin = true, loginSheetId = 1) }
            // 진행 중이면 다시 시작하지 않는다.
            containerHost.onGoogleLoginRequested()

            containerHost.onDirectLoginFailed(showsMessage = false)
            expectState { copy(directLogin = false) }
            // 바로 로그인 중이 아니면 실패 알림을 무시한다.
            containerHost.onDirectLoginFailed(showsMessage = true)
        }
    }

    @Test
    fun `Google 바로 로그인 - 실패 문구가 있으면 같은 로그인 상태로 마이 문맥 시트를 띄운다`() = runTest {
        coordinator(FakeAuth().apply { sessionId = null }).test(this, home) {
            containerHost.onGoogleLoginRequested()
            expectState { copy(directLogin = true, loginSheetId = 1) }

            containerHost.onDirectLoginFailed(showsMessage = true)
            expectState { copy(directLogin = false, loginSheet = LoginContext.Profile) }
        }
    }

    @Test
    fun `Google 바로 로그인 - 완료하면 현재 사용자와 계정 상태를 채운다`() = runTest {
        val scope = this
        val users = CurrentUserStore()
        val auth = FakeAuth().apply { sessionId = null }
        coordinator(auth, users).test(this, home) {
            containerHost.onGoogleLoginRequested()
            expectState { copy(directLogin = true, loginSheetId = 1) }

            auth.sessionId = "s1"
            containerHost.onLoginSheetFinished(LaunchLoginResult.Completed(userId = "u1", sessionId = "s1", profile = eligible))
            expectState { copy(directLogin = false) }
            scope.settle()
            assertEquals(signedIn, containerHost.account.value)
        }
        assertEquals("u1", users.currentUserId.value)
    }

    @Test
    fun `Google 바로 로그인 - 덮개 · 시트가 있거나 홈 전이면 시작하지 않는다`() = runTest {
        val auth = FakeAuth().apply { sessionId = null }
        coordinator(auth).test(this, home.copy(launchLogin = true)) {
            containerHost.onGoogleLoginRequested()
        }
        coordinator(auth).test(this, home.copy(loginSheet = LoginContext.Flag, loginSheetId = 1)) {
            containerHost.onGoogleLoginRequested()
        }
        coordinator(auth).test(this, AppCoordinatorState()) {
            containerHost.onGoogleLoginRequested()
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
    }
}
