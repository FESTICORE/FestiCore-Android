package io.festicore.app.di

import io.festicore.app.BuildConfig
import io.festicore.app.navigation.AppCoordinator
import io.festicore.core.datastore.di.coreDataStoreModule
import io.festicore.core.network.di.coreNetworkModule
import io.festicore.feature.onboarding.di.onboardingFeatureModule
import io.festicore.service.api.di.serviceApiModule
import io.festicore.service.auth.AuthSession
import io.festicore.service.auth.SessionExpiryEvents
import io.festicore.service.auth.di.serviceAuthModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** app이 가진 루트 Coordinator만 등록한다. */
private val appModule = module {
    viewModel { AppCoordinator(get(), get<AuthSession>()::isLoggedIn, get<SessionExpiryEvents>().events) }
}

/** 각 모듈이 공개한 Koin 모듈 목록만 합친다. 정의는 그 모듈의 di/에 둔다. */
val appModules: List<Module> = listOf(
    coreNetworkModule,
    coreDataStoreModule,
    serviceApiModule,
    serviceAuthModule(baseUrl = BuildConfig.BASE_URL),
    onboardingFeatureModule,
    appModule,
)
