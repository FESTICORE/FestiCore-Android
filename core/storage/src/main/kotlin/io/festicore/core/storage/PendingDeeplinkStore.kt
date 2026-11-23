package io.festicore.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * 아직 열지 않은 딥링크(iOS `PendingDeeplinkStore` · `PendingDeeplinkSharedKey`). 최신 하나만 URL 문자열로 영속 저장한다.
 * 해석은 부르는 쪽(`FestiCoreDeeplinkParser`)이 한다. 키 이름은 iOS와 같다.
 */
interface PendingDeeplinkStore {
    /** 이전 대기 값을 덮어쓴다. */
    suspend fun save(encoded: String)

    /** 대기 값을 꺼내고 비운다. 없으면 `null`. */
    suspend fun consume(): String?
}

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
