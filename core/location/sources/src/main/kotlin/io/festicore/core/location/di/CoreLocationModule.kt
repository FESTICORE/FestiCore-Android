package io.festicore.core.location.di

import io.festicore.core.location.client.IntentMapsClient
import io.festicore.core.location.client.LocationClient
import io.festicore.core.location.client.MapAppClient
import io.festicore.core.location.client.MapsClient
import io.festicore.core.location.client.PackageManagerMapAppClient
import io.festicore.core.location.client.PlatformLocationClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** iOS `LocationLiveDependencies`. [io.festicore.core.location.client.LocationActivityProvider]는 app이 등록한다. */
val coreLocationModule = module {
    single<LocationClient> { PlatformLocationClient(androidContext(), get()) }
    single<MapsClient> { IntentMapsClient(androidContext(), get()) }
    factory<MapAppClient> { PackageManagerMapAppClient(androidContext()) }
}
