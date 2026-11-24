package io.festicore.core.storage.shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DataStoreAppearanceModeStore(private val dataStore: DataStore<Preferences>) : AppearanceModeStore {
    override val appearanceMode: Flow<AppearanceMode> =
        dataStore.data.map { AppearanceMode.fromStoredValue(it[APPEARANCE_MODE]) }.distinctUntilChanged()

    override suspend fun saveAppearanceMode(mode: AppearanceMode) {
        dataStore.edit { it[APPEARANCE_MODE] = mode.storedValue }
    }

    private companion object {
        val APPEARANCE_MODE = stringPreferencesKey("FestiCore.appearanceMode")
    }
}
