package io.festicore.app.navigation

import io.festicore.core.datastore.FirstInstallOnboardingStore
import io.festicore.feature.onboarding.OnboardingRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

        override suspend fun hasCompleted(): Boolean = readError?.let { throw it } ?: completed

        override suspend fun markCompleted() {
            markCount++
            writeError?.let { throw it }
            completed = true
        }
    }

    private fun coordinator(
        store: FirstInstallOnboardingStore,
        loggedIn: Boolean,
        expiry: Flow<Unit> = emptyFlow(),
    ) = AppCoordinator(store, { loggedIn }, expiry)

    private fun TestScope.assertSplashLasted2Seconds() = assertEquals(2_000L, testScheduler.currentTime)

    @Test
    fun `첫 설치면 스플래시 2초 뒤 온보딩으로 바꾼다`() = runTest {
        val scope = this
        coordinator(FakeOnboardingStore(completed = false), loggedIn = true).test(this) {
            runOnCreate()
            expectState { copy(routes = listOf(OnboardingRoute)) }
            scope.assertSplashLasted2Seconds()
        }
    }

    @Test
    fun `온보딩을 마치면 완료를 저장하고 비로그인이면 launchLogin 덮개로 홈에 간다`() = runTest {
        val store = FakeOnboardingStore(completed = false)
        coordinator(store, loggedIn = false).test(this) {
            runOnCreate()
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
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = false) }
            scope.assertSplashLasted2Seconds()
        }
    }

    @Test
    fun `완료 기록이 있고 비로그인이면 스플래시 뒤 launchLogin 덮개로 홈에 간다`() = runTest {
        coordinator(FakeOnboardingStore(completed = true), loggedIn = false).test(this) {
            runOnCreate()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = true) }
        }
    }

    @Test
    fun `홈에서 세션 만료 이벤트를 받으면 launchLogin 덮개를 띄운다`() = runTest {
        val expiry = MutableSharedFlow<Unit>()
        coordinator(FakeOnboardingStore(completed = true), loggedIn = true, expiry = expiry).test(this) {
            runOnCreate()
            expectState { copy(routes = listOf(AppRoute.Home), launchLogin = false) }

            expiry.subscriptionCount.first { it > 0 }
            expiry.emit(Unit)
            expectState { copy(launchLogin = true) }
            // 만료 구독은 홈에 있는 동안 계속 돈다.
            cancelAndIgnoreRemainingItems()
        }
    }
}
