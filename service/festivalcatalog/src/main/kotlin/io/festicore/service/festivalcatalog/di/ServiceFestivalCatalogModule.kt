package io.festicore.service.festivalcatalog.di

import io.festicore.domain.festival.sources.service.FestivalCatalogService
import io.festicore.domain.festival.sources.service.FestivalFlagFollowService
import io.festicore.domain.festival.sources.service.FestivalLiveBoardService
import io.festicore.domain.festival.sources.service.FestivalOfficialNewsService
import io.festicore.domain.festival.sources.service.FestivalScheduleService
import io.festicore.service.auth.AuthSession
import io.festicore.service.device.DeviceInstallationService
import io.festicore.service.festivalcatalog.FestivalCatalogCache
import io.festicore.service.festivalcatalog.FestivalImageUrlResolver
import io.festicore.service.festivalcatalog.NetworkFestivalCatalogService
import io.festicore.service.festivalcatalog.NetworkFestivalFlagFollowService
import io.festicore.service.festivalcatalog.NetworkFestivalLiveBoardService
import io.festicore.service.festivalcatalog.NetworkFestivalOfficialNewsService
import io.festicore.service.festivalcatalog.NetworkFestivalScheduleService
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** [baseUrl]은 app이 `BuildConfig.BASE_URL`로 넘긴다(썸네일 상대 경로 결합). 캐시 파일은 앱 전용 `filesDir` 아래. */
fun serviceFestivalCatalogModule(baseUrl: String): Module = module {
    single { FestivalCatalogCache(File(androidContext().filesDir, CACHE_PATH), get()) }
    single { FestivalImageUrlResolver(baseUrl) }
    single<FestivalCatalogService> { NetworkFestivalCatalogService(get(), get(), get()) }
    // 세션별 ETag · 429 대기를 들고 있으므로 하나만 둔다.
    single<FestivalLiveBoardService> {
        NetworkFestivalLiveBoardService(
            api = get(),
            deviceId = get<DeviceInstallationService>()::installationDeviceId,
            currentSessionId = get<AuthSession>()::currentSessionId,
        )
    }
    single<FestivalOfficialNewsService> { NetworkFestivalOfficialNewsService(get()) }
    single<FestivalScheduleService> { NetworkFestivalScheduleService(api = get(), currentSessionId = get<AuthSession>()::currentSessionId) }
    single<FestivalFlagFollowService> {
        NetworkFestivalFlagFollowService(api = get(), currentSessionId = get<AuthSession>()::currentSessionId)
    }
}

private const val CACHE_PATH = "festicore/festival-catalog.json"
