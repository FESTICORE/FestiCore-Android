package io.festicore.core.storage.shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

internal class DataStoreLiveBoardPreferenceStore(private val dataStore: DataStore<Preferences>) : LiveBoardPreferenceStore {
    override suspend fun selectedFestivalId(): String? = dataStore.data.first()[SELECTED_FESTIVAL]

    override suspend fun saveSelectedFestivalId(festivalId: String) {
        dataStore.edit { it[SELECTED_FESTIVAL] = festivalId }
    }

    override suspend fun isFollowedFlagGuideHidden(festivalId: String): Boolean =
        dataStore.data.first()[HIDDEN_FOLLOWED_FLAG_GUIDES]?.contains(festivalId) == true

    override suspend fun hideFollowedFlagGuide(festivalId: String) {
        if (festivalId.isEmpty()) return
        dataStore.edit { it[HIDDEN_FOLLOWED_FLAG_GUIDES] = it[HIDDEN_FOLLOWED_FLAG_GUIDES].orEmpty() + festivalId }
    }

    private companion object {
        val SELECTED_FESTIVAL = stringPreferencesKey("FestiCore.selectedFestivalID")
        val HIDDEN_FOLLOWED_FLAG_GUIDES = stringSetPreferencesKey("FestiCore.hiddenFollowedFlagGuides")
    }
}
