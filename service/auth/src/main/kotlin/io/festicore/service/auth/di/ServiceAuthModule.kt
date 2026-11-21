package io.festicore.service.auth.di

import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.service.api.AuthApi
import io.festicore.service.auth.AuthSession
import io.festicore.service.auth.CredentialStore
import io.festicore.service.auth.SessionExpiryEvents
import io.festicore.service.auth.TokenRefresher
import io.festicore.service.auth.festiCoreAuthPlugin
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/** 앱 서버 [HttpClient](인증 플러그인 포함)와 세션을 등록한다. [baseUrl]은 app이 `BuildConfig.BASE_URL`로 넘긴다. */
fun serviceAuthModule(baseUrl: String): Module = module {
    single { CredentialStore(get()) }
    single { SessionExpiryEvents() }
    single { AuthSession(get()) }
    // refresh 호출은 같은 클라이언트를 쓰지만 Bearer 표시가 없어 인증 플러그인을 거치지 않는다.
    single { TokenRefresher(get(), get()) { refreshToken -> get<AuthApi>().refresh(refreshToken) } }
    single<HttpClient> {
        val plugin = festiCoreAuthPlugin(get(), get())
        createFestiCoreHttpClient(baseUrl, json = get()) { install(plugin) }
    }
}
