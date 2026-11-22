package io.festicore.domain.usernotification.sources.di

import io.festicore.domain.usernotification.UserNotificationBadgeUseCase
import io.festicore.domain.usernotification.UserNotificationRepository
import io.festicore.domain.usernotification.UserNotificationUseCase
import io.festicore.domain.usernotification.sources.UserNotificationBadgeStore
import io.festicore.domain.usernotification.sources.UserNotificationRepositoryImpl
import io.festicore.domain.usernotification.sources.UserNotificationUseCaseImpl
import io.festicore.service.auth.AuthSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val domainUserNotificationModule = module {
    single<UserNotificationRepository> { UserNotificationRepositoryImpl(get(), get<AuthSession>()::currentSessionId) }
    factory<UserNotificationUseCase> { UserNotificationUseCaseImpl(get()) }
    // 보드 · 알림함이 같은 배지를 보도록 하나만 둔다. 읽음 요청은 앱이 사는 동안의 범위에서 보낸다(설계 E7).
    single<UserNotificationBadgeUseCase> {
        UserNotificationBadgeStore(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
