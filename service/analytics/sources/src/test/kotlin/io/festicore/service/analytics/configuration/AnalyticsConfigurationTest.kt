package io.festicore.service.analytics.configuration

import android.content.Context
import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.AnalyticsScreen
import io.festicore.service.analytics.usecase.AnalyticsUseCaseImpl
import io.festicore.service.analytics.usecase.RecordingMixpanelClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** iOS `AnalyticsConfigurationTests`의 Mixpanel 설정에 대응한다. */
@RunWith(RobolectricTestRunner::class)
class AnalyticsConfigurationTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        MixpanelConfiguration.mainInstance = null
    }

    @Test
    fun `토큰은 공백이 아닐 때만 쓴다`() {
        assertNull(MixpanelConfiguration.token(""))
        assertNull(MixpanelConfiguration.token(" \n "))
        assertEquals("token", MixpanelConfiguration.token(" token "))
    }

    @Test
    fun `토큰이 없으면 Mixpanel을 만들지 않고 이벤트도 보내지 않는다`() {
        val created = mutableListOf<String>()
        listOf("", " \n ").forEach { raw ->
            MixpanelConfiguration.configure(context, raw) { _, token -> RecordingMixpanelClient().also { created += token } }
        }
        FestiCoreAnalyticsConfiguration.configure(context, "")

        assertEquals(emptyList<String>(), created)
        assertNull(MixpanelConfiguration.mainInstance)
        // 키 없는 빌드의 실제 구현 호출도 아무 데도 닿지 않는다(예외 없음).
        val useCase = AnalyticsUseCaseImpl({ MixpanelConfiguration.mainInstance }, mapOf("os_type" to "android"))
        useCase.registerBaseProperties()
        useCase.identify("user", "google")
        useCase.track(AnalyticsEvent.ScreenView(AnalyticsScreen.Login))
        useCase.reset()
        assertNull(MixpanelConfiguration.mainInstance)
    }

    @Test
    fun `토큰이 있으면 앞뒤 공백을 지운 값으로 한 번만 시작한다`() {
        val created = mutableListOf<String>()
        val client = RecordingMixpanelClient()
        repeat(2) {
            MixpanelConfiguration.configure(context, " token ") { _, token -> client.also { created += token } }
        }

        assertEquals(listOf("token"), created)
        assertSame(client, MixpanelConfiguration.mainInstance)
    }
}
