package io.festicore.core.storage

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** iOS `AppearanceModeSharedKeyTests` 중 기본값 · 알 수 없는 값. 이전 appStorage 키 이관은 Android에 없다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppearanceModeStoreTest {
    private val stores = TestDataStores("appearance_test")

    @After
    fun tearDown() = runTest { stores.close() }

    @Test
    fun `저장한 값이 없으면 iOS처럼 다크다`() = runTest {
        assertEquals(AppearanceMode.DARK, DataStoreAppearanceModeStore(stores.open()).appearanceMode.first())
    }

    @Test
    fun `알 수 없는 저장 값은 다크로 본다`() = runTest {
        val dataStore = stores.open()
        dataStore.edit { it[stringPreferencesKey("FestiCore.appearanceMode")] = "sepia" }

        assertEquals(AppearanceMode.DARK, DataStoreAppearanceModeStore(dataStore).appearanceMode.first())
    }

    @Test
    fun `고른 화면 모드는 iOS와 같은 키 · 값으로 남는다`() = runTest {
        val store = DataStoreAppearanceModeStore(stores.open())
        store.saveAppearanceMode(AppearanceMode.SYSTEM)
        stores.close()

        val dataStore = stores.open()
        assertEquals("system", dataStore.data.first()[stringPreferencesKey("FestiCore.appearanceMode")])
        assertEquals(AppearanceMode.SYSTEM, DataStoreAppearanceModeStore(dataStore).appearanceMode.first())
    }
}
