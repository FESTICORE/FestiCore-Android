package io.festicore.service.analytics.usecase

import io.festicore.core.logger.FestiCoreLogCategory
import io.festicore.core.logger.FestiCoreLogEntry
import io.festicore.core.logger.FestiCoreLogLevel
import io.festicore.service.analytics.configuration.SentryConfiguration
import io.festicore.service.analytics.log.SentryLogSink
import io.sentry.Sentry
import io.sentry.SentryLogEvent
import io.sentry.SentryLogLevel
import io.sentry.SentryMetricsEvent
import io.sentry.SentryOptions
import io.sentry.transport.NoOpTransport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentrySdkCallbackTest {
    @After
    fun tearDown() {
        Sentry.close()
        SentryConfiguration.resetForTesting()
    }

    @Test
    fun `수동 Logs와 Metrics는 자동 통합 없이 SDK callback에 도착한다`() {
        val logs = mutableListOf<SentryLogEvent>()
        val metrics = mutableListOf<SentryMetricsEvent>()
        Sentry.init { options: SentryOptions ->
            options.dsn = "https://publickey@example.com/123"
            options.integrations.clear()
            options.isEnableShutdownHook = false
            options.isSendClientReports = false
            options.setTransportFactory { _, _ -> NoOpTransport.getInstance() }
            options.logs.isEnabled = false
            options.metrics.isEnabled = false
            options.logs.setBeforeSend { log ->
                logs += log
                null
            }
            options.metrics.setBeforeSend { metric, _ ->
                metrics += metric
                null
            }
        }
        assertTrue(Sentry.isEnabled())
        SentryLogSink(enabled = Sentry::isEnabled)
            .send(FestiCoreLogEntry(FestiCoreLogLevel.NOTICE, FestiCoreLogCategory.APP, "safe test"))
        SentryApiClient(enabled = Sentry::isEnabled).track("flag_follow", mapOf("is_following" to true, "screen" to "flag"))
        assertEquals("safe test", logs.single().body)
        assertEquals(SentryLogLevel.WARN, logs.single().level)
        assertEquals("app", logs.single().attributes!!["category"]?.value)
        assertEquals("user.action.count", metrics.single().name)
        assertEquals(1.0, metrics.single().value!!, 0.0)
        assertEquals("flag_follow", metrics.single().attributes!!["event"]?.value)
        assertEquals("flag", metrics.single().attributes!!["screen"]?.value)
        assertFalse(metrics.single().attributes!!.containsKey("is_following"))
    }
}
