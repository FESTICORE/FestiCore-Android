package io.festicore.service.serviceassembly

import io.festicore.service.apiendpoint.di.serviceApiEndpointModule
import io.festicore.service.auth.di.serviceAuthModule
import io.festicore.service.device.di.serviceDeviceModule
import io.festicore.service.festivalcatalog.di.serviceFestivalCatalogModule
import io.festicore.service.pack.di.servicePackModule
import org.koin.core.module.Module

/** iOS `Service/ServiceAssembly`처럼 service 모듈의 Koin 목록만 합친다. 각 서비스 모듈이 `di/`에서 domain 계약에 자기 구현을 `bind`한다. */
fun serviceModules(baseUrl: String): List<Module> = listOf(
    serviceApiEndpointModule,
    serviceAuthModule(baseUrl = baseUrl),
    serviceDeviceModule,
    serviceFestivalCatalogModule(baseUrl = baseUrl),
    servicePackModule(baseUrl = baseUrl),
)
