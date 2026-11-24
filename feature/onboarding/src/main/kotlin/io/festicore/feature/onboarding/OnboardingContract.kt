package io.festicore.feature.onboarding

/** iOS `OnboardingFeature.State`. */
data class OnboardingState(val page: Int = 0)

/** iOS `OnboardingFeature.DelegateAction`. */
sealed interface OnboardingSideEffect {
    data object Completed : OnboardingSideEffect
}
