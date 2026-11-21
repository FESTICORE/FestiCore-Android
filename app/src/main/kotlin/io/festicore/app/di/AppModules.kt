package io.festicore.app.di

import io.festicore.app.BuildConfig
import io.festicore.app.MainActivityProvider
import io.festicore.app.navigation.AppCoordinator
import io.festicore.core.datastore.di.coreDataStoreModule
import io.festicore.core.network.di.coreNetworkModule
import io.festicore.domain.auth.sources.ActivityProvider
import io.festicore.domain.auth.sources.di.domainAuthModule
import io.festicore.feature.auth.di.authFeatureModule
import io.festicore.feature.onboarding.di.onboardingFeatureModule
import io.festicore.service.api.di.serviceApiModule
import io.festicore.service.auth.AuthSession
import io.festicore.service.auth.SessionExpiryEvents
import io.festicore.service.auth.di.serviceAuthModule
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/** app이 가진 루트 Coordinator와 현재 Activity 제공자만 등록한다. */
private val appModule = module {
    single { MainActivityProvider() } bind ActivityProvider::class
    viewModel {
        val session = get<AuthSession>()
        AppCoordinator(
            onboardingStore = get(),
            isLoggedIn = session::isLoggedIn,
            sessionExpiry = get<SessionExpiryEvents>().events,
            authRepository = get(),
            currentSessionId = session::currentSessionId,
        )
    }
}

/** 각 모듈이 공개한 Koin 모듈 목록만 합친다. 정의는 그 모듈의 di/에 둔다. */
val appModules: List<Module> = listOf(
    coreNetworkModule,
    coreDataStoreModule,
    serviceApiModule,
    serviceAuthModule(baseUrl = BuildConfig.BASE_URL),
    domainAuthModule(googleServerClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID),
    onboardingFeatureModule,
    authFeatureModule(legalBaseUrl = BuildConfig.LEGAL_BASE_URL),
    appModule,
)
