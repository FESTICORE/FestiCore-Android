package io.festicore.core.coreassembly

import io.festicore.core.network.di.coreNetworkModule
import io.festicore.core.storage.di.coreStorageModule
import org.koin.core.module.Module

/** iOS `Core/CoreAssembly`처럼 core 모듈의 Koin 목록만 합친다. 정의는 각 core 모듈 `di/`에 둔다. */
val coreModules: List<Module> = listOf(
    coreNetworkModule,
    coreStorageModule,
)
