package io.festicore.app.di

import io.festicore.app.BuildConfig
import io.festicore.app.application.MainActivityProvider
import io.festicore.app.application.StoredAppearanceModeSettings
import io.festicore.app.deeplink.AppDeeplinkBridge
import io.festicore.app.navigation.AppCoordinator
import io.festicore.app.navigation.MainTabCoordinator
import io.festicore.core.coreassembly.coreModules
import io.festicore.domain.auth.sources.ActivityProvider
import io.festicore.domain.auth.sources.CurrentUserStore
import io.festicore.domain.auth.sources.di.domainAuthModule
import io.festicore.domain.festival.pack.FestivalPackUseCase
import io.festicore.domain.festival.sources.di.domainFestivalModule
import io.festicore.domain.notice.sources.di.domainNoticeModule
import io.festicore.domain.usernotification.sources.di.domainUserNotificationModule
import io.festicore.feature.featureassembly.featureModules
import io.festicore.feature.profile.appearance.AppearanceModeSettings
import io.festicore.service.auth.AuthSession
import io.festicore.service.auth.expiry.SessionExpiryEvents
import io.festicore.service.serviceassembly.serviceModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** app이 가진 루트 · 메인 탭 Coordinator, 현재 Activity 제공자, 딥링크 브리지, 화면 모드 저장소 연결만 등록한다. */
private val appModule = module {
    single { MainActivityProvider() } bind ActivityProvider::class
    single<AppearanceModeSettings> { StoredAppearanceModeSettings(get()) }
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
            deleteExpiredPacks = { get<FestivalPackUseCase>().deleteExpiredPacks() },
        )
    }
    viewModelOf(::MainTabCoordinator)
}

/** core · service · domain · feature 목록과 app 정의를 합친다. 정의는 각 모듈의 di/에 두고, core · service · feature 목록은 각 assembly 모듈이 가진다. */
val appModules: List<Module> =
    coreModules +
        serviceModules(baseUrl = BuildConfig.BASE_URL) +
        listOf(
            domainAuthModule(googleServerClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID),
            domainFestivalModule,
            domainUserNotificationModule,
            domainNoticeModule,
        ) +
        featureModules(legalBaseUrl = BuildConfig.LEGAL_BASE_URL) +
        appModule
