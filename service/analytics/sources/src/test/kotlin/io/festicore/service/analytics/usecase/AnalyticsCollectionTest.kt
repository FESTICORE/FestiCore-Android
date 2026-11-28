package io.festicore.service.analytics.usecase

import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.AnalyticsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnalyticsCollectionTest {
    private class FirebaseRecorder : FirebaseAnalyticsClient {
        val calls = mutableListOf<String>()
        override fun registerBaseProperties(properties: Map<String, Any>) {
            calls += "base"
        }
        override fun identify(userId: String, method: String?) {
            calls += "identify"
        }
        override fun track(name: String, properties: Map<String, Any>) {
            calls += name
        }
        override fun reset() {
            calls += "reset"
        }
        override fun setCollectionEnabled(enabled: Boolean) {
            calls += "enabled:$enabled"
        }
    }

    @Test fun `동의하지 않으면 이벤트와 사용자 식별을 세 SDK에 전달하지 않는다`() {
        val mixpanel = RecordingMixpanelClient()
        val firebase = FirebaseRecorder()
        val sentry = object : SentryClient {
            override fun identify(userId: String, method: String?) {
                error("must not identify")
            }
            override fun track(name: String, properties: Map<String, Any>) {
                error("must not track")
            }
            override fun reset() = Unit
        }
        val useCase = AnalyticsUseCaseImpl({ mixpanel }, emptyMap(), sentry, { firebase }, { false })
        useCase.registerBaseProperties()
        useCase.identify("fixture-user", "google")
        useCase.track(AnalyticsEvent.ScreenView(AnalyticsScreen.Login))
        assertTrue(mixpanel.calls.isEmpty())
        assertTrue(firebase.calls.isEmpty())
    }

    @Test fun `Mixpanel 실패에도 Firebase 이벤트 식별 초기화와 Sentry 호출을 계속한다`() {
        val firebase = FirebaseRecorder()
        val calls = mutableListOf<String>()
        val sentry = object : SentryClient {
            override fun identify(userId: String, method: String?) {
                calls += "identify"
            }
            override fun track(name: String, properties: Map<String, Any>) {
                calls += name
            }
            override fun reset() {
                calls += "reset"
            }
        }
        val useCase = AnalyticsUseCaseImpl({ error("SDK failure") }, emptyMap(), sentry, { firebase })
        useCase.registerBaseProperties()
        useCase.identify("fixture-user", "google")
        useCase.track(AnalyticsEvent.ScreenView(AnalyticsScreen.Login))
        useCase.reset()
        assertEquals(listOf("base", "identify", "screen_view", "reset", "base"), firebase.calls)
        assertEquals(listOf("identify", "screen_view", "reset"), calls)
    }

    @Test fun `Firebase 매개변수는 Boolean을 정수로 바꾸고 임의 객체를 제외한다`() {
        val bundle = firebaseParameters(mapOf("enabled" to true, "disabled" to false, "screen" to "login", "unsafe" to Any()))
        assertEquals(1L, bundle.getLong("enabled"))
        assertEquals(0L, bundle.getLong("disabled"))
        assertEquals("login", bundle.getString("screen"))
        assertFalse(bundle.containsKey("unsafe"))
    }
}
