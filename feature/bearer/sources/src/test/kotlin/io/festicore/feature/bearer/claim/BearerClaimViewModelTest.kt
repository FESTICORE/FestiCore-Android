package io.festicore.feature.bearer.claim

import io.festicore.domain.bearer.entity.BearerError
import java.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `BearerClaimFeatureTests` 7건. 함수 이름을 iOS와 같게 둔다. */
class BearerClaimViewModelTest {
    private val start = Instant.ofEpochSecond(1_000)

    private fun viewModel(useCase: FakeBearerUseCase = FakeBearerUseCase(), clock: MutableClock = MutableClock(start)) =
        BearerClaimViewModel(festivalId = "fst_1", bearerUseCase = useCase, clock = clock)

    // 코드는 대문자 6자리 허용 글자만 남긴다
    @Test
    fun filtersCode() = runTest {
        viewModel().test(this) {
            containerHost.onCodeChanged("r4k-9o1t2xy")
            expectState { copy(code = "R4K9T2") }
            val typed = containerHost.container.stateFlow.value
            assertTrue(typed.canSubmit)
        }
    }

    // 맞는 코드는 기수 등록 결과를 부모에게 알린다
    @Test
    fun claims() = runTest {
        val useCase =
            FakeBearerUseCase(onClaim = { festivalId, code ->
                assertEquals("fst_1", festivalId)
                assertEquals("R4K9T2", code)
                SampleClaim
            })
        viewModel(useCase).test(this) {
            containerHost.onCodeChanged("R4K9T2")
            expectState { copy(code = "R4K9T2") }
            containerHost.onSubmitTapped()
            expectState { copy(isSubmitting = true) }
            expectState { copy(isSubmitting = false) }
            expectSideEffect(BearerClaimSideEffect.Claimed(festivalId = "fst_1", claim = SampleClaim))
        }
    }

    // 틀린 코드는 남은 횟수를 보이고 서버 값이 없으면 직접 센다
    @Test
    fun invalidCode() = runTest {
        val useCase = FakeBearerUseCase(onClaim = { _, _ -> throw BearerError.InvalidCode(remainingAttempts = null) })
        viewModel(useCase).test(this) {
            containerHost.onCodeChanged("R4K9T2")
            expectState { copy(code = "R4K9T2") }
            containerHost.onSubmitTapped()
            expectState { copy(isSubmitting = true) }
            expectState {
                copy(isSubmitting = false, consecutiveFailures = 1, failure = BearerClaimFailure.Invalid(remainingAttempts = 2))
            }
            containerHost.onCodeChanged("R4K9T")
            expectState { copy(code = "R4K9T", failure = null) }
            containerHost.onClaimFailed(BearerError.InvalidCode(remainingAttempts = 1))
            expectState { copy(consecutiveFailures = 2, failure = BearerClaimFailure.Invalid(remainingAttempts = 1)) }
        }
    }

    // 잠기면 입력을 비우고 1초마다 남은 시간을 줄인 뒤 풀어 준다
    @Test
    fun locked() = runTest {
        val clock = MutableClock(start)
        viewModel(clock = clock).test(this) {
            containerHost.onCodeChanged("R4K9T2")
            expectState { copy(code = "R4K9T2") }
            containerHost.onClaimFailed(BearerError.CodeLocked(retryAfter = 2))
            expectState { copy(code = "", failure = BearerClaimFailure.Locked, lockedUntil = start.plusSeconds(2)) }
            val locked = containerHost.container.stateFlow.value
            assertTrue(locked.isLocked)
            assertEquals(2, locked.lockRemainingSeconds)
            containerHost.onCodeChanged("ABCDEF")
            testScheduler.runCurrent()

            clock.now = start.plusSeconds(1)
            testScheduler.advanceTimeBy(1_000)
            testScheduler.runCurrent()
            expectState { copy(now = start.plusSeconds(1)) }
            val ticked = containerHost.container.stateFlow.value
            assertEquals("", ticked.code)
            assertEquals(1, ticked.lockRemainingSeconds)

            clock.now = start.plusSeconds(2)
            testScheduler.advanceTimeBy(1_000)
            testScheduler.runCurrent()
            expectState { copy(now = start.plusSeconds(2)) }
            expectState { copy(lockedUntil = null, failure = null) }
            val unlocked = containerHost.container.stateFlow.value
            assertFalse(unlocked.isLocked)

            // 풀리면 카운트다운도 멈춘다.
            testScheduler.advanceTimeBy(5_000)
            testScheduler.runCurrent()
        }
    }

    // 이미 다른 깃발을 맡았으면 안내 문구를 보인다
    @Test
    fun bearerLimit() = runTest {
        viewModel().test(this) {
            containerHost.onClaimFailed(BearerError.BearerLimit())
            expectState { copy(failure = BearerClaimFailure.Message("이 페스티벌에서 이미 다른 깃발을 맡고 있어요")) }
        }
    }

    // 확인 중에 닫으면 진행 중인 등록 요청을 취소한다
    @Test
    fun closeWhileSubmitting() = runTest {
        var cancelled = false
        val useCase =
            FakeBearerUseCase(onClaim = { _, _ ->
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            })
        viewModel(useCase).test(this) {
            containerHost.onCodeChanged("R4K9T2")
            expectState { copy(code = "R4K9T2") }
            containerHost.onSubmitTapped()
            expectState { copy(isSubmitting = true) }
            testScheduler.runCurrent()
            containerHost.onBackTapped()
            expectState { copy(isSubmitting = false) }
            expectSideEffect(BearerClaimSideEffect.CloseRequested)
            testScheduler.runCurrent()
            assertTrue(cancelled)
        }
    }

    // 뒤로 가기는 닫기를 요청한다
    @Test
    fun back() = runTest {
        viewModel().test(this) {
            containerHost.onBackTapped()
            expectSideEffect(BearerClaimSideEffect.CloseRequested)
        }
    }
}
