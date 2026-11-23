package io.festicore.service.pack.di

import io.festicore.core.network.createFestiCoreWebSocketClient
import io.festicore.service.pack.NetworkPackSyncService
import io.festicore.service.pack.PackCache
import io.festicore.service.pack.PackSyncService
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** [baseUrl]은 app이 `BuildConfig.BASE_URL`로 넘긴다(https → wss). 팩 파일은 앱 전용 `filesDir/festicore/packs/` 아래. */
fun servicePackModule(baseUrl: String): Module = module {
    single { PackCache(File(androidContext().filesDir, PACK_DIRECTORY), get()) }
    // 요청 세대 · 삭제 횟수를 들고 있으므로 하나만 둔다.
    single<PackSyncService> { NetworkPackSyncService(get(), createFestiCoreWebSocketClient(baseUrl)) }
}

private const val PACK_DIRECTORY = "festicore/packs"
