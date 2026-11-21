package io.festicore.feature.onboarding

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** iOS `OnboardingFeature`. 완료 저장은 하지 않고 상위에 알리기만 한다. */
class OnboardingViewModel :
    ViewModel(),
    OrbitContainerHost<OnboardingState, OnboardingState, OnboardingSideEffect> {
    override val container = orbitContainer<OnboardingState, OnboardingSideEffect>(OnboardingState())

    fun onPageChanged(page: Int) = intent {
        reduce { state.copy(page = page.coerceIn(0, OnboardingPage.all.lastIndex)) }
    }

    fun onNextTapped() = intent {
        if (state.page < OnboardingPage.all.lastIndex) {
            reduce { state.copy(page = state.page + 1) }
        } else {
            postSideEffect(OnboardingSideEffect.Completed)
        }
    }

    fun onSkipTapped() = intent {
        postSideEffect(OnboardingSideEffect.Completed)
    }
}
