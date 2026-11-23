package io.festicore.app.application

import io.festicore.core.storage.AppearanceMode as StoredAppearanceMode
import io.festicore.core.storage.AppearanceModeStore
import io.festicore.feature.profile.appearance.AppearanceMode
import io.festicore.feature.profile.appearance.AppearanceModeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 마이 > 화면 모드가 쓰는 저장소. feature는 `core`를 의존하지 않으므로 app이 [AppearanceModeStore]를 저장 값(`system` · `light` · `dark`)으로 잇는다. */
internal class StoredAppearanceModeSettings(private val store: AppearanceModeStore) : AppearanceModeSettings {
    override val appearanceMode: Flow<AppearanceMode> =
        store.appearanceMode.map { AppearanceMode.fromStoredValue(it.storedValue) }

    override suspend fun saveAppearanceMode(mode: AppearanceMode) {
        store.saveAppearanceMode(StoredAppearanceMode.fromStoredValue(mode.storedValue))
    }
}
