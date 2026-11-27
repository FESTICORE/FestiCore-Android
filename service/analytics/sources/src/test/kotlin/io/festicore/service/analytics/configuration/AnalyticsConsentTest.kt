package io.festicore.service.analytics.configuration

import io.festicore.core.storage.shared.AnalyticsConsentStore
import io.festicore.service.analytics.usecase.MixpanelClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AnalyticsConsentTest {
    @Test fun `Stage debug만 상세 로그를 허용하고 모든 Prod는 최소 로그만 남긴다`() {
        assertEquals(io.festicore.core.logger.FestiCoreLogLevel.DEBUG, FestiCoreAnalyticsConfiguration.minimumLogLevel(true, true))
        assertEquals(io.festicore.core.logger.FestiCoreLogLevel.INFO, FestiCoreAnalyticsConfiguration.minimumLogLevel(false, true))
        assertEquals(io.festicore.core.logger.FestiCoreLogLevel.NOTICE, FestiCoreAnalyticsConfiguration.minimumLogLevel(true, false))
        assertEquals(io.festicore.core.logger.FestiCoreLogLevel.NOTICE, FestiCoreAnalyticsConfiguration.minimumLogLevel(false, false))
    }

    private class Store(
        var value: Boolean = false,
        var saves: Boolean = true,
    ) : AnalyticsConsentStore {
        override fun isEnabled() = value
        override fun setEnabled(enabled: Boolean): Boolean {
            if (saves) value = enabled
            return saves
        }
    }

    @After fun cleanup() {
        MixpanelConfiguration.mainInstance = null
        FestiCoreAnalyticsConfiguration.configure(RuntimeEnvironment.getApplication(), "")
    }

    @Test fun `최초 거부와 동의 저장 재시작 철회를 적용한다`() {
        val store = Store()
        val calls = mutableListOf<Boolean>()
        MixpanelConfiguration.mainInstance = object : MixpanelClient {
            override fun setCollectionEnabled(enabled: Boolean) {
                calls += enabled
            }
            override fun registerSuperProperties(properties: Map<String, Any>) = Unit
            override fun identify(distinctId: String) = Unit
            override fun setPeopleProperty(name: String, value: Any) = Unit
            override fun track(event: String, properties: Map<String, Any>) = Unit
            override fun reset() = Unit
        }
        val context = RuntimeEnvironment.getApplication()
        FestiCoreAnalyticsConfiguration.configure(context, "", consentStore = store)
        assertFalse(FestiCoreAnalyticsConfiguration.collectionEnabled)
        assertTrue(FestiCoreAnalyticsConfiguration.setCollectionEnabled(true))
        assertTrue(store.value)
        FestiCoreAnalyticsConfiguration.configure(context, "", consentStore = store)
        assertTrue(FestiCoreAnalyticsConfiguration.collectionEnabled)
        assertTrue(FestiCoreAnalyticsConfiguration.setCollectionEnabled(false))
        assertFalse(FestiCoreAnalyticsConfiguration.collectionEnabled)
        assertEquals(listOf(false, true, true, false), calls)
    }

    @Test fun `동의 저장 실패면 시작하지 않고 철회 저장 실패라도 현재 수집을 멈춘다`() {
        val store = Store(saves = false)
        FestiCoreAnalyticsConfiguration.configure(RuntimeEnvironment.getApplication(), "", consentStore = store)
        assertFalse(FestiCoreAnalyticsConfiguration.setCollectionEnabled(true))
        assertFalse(FestiCoreAnalyticsConfiguration.collectionEnabled)
        store.saves = true
        assertTrue(FestiCoreAnalyticsConfiguration.setCollectionEnabled(true))
        store.saves = false
        assertFalse(FestiCoreAnalyticsConfiguration.setCollectionEnabled(false))
        assertFalse(FestiCoreAnalyticsConfiguration.collectionEnabled)
    }
}
