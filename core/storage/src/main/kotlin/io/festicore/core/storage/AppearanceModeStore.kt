package io.festicore.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 마이 > 화면 모드(Figma S13-J). 저장 값은 iOS와 같은 `system` · `light` · `dark`다(iOS `AppearanceMode`). */
enum class AppearanceMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        /** 값이 없거나 알 수 없는 값은 iOS처럼 다크로 본다(iOS `AppearanceMode(storedValue:)` · `appearanceMode` 기본값). */
        fun fromStoredValue(value: String?): AppearanceMode = entries.firstOrNull { it.storedValue == value } ?: DARK
    }
}

/** 화면 모드 선택 저장소(iOS `SharedReaderKey.appearanceMode`). 키 이름은 iOS와 같다. */
interface AppearanceModeStore {
    /** 지금 값과 이후 바뀐 값을 낸다. 저장된 값이 없으면 [AppearanceMode.DARK]다. */
    val appearanceMode: Flow<AppearanceMode>

    suspend fun saveAppearanceMode(mode: AppearanceMode)
}

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
