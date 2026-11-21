package io.festicore.service.api.di

import io.festicore.service.api.AuthApi
import io.festicore.service.api.FestivalApi
import org.koin.dsl.module

/** HttpClient는 service/auth가 인증 플러그인을 붙여 등록한다. */
val serviceApiModule = module {
    single { AuthApi(get()) }
    single { FestivalApi(get()) }
}
