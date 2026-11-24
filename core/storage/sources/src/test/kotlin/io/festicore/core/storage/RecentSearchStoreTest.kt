package io.festicore.core.storage

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.festicore.core.storage.shared.DataStoreRecentSearchStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RecentSearchStoreTest {
    private val stores = TestDataStores("recent_search_test")

    @After
    fun tearDown() = runTest { stores.close() }

    @Test
    fun `사용자별로 나뉘고 최신 10개만 남으며 공백을 지운 같은 검색어는 앞으로 옮긴다`() = runTest {
        val store = DataStoreRecentSearchStore(stores.open())
        for (number in 1..12) store.add("검색$number", "user-a")

        val searches = store.load("user-a")
        assertEquals(10, searches.size)
        assertEquals("검색12", searches.first())
        assertEquals("검색3", searches.last())
        assertTrue(store.load("user-b").isEmpty())
        assertTrue(store.load(null).isEmpty())

        assertEquals("검색11", store.add(" 검색11 ", "user-a").first())
        assertEquals(1, store.load("user-a").count { it == "검색11" })
        assertEquals(10, store.load("user-a").size)
    }

    @Test
    fun `공백뿐인 검색어는 저장하지 않고 기존 목록을 돌려준다`() = runTest {
        val store = DataStoreRecentSearchStore(stores.open())
        store.add("펜타포트", null)

        assertEquals(listOf("펜타포트"), store.add("   ", null))
        assertEquals(listOf("펜타포트"), store.load(null))
    }

    @Test
    fun `익명 기록을 지우고 모두 지울 수 있고 빈 사용자 ID는 익명으로 본다`() = runTest {
        val store = DataStoreRecentSearchStore(stores.open())

        assertEquals(listOf("펜타포트"), store.add("펜타포트", null))
        assertEquals(listOf("원더리벳", "펜타포트"), store.add("원더리벳", ""))
        assertEquals(listOf("원더리벳"), store.remove("펜타포트", null))
        store.clear(null)
        assertTrue(store.load("").isEmpty())
    }

    @Test
    fun `저장한 목록은 새 인스턴스에서도 읽히고 iOS와 같은 키 · 형식으로 저장된다`() = runTest {
        DataStoreRecentSearchStore(stores.open()).apply {
            add("두 번째 검색", "u1")
            add("기존 검색", "u1")
        }
        stores.close()

        val dataStore = stores.open()
        val raw = dataStore.data.first()[stringPreferencesKey("FestiCore.festivalRecentSearches.user.u1")]
        assertEquals("기존 검색\n두 번째 검색", raw)

        dataStore.edit { it[stringPreferencesKey("FestiCore.festivalRecentSearches.anonymous")] = "기존 익명\n\n두 번째 익명" }
        val store = DataStoreRecentSearchStore(dataStore)
        assertEquals(listOf("기존 검색", "두 번째 검색"), store.load("u1"))
        assertEquals(listOf("기존 익명", "두 번째 익명"), store.load(null))
        assertEquals(listOf("새 검색", "기존 익명", "두 번째 익명"), store.add("새 검색", null))
    }
}
