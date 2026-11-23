package io.festicore.core.datastore

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PendingDeeplinkStoreTest {
    private val stores = TestDataStores("pending_deeplink_test")

    @After
    fun tearDown() = runTest { stores.close() }

    @Test
    fun `대기 딥링크는 다시 열어도 남고 꺼내면 비워진다`() = runTest {
        DataStorePendingDeeplinkStore(stores.open()).save("festicore://festival/fst_a")
        stores.close()

        val store = DataStorePendingDeeplinkStore(stores.open())
        assertEquals("festicore://festival/fst_a", store.consume())
        assertNull(store.consume())
    }

    @Test
    fun `최신 딥링크 하나만 남는다`() = runTest {
        val store = DataStorePendingDeeplinkStore(stores.open())
        store.save("festicore://festival")
        store.save("festicore://flag/flg_1")

        assertEquals("festicore://flag/flg_1", store.consume())
    }
}
