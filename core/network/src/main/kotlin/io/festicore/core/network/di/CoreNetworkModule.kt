package io.festicore.core.network.di

import io.festicore.core.network.FestiCoreJson
import org.koin.dsl.module

/** HttpClient는 BASE_URL을 가진 쪽이 [io.festicore.core.network.createFestiCoreHttpClient]로 만든다. */
val coreNetworkModule = module {
    single { FestiCoreJson }
}
