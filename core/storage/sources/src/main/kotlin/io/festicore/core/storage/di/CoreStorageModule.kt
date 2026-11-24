package io.festicore.core.storage.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.festicore.core.storage.onboarding.DataStoreFirstInstallOnboardingStore
import io.festicore.core.storage.onboarding.FirstInstallOnboardingStore
import io.festicore.core.storage.secure.DataStoreSecureStorage
import io.festicore.core.storage.secure.KeystoreValueCipher
import io.festicore.core.storage.secure.SecureStorage
import io.festicore.core.storage.shared.AppearanceModeStore
import io.festicore.core.storage.shared.DataStoreAppearanceModeStore
import io.festicore.core.storage.shared.DataStoreFestivalPackLocalStore
import io.festicore.core.storage.shared.DataStoreLiveBoardPreferenceStore
import io.festicore.core.storage.shared.DataStoreLocationPermissionStore
import io.festicore.core.storage.shared.DataStorePendingDeeplinkStore
import io.festicore.core.storage.shared.DataStoreRecentSearchStore
import io.festicore.core.storage.shared.FestivalPackLocalStore
import io.festicore.core.storage.shared.LiveBoardPreferenceStore
import io.festicore.core.storage.shared.LocationPermissionStore
import io.festicore.core.storage.shared.PendingDeeplinkStore
import io.festicore.core.storage.shared.RecentSearchStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_onboarding")
private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_secure")
private val Context.recentSearchDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_recent_search")
private val Context.liveBoardDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_live_board")
private val Context.deeplinkDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_deeplink")
private val Context.festivalPackDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_festival_pack")
private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_appearance")
private val Context.locationPermissionDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_location_permission")

val coreStorageModule = module {
    single<FirstInstallOnboardingStore> { DataStoreFirstInstallOnboardingStore(androidContext().onboardingDataStore) }
    single<SecureStorage> { DataStoreSecureStorage(androidContext().secureDataStore, KeystoreValueCipher()) }
    single<RecentSearchStore> { DataStoreRecentSearchStore(androidContext().recentSearchDataStore) }
    single<LiveBoardPreferenceStore> { DataStoreLiveBoardPreferenceStore(androidContext().liveBoardDataStore) }
    single<PendingDeeplinkStore> { DataStorePendingDeeplinkStore(androidContext().deeplinkDataStore) }
    single<FestivalPackLocalStore> { DataStoreFestivalPackLocalStore(androidContext().festivalPackDataStore) }
    single<AppearanceModeStore> { DataStoreAppearanceModeStore(androidContext().appearanceDataStore) }
    single<LocationPermissionStore> { DataStoreLocationPermissionStore(androidContext().locationPermissionDataStore) }
}
