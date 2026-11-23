package io.festicore.app.di

import io.festicore.app.AppDeeplinkBridge
import io.festicore.app.BuildConfig
import io.festicore.app.MainActivityProvider
import io.festicore.app.navigation.AppCoordinator
import io.festicore.app.navigation.MainTabCoordinator
import io.festicore.core.datastore.di.coreDataStoreModule
import io.festicore.core.network.di.coreNetworkModule
import io.festicore.domain.auth.sources.ActivityProvider
import io.festicore.domain.auth.sources.CurrentUserStore
import io.festicore.domain.auth.sources.di.domainAuthModule
import io.festicore.domain.festival.sources.di.domainFestivalModule
import io.festicore.domain.usernotification.sources.di.domainUserNotificationModule
import io.festicore.feature.auth.di.authFeatureModule
import io.festicore.feature.festival.di.festivalFeatureModule
import io.festicore.feature.onboarding.di.onboardingFeatureModule
import io.festicore.feature.web.di.webFeatureModule
import io.festicore.service.apiendpoint.di.serviceApiEndpointModule
import io.festicore.service.auth.AuthSession
import io.festicore.service.auth.SessionExpiryEvents
import io.festicore.service.auth.di.serviceAuthModule
import io.festicore.service.device.di.serviceDeviceModule
import io.festicore.service.festivalcatalog.di.serviceFestivalCatalogModule
import io.festicore.service.pack.di.servicePackModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** app이 가진 루트 · 메인 탭 Coordinator, 현재 Activity 제공자, 딥링크 브리지만 등록한다. */
private val appModule = module {
    single { MainActivityProvider() } bind ActivityProvider::class
    single { AppDeeplinkBridge(store = get(), scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    viewModel {
        val session = get<AuthSession>()
        AppCoordinator(
            onboardingStore = get(),
            isLoggedIn = session::isLoggedIn,
            sessionExpired = get<SessionExpiryEvents>().expired,
            authRepository = get(),
            currentSessionId = session::currentSessionId,
            updateCurrentUserId = get<CurrentUserStore>()::update,
            deeplinkReceived = get<AppDeeplinkBridge>().received,
            consumePendingDeeplink = get<AppDeeplinkBridge>()::consumePending,
        )
    }
    viewModelOf(::MainTabCoordinator)
}

/** 각 모듈이 공개한 Koin 모듈 목록만 합친다. 정의는 그 모듈의 di/에 둔다. */
val appModules: List<Module> = listOf(
    coreNetworkModule,
    coreDataStoreModule,
    serviceApiEndpointModule,
    serviceAuthModule(baseUrl = BuildConfig.BASE_URL),
    serviceDeviceModule,
    serviceFestivalCatalogModule(baseUrl = BuildConfig.BASE_URL),
    servicePackModule(baseUrl = BuildConfig.BASE_URL),
    domainAuthModule(googleServerClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID),
    domainFestivalModule,
    domainUserNotificationModule,
    onboardingFeatureModule,
    authFeatureModule(legalBaseUrl = BuildConfig.LEGAL_BASE_URL),
    webFeatureModule,
    festivalFeatureModule,
    appModule,
)
