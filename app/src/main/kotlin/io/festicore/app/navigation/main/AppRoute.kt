package io.festicore.app.navigation.main

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** 루트 back stack 키. 온보딩은 feature 경로 `OnboardingRoute`를 쓴다. iOS `AppCoordinator.State`. */
@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Splash : AppRoute

    @Serializable
    data object Home : AppRoute
}
