package io.festicore.service.festivalcatalog.di

import io.festicore.service.auth.AuthSession
import io.festicore.service.device.DeviceInstallationService
import io.festicore.service.festivalcatalog.FestivalCatalogCache
import io.festicore.service.festivalcatalog.FestivalCatalogService
import io.festicore.service.festivalcatalog.FestivalImageUrlResolver
import io.festicore.service.festivalcatalog.FestivalLiveBoardService
import io.festicore.service.festivalcatalog.FestivalOfficialNewsService
import io.festicore.service.festivalcatalog.FestivalScheduleService
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** [baseUrl]은 app이 `BuildConfig.BASE_URL`로 넘긴다(썸네일 상대 경로 결합). 캐시 파일은 앱 전용 `filesDir` 아래. */
fun serviceFestivalCatalogModule(baseUrl: String): Module = module {
    single { FestivalCatalogCache(File(androidContext().filesDir, CACHE_PATH), get()) }
    single { FestivalImageUrlResolver(baseUrl) }
    single { FestivalCatalogService(get(), get(), get()) }
    // 세션별 ETag · 429 대기를 들고 있으므로 하나만 둔다.
    single {
        FestivalLiveBoardService(
            api = get(),
            deviceId = get<DeviceInstallationService>()::installationDeviceId,
            currentSessionId = get<AuthSession>()::currentSessionId,
        )
    }
    single { FestivalOfficialNewsService(get()) }
    single { FestivalScheduleService(get()) }
}

private const val CACHE_PATH = "festicore/festival-catalog.json"
