package io.festicore.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * 라이브 보드 설정. 마지막으로 고른 축제(iOS `FestivalSelectionStore`)와 팔로우 안내를 숨긴 축제(iOS `FestivalGuideVisibilityStore`).
 * 키 이름은 iOS `UserDefaults` 키와 같다.
 */
interface LiveBoardPreferenceStore {
    suspend fun selectedFestivalId(): String?

    suspend fun saveSelectedFestivalId(festivalId: String)

    suspend fun isFollowedFlagGuideHidden(festivalId: String): Boolean

    /** 빈 ID는 저장하지 않는다(iOS와 같음). */
    suspend fun hideFollowedFlagGuide(festivalId: String)
}

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
