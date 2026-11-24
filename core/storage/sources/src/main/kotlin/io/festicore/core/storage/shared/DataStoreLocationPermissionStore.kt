package io.festicore.core.storage.shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

internal class DataStoreLocationPermissionStore(private val dataStore: DataStore<Preferences>) : LocationPermissionStore {
    override suspend fun isDenied(): Boolean = dataStore.data.first()[LOCATION_PERMISSION_DENIED] ?: false

    override suspend fun setDenied(denied: Boolean) {
        dataStore.edit { it[LOCATION_PERMISSION_DENIED] = denied }
    }

    private companion object {
        val LOCATION_PERMISSION_DENIED = booleanPreferencesKey("FestiCore.location.permissionDenied")
    }
}
