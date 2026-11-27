package io.festicore.feature.festival.flagdetail

import io.festicore.domain.bearer.testing.FakeBearerUseCase
import io.festicore.domain.festival.entity.FestivalFlagPinError
import io.festicore.feature.festival.RecordingAnalyticsUseCase
import io.festicore.feature.festival.flagdetail.FlagDetailFixtures.NOW
import io.festicore.feature.festival.liveboard.FakeFlagUseCase
import io.festicore.feature.festival.liveboard.TestSleeper
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

class FlagDetailWidgetPinTest {
    private fun viewModel(useCase: FakeFlagDetailUseCase, sleep: suspend (Duration) -> Unit = { awaitCancellation() }) =
        FlagDetailViewModel(
            flagId = "flg_1",
            context = FlagDetailFixtures.context(),
            detailUseCase = useCase,
            flagUseCase = FakeFlagUseCase(),
            bearerUseCase = FakeBearerUseCase(),
            analyticsUseCase = RecordingAnalyticsUseCase(),
            clock = MutableClock(NOW),
            sleep = sleep,
        )

    @Test
    fun `대표 깃발은 성공 전 선택값을 바꾸지 않고 중복 탭을 무시한다`() = runTest {
        val result = CompletableDeferred<String?>()
        val useCase = FakeFlagDetailUseCase(pin = { result.await() })
        val initial = FlagDetailFixtures.loaded(FlagDetailFixtures.detail(isFollowing = true))
        viewModel(useCase).test(this, initialState = initial) {
            containerHost.onWidgetPinChanged(true).join()
            assertTrue("대표 요청은 응답 전 pending이어야 한다", containerHost.container.stateFlow.value.isPinPending)
            val pending = awaitState()
            assertTrue(pending.isPinPending)
            assertFalse(pending.isPinned)
            containerHost.onWidgetPinChanged(true)
            result.complete("flg_1")
            val saved = awaitState()
            assertFalse(saved.isPinPending)
            assertTrue(saved.isPinned)
            assertEquals(listOf("flg_1"), useCase.pins)
            expectSideEffect(FlagDetailSideEffect.PinChanged)
        }
    }

    @Test
    fun `대표 깃발 실패는 선택값을 유지하고 하단 오류 토스트를 보낸다`() = runTest {
        val sleeper = TestSleeper()
        val useCase = FakeFlagDetailUseCase(pin = { throw FestivalFlagPinError.Unavailable() })
        val initial = FlagDetailFixtures.loaded(FlagDetailFixtures.detail(isFollowing = true)).copy(pinOverride = true)
        viewModel(useCase, sleeper::sleep).test(this, initialState = initial) {
            containerHost.onWidgetPinChanged(false)
            assertTrue(awaitState().isPinPending)
            val failed = awaitState()
            assertFalse(failed.isPinPending)
            assertTrue(failed.isPinned)
            expectState { copy(toast = FlagDetailToast.PinFailed) }
            assertEquals(listOf<String?>(null), useCase.pins)
            sleeper.release(2.seconds)
            expectState { copy(toast = null) }
        }
    }

    @Test
    fun `로그아웃은 진행 중 대표 요청을 취소하고 이전 선택을 비운다`() = runTest {
        val result = CompletableDeferred<String?>()
        var cancelled = false
        val useCase = FakeFlagDetailUseCase(detail = { awaitCancellation() }, pin = {
            try {
                result.await()
            } finally {
                cancelled = true
            }
        })
        val initial = FlagDetailFixtures.loaded(FlagDetailFixtures.detail(isFollowing = true))
        viewModel(useCase).test(this, initialState = initial) {
            containerHost.onWidgetPinChanged(true)
            assertTrue(awaitState().isPinPending)
            containerHost.onContextChanged(FlagDetailFixtures.context(hasSession = false))
            val loggedOut = awaitState()
            assertFalse(loggedOut.isPinPending)
            assertFalse(loggedOut.showsWidgetPin)
            assertEquals(null, loggedOut.pinOverride)
            result.complete("flg_1")
            containerHost.onDisappeared()
            expectState { copy(loadGeneration = loadGeneration + 1) }
            assertTrue(cancelled)
        }
    }

