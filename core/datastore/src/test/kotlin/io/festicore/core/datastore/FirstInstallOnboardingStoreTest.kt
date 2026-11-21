package io.festicore.core.datastore

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirstInstallOnboardingStoreTest {
    private val stores = TestDataStores("onboarding_test")

    @After
    fun tearDown() = runTest { stores.close() }

    @Test
    fun `처음에는 미완료이고 완료를 저장하면 새 인스턴스에서도 완료다`() = runTest {
        val store = DataStoreFirstInstallOnboardingStore(stores.open())
        assertFalse(store.hasCompleted())

        store.markCompleted()
        assertTrue(store.hasCompleted())

        stores.close()
        assertTrue(DataStoreFirstInstallOnboardingStore(stores.open()).hasCompleted())
    }
}
