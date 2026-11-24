package io.festicore.core.location.di

import io.festicore.core.location.client.GoogleRoutesClient
import io.festicore.core.location.client.IntentMapsClient
import io.festicore.core.location.client.LocationClient
import io.festicore.core.location.client.MapAppClient
import io.festicore.core.location.client.MapsClient
import io.festicore.core.location.client.PackageManagerMapAppClient
import io.festicore.core.location.client.PlatformLocationClient
import io.festicore.core.location.client.appSigningIdentity
import io.festicore.core.network.client.createFestiCoreHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS `LocationLiveDependencies`. [io.festicore.core.location.client.LocationActivityProvider]는 app이 등록한다.
 * [routesApiKey]는 app이 `BuildConfig`(`FESTICORE_GOOGLE_ROUTES_API_KEY`)로 넘긴다. 비면 Routes API를 부르지 않는다.
 * Routes API는 앱 서버가 아니라 외부 호스트라 core/network의 HttpClient 생성 함수로 따로 만든다.
 */
fun coreLocationModule(routesApiKey: String): Module = module {
    single<LocationClient> { PlatformLocationClient(androidContext(), get()) }
    single<MapsClient> {
        val context = androidContext()
        val identity by lazy { appSigningIdentity(context) }
        val routes = GoogleRoutesClient(createFestiCoreHttpClient(baseUrl = GoogleRoutesClient.ENDPOINT), routesApiKey) { identity }
        IntentMapsClient(context, get(), routes)
    }
    factory<MapAppClient> { PackageManagerMapAppClient(androidContext()) }
}
