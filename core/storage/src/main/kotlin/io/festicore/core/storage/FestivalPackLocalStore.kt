package io.festicore.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * 받은 팩의 기기 설정. 축제별 준비물 기록(JSON 문자열, 해석은 domain이 한다)과 와이파이 자동 받기.
 * 팩을 지워도 남는다. 키 이름은 iOS `FestivalPackLocalStore`의 `UserDefaults` 키와 같다.
 */
interface FestivalPackLocalStore {
    /** 저장한 준비물 JSON. 없으면 null. */
    suspend fun checklistJson(festivalId: String): String?

    suspend fun saveChecklistJson(festivalId: String, json: String)

    /** 처음에는 켜져 있다(iOS와 같음). */
    suspend fun autoDownloadOnWiFi(): Boolean

    suspend fun setAutoDownloadOnWiFi(isOn: Boolean)
}

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
