package io.festicore.domain.auth.sources.di

import io.festicore.domain.auth.auth.AuthRepository
import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.domain.auth.google.GoogleLoginUseCase
import io.festicore.domain.auth.google.GoogleSignInClient
import io.festicore.domain.auth.sources.auth.CurrentUserStore
import io.festicore.domain.auth.sources.google.CredentialManagerGoogleSignInClient
import io.festicore.domain.auth.sources.google.GoogleLoginUseCaseImpl
import io.festicore.domain.auth.sources.repository.AuthRepositoryImpl
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** [googleServerClientId]는 app이 `BuildConfig`로 넘긴다. [io.festicore.domain.auth.sources.ActivityProvider]는 app이 등록한다. */
fun domainAuthModule(googleServerClientId: String): Module = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<GoogleSignInClient> { CredentialManagerGoogleSignInClient(get(), googleServerClientId) }
    factory<GoogleLoginUseCase> { GoogleLoginUseCaseImpl(get(), get()) }
    single { CurrentUserStore() } bind CurrentUserProvider::class
}
