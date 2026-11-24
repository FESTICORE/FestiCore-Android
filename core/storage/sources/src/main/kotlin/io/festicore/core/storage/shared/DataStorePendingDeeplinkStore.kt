package io.festicore.core.storage.shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

internal class DataStorePendingDeeplinkStore(private val dataStore: DataStore<Preferences>) : PendingDeeplinkStore {
    override suspend fun save(encoded: String) {
        dataStore.edit { it[PENDING_DEEPLINK] = encoded }
    }

    override suspend fun consume(): String? {
        var consumed: String? = null
        // 읽기와 비우기를 한 edit 안에서 해 동시에 두 번 꺼내지 않는다.
        dataStore.edit { preferences ->
            consumed = preferences[PENDING_DEEPLINK]
            preferences.remove(PENDING_DEEPLINK)
        }
        return consumed
    }

    private companion object {
        val PENDING_DEEPLINK = stringPreferencesKey("FestiCore.pendingDeeplink")
    }
}
