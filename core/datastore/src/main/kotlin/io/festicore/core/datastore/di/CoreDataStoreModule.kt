package io.festicore.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.festicore.core.datastore.DataStoreFirstInstallOnboardingStore
import io.festicore.core.datastore.DataStoreSecureStorage
import io.festicore.core.datastore.FirstInstallOnboardingStore
import io.festicore.core.datastore.KeystoreValueCipher
import io.festicore.core.datastore.SecureStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_onboarding")
private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "festicore_secure")

val coreDataStoreModule = module {
    single<FirstInstallOnboardingStore> { DataStoreFirstInstallOnboardingStore(androidContext().onboardingDataStore) }
    single<SecureStorage> { DataStoreSecureStorage(androidContext().secureDataStore, KeystoreValueCipher()) }
}