    @Test
    fun `대표 변경 이전에 시작한 상세 응답은 성공값을 되돌리지 않는다`() = runTest {
        val initial = FlagDetailFixtures.loaded(FlagDetailFixtures.detail(isFollowing = true))
        viewModel(FakeFlagDetailUseCase()).test(this, initialState = initial) {
            containerHost.onWidgetPinChanged(true)
            awaitState()
            assertTrue(awaitState().isPinned)
            expectSideEffect(FlagDetailSideEffect.PinChanged)
            containerHost.onLoaded(0, FlagDetailFixtures.detail(isFollowing = true).copy(isPinned = false), NOW).join()
            assertTrue("이전 상세 응답은 pin 성공을 덮지 않아야 한다", containerHost.container.stateFlow.value.isPinned)
        }
    }

    @Test
    fun `대표 해제 성공은 서버 null을 선택 해제로 반영한다`() = runTest {
        val initial = FlagDetailFixtures.loaded(FlagDetailFixtures.detail(isFollowing = true)).copy(pinOverride = true)
        val useCase = FakeFlagDetailUseCase()
        viewModel(useCase).test(this, initialState = initial) {
            containerHost.onWidgetPinChanged(false)
            assertTrue(awaitState().isPinPending)
            assertFalse(awaitState().isPinned)
            expectSideEffect(FlagDetailSideEffect.PinChanged)
            assertEquals(listOf<String?>(null), useCase.pins)
        }
    }

    @Test
    fun `대표 변경의 인증 오류는 하단 오류와 로그인 약관 경계를 전달한다`() = runTest {
        listOf(
            FestivalFlagPinError.LoginRequired() to FlagDetailSideEffect.LoginRequested,
            FestivalFlagPinError.TermsRequired() to FlagDetailSideEffect.TermsAcceptanceRequested,
        ).forEach { (error, sideEffect) ->
            val sleeper = TestSleeper()
            viewModel(FakeFlagDetailUseCase(pin = { throw error }), sleeper::sleep)
                .test(this, initialState = FlagDetailFixtures.loaded(FlagDetailFixtures.detail(isFollowing = true))) {
                    containerHost.onWidgetPinChanged(true)
                    assertTrue(awaitState().isPinPending)
                    assertFalse(awaitState().isPinPending)
                    expectState { copy(toast = FlagDetailToast.PinFailed) }
                    expectSideEffect(sideEffect)
                    sleeper.release(2.seconds)
                    expectState { copy(toast = null) }
                }
        }
    }

    @Test
    fun `언팔로우 성공은 대표 선택을 숨기고 서버 재조회를 한다`() = runTest {
        val initial = FlagDetailFixtures.loaded(FlagDetailFixtures.detail(isFollowing = true)).copy(pinOverride = true)
        viewModel(FakeFlagDetailUseCase(detail = { awaitCancellation() })).test(this, initialState = initial) {
            containerHost.onFollowTapped()
            assertTrue(awaitState().isFollowPending)
            val unfollowed = awaitState()
            assertFalse(unfollowed.isFollowing)
            assertFalse(unfollowed.isPinned)
            assertFalse(unfollowed.showsWidgetPin)
            expectSideEffect(FlagDetailSideEffect.FollowChanged("flg_1", false, 1))
            containerHost.onDisappeared()
            expectState { copy(loadGeneration = loadGeneration + 1) }
        }
    }

    @Test
    fun `비로그인 미팔로우 오프라인 화면에서는 대표 요청을 보내지 않는다`() = runTest {
        val followed = FlagDetailFixtures.loaded(FlagDetailFixtures.detail(isFollowing = true))
        listOf(
            followed.copy(context = FlagDetailFixtures.context(hasSession = false)),
            FlagDetailFixtures.loaded(),
            followed.copy(isOffline = true),
        ).forEach { initial ->
            val useCase = FakeFlagDetailUseCase()
            viewModel(useCase).test(this, initialState = initial) {
                containerHost.onWidgetPinChanged(true)
                assertEquals(emptyList<String?>(), useCase.pins)
            }
        }
    }
}
