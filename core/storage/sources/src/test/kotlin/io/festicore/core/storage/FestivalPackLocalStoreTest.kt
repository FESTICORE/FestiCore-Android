package io.festicore.core.storage

import android.app.Application
import io.festicore.core.storage.shared.DataStoreFestivalPackLocalStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** iOS `FestivalPackDetailUseCaseTests.localStoreRoundTripsChecklistInUserDefaults`의 저장 부분. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FestivalPackLocalStoreTest {
    private val stores = TestDataStores("festival_pack_test")

    @After
    fun tearDown() = runTest { stores.close() }

    @Test
    fun `준비물은 축제마다 따로 남고 자동 받기는 처음에 켜져 있다`() = runTest {
        val store = DataStoreFestivalPackLocalStore(stores.open())
        assertNull(store.checklistJson("fst_a"))
        assertTrue(store.autoDownloadOnWiFi())

        store.saveChecklistJson("fst_a", """{"checkedIDs":["a"]}""")
        store.setAutoDownloadOnWiFi(false)
        stores.close()

        val reopened = DataStoreFestivalPackLocalStore(stores.open())
        assertEquals("""{"checkedIDs":["a"]}""", reopened.checklistJson("fst_a"))
        assertNull(reopened.checklistJson("fst_b"))
        assertFalse(reopened.autoDownloadOnWiFi())
    }
}
