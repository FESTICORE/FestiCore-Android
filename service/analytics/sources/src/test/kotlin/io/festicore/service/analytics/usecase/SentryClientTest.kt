package io.festicore.service.analytics.usecase

import io.festicore.service.analytics.configuration.SentryConfiguration
import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.AnalyticsPackSyncResult
import io.festicore.service.analytics.event.AnalyticsTimetableAction
import io.festicore.service.analytics.event.FlagFollowData
import io.festicore.service.analytics.event.PackSyncData
import io.festicore.service.analytics.event.TimetableActionData
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.protocol.User
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentryClientTest {
    @After
    fun tearDown() {
        Sentry.close()
        SentryConfiguration.resetForTesting()
    }

    @Test
    fun `사용자 ID와 선택적 provider만 설정하고 reset으로 지운다`() {
        val users = mutableListOf<User?>()
        val client = SentryApiClient(enabled = { true }, setUser = { users += it })
        client.identify("", "google")
        assertTrue(users.isEmpty())
        client.identify("user", "google")
        client.identify("next", null)
        client.identify("third", "")
        client.reset()
        assertEquals("user", users[0]?.id)
        assertEquals(mapOf("login_provider" to "google"), users[0]?.data)
        assertNull(users[0]?.email)
        assertNull(users[0]?.username)
        assertNull(users[0]?.ipAddress)
        assertTrue(users[1]?.data.isNullOrEmpty())
        assertTrue(users[2]?.data.isNullOrEmpty())
        assertNull(users[3])
    }

    @Test
    fun `사용자 동작은 INFO breadcrumb와 문자열만 가진 카운트로 보낸다`() {
        val breadcrumbs = mutableListOf<Breadcrumb>()
        val counts = mutableListOf<Triple<String, Double, Map<String, Any>>>()
        val client = SentryApiClient(
            enabled = { true },
            addBreadcrumb = { breadcrumbs += it },
            count = { name, value, attributes -> counts += Triple(name, value, attributes) },
        )
        client.track("flag_follow", mapOf("is_following" to true, "screen" to "flag"))
        val breadcrumb = breadcrumbs.single()
        assertEquals(SentryLevel.INFO, breadcrumb.level)
        assertEquals("user.action", breadcrumb.category)
        assertEquals("flag_follow", breadcrumb.message)
        assertEquals(mapOf("is_following" to true, "screen" to "flag"), breadcrumb.data)
        assertEquals(Triple("user.action.count", 1.0, mapOf("event" to "flag_follow", "screen" to "flag")), counts.single())
    }

    @Test
    fun `설정이 없으면 활성 SDK여도 어떤 동작도 보내지 않는다`() {
        var calls = 0
        val client = SentryApiClient(
            enabled = { SentryConfiguration.isEnabled { true } },
            setUser = { calls++ },
            addBreadcrumb = { calls++ },
            count = { _, _, _ -> calls++ },
        )
        client.identify("user", "google")
        client.track("event", emptyMap())
        client.reset()
        assertEquals(0, calls)
    }

    @Test
    fun `Mixpanel 없이도 Sentry 사용자와 동작을 전달한다`() {
        val fake = RecordingSentryClient()
        val useCase = AnalyticsUseCaseImpl({ null }, emptyMap(), fake)
        useCase.identify("", "google")
        assertNull(fake.user)
        useCase.identify("user", "google")
        assertEquals("user", fake.user)
        useCase.track(AnalyticsEvent.FlagFollow(FlagFollowData("private-flag", true)))
        assertEquals(listOf("flag_follow" to mapOf("is_following" to true)), fake.events)
        useCase.reset()
        assertNull(fake.user)
        assertEquals(1, fake.resetCount)
    }

    @Test
    fun `ID는 Mixpanel과 Sentry 모두에서 제외한다`() {
        val fake = RecordingSentryClient()
        val mixpanel = RecordingMixpanelClient()
        val useCase = AnalyticsUseCaseImpl({ mixpanel }, emptyMap(), fake)
        val events = listOf(
            AnalyticsEvent.FlagFollow(FlagFollowData("private-flag", true)),
            AnalyticsEvent.TimetableAction(TimetableActionData(AnalyticsTimetableAction.SetOpen, "private-festival", "private-set")),
            AnalyticsEvent.PackSync(PackSyncData("private-festival", AnalyticsPackSyncResult.Updated)),
        )
        events.forEach(useCase::track)
        assertEquals(fake.events, mixpanel.calls.map { it.second })
        fake.events.forEach { (_, properties) ->
            assertFalse(properties.keys.any { it in setOf("flag_id", "festival_id", "set_id") })
            assertFalse(properties.values.any { it.toString().startsWith("private-") })
        }
    }

    @Test
    fun `debug 메시지에는 속성 값 없이 정렬된 키만 남긴다`() {
        val message = analyticsDebugMessage("screen_view", mapOf("screen" to "private-value", "referrer" to "another-private-value"))
        assertEquals("Analytics event — name: screen_view, property_keys: [referrer, screen]", message)
    }

    private class RecordingSentryClient : SentryClient {
        var user: String? = null
        var resetCount = 0
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun identify(userId: String, method: String?) {
            user = userId
        }
        override fun reset() {
            user = null
            resetCount++
        }
        override fun track(name: String, properties: Map<String, Any>) {
            events += name to properties
        }
    }
}
