package io.festicore.feature.onboarding

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 첫 설치 온보딩 화면 경로. iOS `Feature/Onboarding`의 진입점과 같다. */
@Serializable
data object OnboardingRoute : NavKey
