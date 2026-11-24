package io.festicore.core.storage.shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

internal class DataStoreFestivalPackLocalStore(private val dataStore: DataStore<Preferences>) : FestivalPackLocalStore {
    override suspend fun checklistJson(festivalId: String): String? = dataStore.data.first()[checklistKey(festivalId)]

    override suspend fun saveChecklistJson(festivalId: String, json: String) {
        dataStore.edit { it[checklistKey(festivalId)] = json }
    }

    override suspend fun autoDownloadOnWiFi(): Boolean = dataStore.data.first()[AUTO_DOWNLOAD] ?: true

    override suspend fun setAutoDownloadOnWiFi(isOn: Boolean) {
        dataStore.edit { it[AUTO_DOWNLOAD] = isOn }
    }

    private fun checklistKey(festivalId: String) = stringPreferencesKey("FestiCore.pack.checklist.$festivalId")

    private companion object {
        val AUTO_DOWNLOAD = booleanPreferencesKey("FestiCore.pack.autoDownloadOnWiFi")
    }
}
