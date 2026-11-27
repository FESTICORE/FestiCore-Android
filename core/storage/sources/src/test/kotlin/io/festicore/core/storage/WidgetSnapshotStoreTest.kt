package io.festicore.core.storage

import android.app.Application
import io.festicore.core.storage.secure.ValueCipher
import io.festicore.core.storage.shared.DataStoreWidgetSnapshotStore
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetSnapshotStoreTest {
    private val stores = TestDataStores("widget_snapshot_test")
    private val cipher = object : ValueCipher {
        override fun encrypt(plain: ByteArray) = plain.reversedArray()
        override fun decrypt(data: ByteArray) = data.reversedArray()
    }

    @After fun close() = runTest { stores.close() }

    @Test fun `snapshot survives recreation and stale completion cannot overwrite invalidation`() = runTest {
        val store = DataStoreWidgetSnapshotStore(stores.open(), cipher)
        assertTrue(store.compareAndSet(0, "account-a"))
        assertTrue(store.compareAndSet(1, null, invalidate = true))
        assertFalse(store.compareAndSet(1, "late-a"))
        stores.close()
        val result = DataStoreWidgetSnapshotStore(stores.open(), cipher).read()
        assertEquals(2L, result.revision)
        assertEquals(1L, result.generation)
        assertNull(result.payload)
    }

    @Test fun `lost cipher is unavailable and next valid write repairs it`() = runTest {
        val dataStore = stores.open()
        val store = DataStoreWidgetSnapshotStore(dataStore, cipher)
        assertTrue(store.compareAndSet(0, "old"))
        val lostKey = object : ValueCipher {
            override fun encrypt(plain: ByteArray) = cipher.encrypt(plain)
            override fun decrypt(data: ByteArray): ByteArray = error("key unavailable")
        }
        assertTrue(DataStoreWidgetSnapshotStore(dataStore, lostKey).read().unavailable)
        assertTrue(store.compareAndSet(1, "repaired"))
        assertEquals("repaired", store.read().payload)
        assertFalse(store.read().unavailable)
    }
}
