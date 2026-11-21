package io.festicore.service.festivalcatalog.di

import io.festicore.service.festivalcatalog.FestivalCatalogCache
import io.festicore.service.festivalcatalog.FestivalCatalogService
import io.festicore.service.festivalcatalog.FestivalImageUrlResolver
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** [baseUrl]은 app이 `BuildConfig.BASE_URL`로 넘긴다(썸네일 상대 경로 결합). 캐시 파일은 앱 전용 `filesDir` 아래. */
fun serviceFestivalCatalogModule(baseUrl: String): Module = module {
    single { FestivalCatalogCache(File(androidContext().filesDir, CACHE_PATH), get()) }
    single { FestivalImageUrlResolver(baseUrl) }
    single { FestivalCatalogService(get(), get(), get()) }
}

private const val CACHE_PATH = "festicore/festival-catalog.json"
