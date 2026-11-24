package io.festicore.feature.onboarding

/** 온보딩이 상위에 알리는 결과. 완료 저장은 상위(app)가 맡는다. iOS `OnboardingFeature.DelegateAction.completed`. */
sealed interface OnboardingResult {
    /** 마지막 페이지에서 시작하거나 건너뛰었다. */
    data object Completed : OnboardingResult
}
