package io.festicore.core.storage

import android.app.Application
import io.festicore.core.storage.shared.DataStoreLocationPermissionStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 위치 권한 거부 기록(설계 R6c 점검 R6-LOC-003). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocationPermissionStoreTest {
    private val stores = TestDataStores("location_permission_test")

    @After
    fun tearDown() = runTest { stores.close() }

    @Test
    fun `기록이 없으면 거부하지 않은 것이고 기록한 값은 다시 열어도 남는다`() = runTest {
        val store = DataStoreLocationPermissionStore(stores.open())
        assertFalse(store.isDenied())
        store.setDenied(true)
        stores.close()
        assertTrue(DataStoreLocationPermissionStore(stores.open()).isDenied())
    }
}
