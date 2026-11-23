package io.festicore.feature.featureassembly

import io.festicore.feature.auth.di.authFeatureModule
import io.festicore.feature.festival.di.festivalFeatureModule
import io.festicore.feature.onboarding.di.onboardingFeatureModule
import io.festicore.feature.profile.di.profileFeatureModule
import io.festicore.feature.web.di.webFeatureModule
import org.koin.core.module.Module

/** iOS `Feature/FeatureAssembly`처럼 feature 모듈의 Koin 목록만 합친다. 정의는 각 feature 모듈 `di/`에 둔다. */
fun featureModules(legalBaseUrl: String): List<Module> = listOf(
    onboardingFeatureModule,
    authFeatureModule,
    webFeatureModule(legalBaseUrl = legalBaseUrl),
    festivalFeatureModule,
    profileFeatureModule,
)
