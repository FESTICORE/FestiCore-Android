package io.festicore.domain.auth.sources.di

import io.festicore.domain.auth.AuthRepository
import io.festicore.domain.auth.GoogleLoginUseCase
import io.festicore.domain.auth.GoogleSignInClient
import io.festicore.domain.auth.sources.AuthRepositoryImpl
import io.festicore.domain.auth.sources.CredentialManagerGoogleSignInClient
import io.festicore.domain.auth.sources.GoogleLoginUseCaseImpl
import org.koin.core.module.Module
import org.koin.dsl.module

/** [googleServerClientId]는 app이 `BuildConfig`로 넘긴다. [io.festicore.domain.auth.sources.ActivityProvider]는 app이 등록한다. */
fun domainAuthModule(googleServerClientId: String): Module = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<GoogleSignInClient> { CredentialManagerGoogleSignInClient(get(), googleServerClientId) }
    factory<GoogleLoginUseCase> { GoogleLoginUseCaseImpl(get(), get()) }
}
