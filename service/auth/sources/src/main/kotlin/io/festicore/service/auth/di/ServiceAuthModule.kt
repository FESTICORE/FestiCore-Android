package io.festicore.service.auth.di

import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.service.apiendpoint.auth.AuthRequest
import io.festicore.service.auth.AuthSession
import io.festicore.service.auth.AuthSessionImpl
import io.festicore.service.auth.expiry.SessionExpiryEvents
import io.festicore.service.auth.expiry.SessionExpiryEventsImpl
import io.festicore.service.auth.festiCoreAuthPlugin
import io.festicore.service.auth.refresh.TokenRefresher
import io.festicore.service.auth.store.CredentialStore
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** 앱 서버 [HttpClient](인증 플러그인 포함)와 세션을 등록한다. [baseUrl]은 app이 `BuildConfig.BASE_URL`로 넘긴다. */
fun serviceAuthModule(baseUrl: String): Module = module {
    single { CredentialStore(get()) }
    single { SessionExpiryEventsImpl() } bind SessionExpiryEvents::class
    single<AuthSession> { AuthSessionImpl(get()) }
    // refresh 호출은 같은 클라이언트를 쓰지만 Bearer 표시가 없어 인증 플러그인을 거치지 않는다.
    single { TokenRefresher(get(), get()) { refreshToken -> get<AuthRequest>().refresh(refreshToken) } }
    single<HttpClient> {
        val plugin = festiCoreAuthPlugin(get(), get())
        createFestiCoreHttpClient(baseUrl, json = get()) { install(plugin) }
    }
}
