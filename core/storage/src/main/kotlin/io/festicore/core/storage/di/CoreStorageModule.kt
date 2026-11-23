package io.festicore.core.storage.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.festicore.core.storage.AppearanceModeStore
import io.festicore.core.storage.DataStoreAppearanceModeStore
import io.festicore.core.storage.DataStoreFestivalPackLocalStore
import io.festicore.core.storage.DataStoreFirstInstallOnboardingStore
import io.festicore.core.storage.DataStoreLiveBoardPreferenceStore
import io.festicore.core.storage.DataStorePendingDeeplinkStore
import io.festicore.core.storage.DataStoreRecentSearchStore
import io.festicore.core.storage.DataStoreSecureStorage
import io.festicore.core.storage.FestivalPackLocalStore
import io.festicore.core.storage.FirstInstallOnboardingStore
import io.festicore.core.storage.KeystoreValueCipher
import io.festicore.core.storage.LiveBoardPreferenceStore
import io.festicore.core.storage.PendingDeeplinkStore
import io.festicore.core.storage.RecentSearchStore
import io.festicore.core.storage.SecureStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_onboarding")
private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_secure")
private val Context.recentSearchDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_recent_search")
private val Context.liveBoardDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_live_board")
private val Context.deeplinkDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_deeplink")
private val Context.festivalPackDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_festival_pack")
private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_appearance")

val coreStorageModule = module {
    single<FirstInstallOnboardingStore> { DataStoreFirstInstallOnboardingStore(androidContext().onboardingDataStore) }
    single<SecureStorage> { DataStoreSecureStorage(androidContext().secureDataStore, KeystoreValueCipher()) }
    single<RecentSearchStore> { DataStoreRecentSearchStore(androidContext().recentSearchDataStore) }
    single<LiveBoardPreferenceStore> { DataStoreLiveBoardPreferenceStore(androidContext().liveBoardDataStore) }
    single<PendingDeeplinkStore> { DataStorePendingDeeplinkStore(androidContext().deeplinkDataStore) }
    single<FestivalPackLocalStore> { DataStoreFestivalPackLocalStore(androidContext().festivalPackDataStore) }
    single<AppearanceModeStore> { DataStoreAppearanceModeStore(androidContext().appearanceDataStore) }
}
