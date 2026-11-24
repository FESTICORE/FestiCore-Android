package io.festicore.core.storage.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

internal class DataStoreFirstInstallOnboardingStore(private val dataStore: DataStore<Preferences>) : FirstInstallOnboardingStore {
    override suspend fun hasCompleted(): Boolean = dataStore.data.first()[COMPLETED] ?: false

    override suspend fun markCompleted() {
        dataStore.edit { it[COMPLETED] = true }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("first_install_onboarding_completed")
    }
}
