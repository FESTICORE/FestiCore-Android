package io.festicore.domain.bearer.sources.di

import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.domain.bearer.bearer.BearerRepository
import io.festicore.domain.bearer.bearer.BearerStatusOutbox
import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.sources.bearer.BearerUseCaseImpl
import io.festicore.domain.bearer.sources.model.mapper.BearerImageUrl
import io.festicore.domain.bearer.sources.outbox.BearerServerClock
import io.festicore.domain.bearer.sources.outbox.BearerStatusOutboxImpl
import io.festicore.domain.bearer.sources.outbox.BearerStatusOutboxResumer
import io.festicore.domain.bearer.sources.repository.BearerRepositoryImpl
import io.festicore.service.auth.AuthSession
import io.festicore.service.device.DeviceInstallationService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * [baseUrl]은 서버가 주는 상대 사진 경로를 풀 API 주소다(iOS `NetworkClientFactory.bundleBaseURL`).
 * [filesDir]는 앱 `filesDir`이다. 상태 대기열 파일은 그 아래 [BearerStatusOutboxImpl.FILE_PATH]에 둔다(설계 쟁점 B3, Context는 받지 않는다).
 * [reconnected]는 네트워크가 다시 연결될 때마다 값을 낸다(대기열 자동 재개).
 */
fun domainBearerModule(
    baseUrl: String,
    filesDir: Scope.() -> File,
    reconnected: Scope.() -> Flow<Unit>,
): Module = module {
    single { BearerServerClock() }
    single {
        BearerRepositoryImpl(
            api = get(),
            currentSessionId = get<AuthSession>()::currentSessionId,
            installationDeviceId = get<DeviceInstallationService>()::installationDeviceId,
            imageUrl = BearerImageUrl(baseUrl),
            serverClock = get(),
        )
    } bind BearerRepository::class
    single {
        val repository = get<BearerRepositoryImpl>()
        val currentUser = get<CurrentUserProvider>()
        BearerStatusOutboxImpl(
            file = File(filesDir(), BearerStatusOutboxImpl.FILE_PATH),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            currentUserId = { currentUser.currentUserId.value },
            currentSessionId = get<AuthSession>()::currentSessionId,
            sendStatus = repository::sendStatus,
            serverClock = get<BearerServerClock>(),
        )
    } bind BearerStatusOutbox::class
    // 앱 시작 · 로그인 · 연결 복구에서 대기열을 이어 보낸다(설계 쟁점 B4).
    single(createdAtStart = true) {
        BearerStatusOutboxResumer(get(), get<CurrentUserProvider>().currentUserId, reconnected())
            .also { it.start(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    }
    factory<BearerUseCase> { BearerUseCaseImpl(get(), get(), clock = get<BearerServerClock>(), outbox = get()) }
}
