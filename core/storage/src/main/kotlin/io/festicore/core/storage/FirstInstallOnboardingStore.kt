package io.festicore.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/** 첫 설치 온보딩 완료 여부. iOS `FirstInstallOnboardingStore`. */
interface FirstInstallOnboardingStore {
    suspend fun hasCompleted(): Boolean

    suspend fun markCompleted()
}

internal class DataStoreFirstInstallOnboardingStore(private val dataStore: DataStore<Preferences>) : FirstInstallOnboardingStore {
    override suspend fun hasCompleted(): Boolean = dataStore.data.first()[COMPLETED] ?: false

    override suspend fun markCompleted() {
        dataStore.edit { it[COMPLETED] = true }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("first_install_onboarding_completed")
    }
}
