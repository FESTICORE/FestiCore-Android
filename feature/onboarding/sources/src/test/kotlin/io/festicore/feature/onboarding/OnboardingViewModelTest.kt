package io.festicore.feature.onboarding

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.orbitmvi.orbit.test.test

class OnboardingViewModelTest {
    @Test
    fun `페이지 변경은 0 이상 마지막 페이지 이하로 제한된다`() = runTest {
        OnboardingViewModel().test(this) {
            containerHost.onPageChanged(2)
            expectState { copy(page = 2) }

            containerHost.onPageChanged(99)
            expectState { copy(page = 3) }

            containerHost.onPageChanged(-5)
            expectState { copy(page = 0) }
        }
    }

    @Test
    fun `다음을 누르면 중간 페이지에서는 한 페이지 넘어간다`() = runTest {
        OnboardingViewModel().test(this) {
            containerHost.onNextTapped()
            expectState { copy(page = 1) }
        }
    }

    @Test
    fun `다음을 누르면 마지막 페이지에서는 완료를 알린다`() = runTest {
        OnboardingViewModel().test(this) {
            containerHost.onPageChanged(3)
            expectState { copy(page = 3) }

            containerHost.onNextTapped()
            expectSideEffect(OnboardingSideEffect.Completed)
        }
    }

    @Test
    fun `건너뛰기는 어느 페이지에서든 완료를 알린다`() = runTest {
        OnboardingViewModel().test(this) {
            containerHost.onSkipTapped()
            expectSideEffect(OnboardingSideEffect.Completed)
        }
    }
}
