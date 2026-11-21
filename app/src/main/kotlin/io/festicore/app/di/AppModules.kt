package io.festicore.app.di

import io.festicore.core.datastore.di.coreDataStoreModule
import io.festicore.core.network.di.coreNetworkModule
import org.koin.core.module.Module

/** 각 모듈이 공개한 Koin 모듈 목록만 합친다. 정의는 그 모듈의 di/에 둔다. */
val appModules: List<Module> = listOf(
    coreNetworkModule,
    coreDataStoreModule,
)
