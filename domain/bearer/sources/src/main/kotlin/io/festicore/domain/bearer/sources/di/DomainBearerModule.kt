package io.festicore.domain.bearer.sources.di

import io.festicore.domain.bearer.bearer.BearerRepository
import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.sources.bearer.BearerUseCaseImpl
import io.festicore.domain.bearer.sources.model.mapper.BearerImageUrl
import io.festicore.domain.bearer.sources.repository.BearerRepositoryImpl
import io.festicore.service.auth.AuthSession
import io.festicore.service.device.DeviceInstallationService
import org.koin.core.module.Module
import org.koin.dsl.module

/** [baseUrl]은 서버가 주는 상대 사진 경로를 풀 API 주소다(iOS `NetworkClientFactory.bundleBaseURL`). */
fun domainBearerModule(baseUrl: String): Module = module {
    single<BearerRepository> {
        BearerRepositoryImpl(
            api = get(),
            currentSessionId = get<AuthSession>()::currentSessionId,
            installationDeviceId = get<DeviceInstallationService>()::installationDeviceId,
            imageUrl = BearerImageUrl(baseUrl),
        )
    }
    factory<BearerUseCase> { BearerUseCaseImpl(get(), get()) }
}
