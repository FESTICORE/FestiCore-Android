package io.festicore.core.storage

import io.festicore.core.storage.shared.SharedPreferencesAnalyticsConsentStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AnalyticsConsentStoreTest {
    @Test fun `기본 거부이며 새 저장소 인스턴스에도 동의와 철회를 유지한다`() {
        val context = RuntimeEnvironment.getApplication()
        val store = SharedPreferencesAnalyticsConsentStore(context)
        assertFalse(store.isEnabled())
        assertTrue(store.setEnabled(true))
        assertTrue(SharedPreferencesAnalyticsConsentStore(context).isEnabled())
        assertTrue(store.setEnabled(false))
        assertFalse(SharedPreferencesAnalyticsConsentStore(context).isEnabled())
    }
}
