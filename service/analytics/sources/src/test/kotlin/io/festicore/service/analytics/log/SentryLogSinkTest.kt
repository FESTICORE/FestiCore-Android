package io.festicore.service.analytics.log

import io.festicore.core.logger.FestiCoreLogCategory
import io.festicore.core.logger.FestiCoreLogEntry
import io.festicore.core.logger.FestiCoreLogLevel
import io.festicore.service.analytics.configuration.SentryConfiguration
import io.sentry.SentryLogLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class SentryLogSinkTest {
    @Test
    fun `INFO 이상 로그의 심각도와 category만 전달하고 NETWORK는 제외한다`() {
        val received = mutableListOf<Triple<SentryLogLevel, String, Map<String, Any>>>()
        val sink = SentryLogSink(enabled = { true }) { level, message, attributes -> received += Triple(level, message, attributes) }
        FestiCoreLogLevel.entries.forEach { level ->
            sink.send(FestiCoreLogEntry(level, FestiCoreLogCategory.AUTH, "safe message"))
            sink.send(FestiCoreLogEntry(level, FestiCoreLogCategory.NETWORK, "excluded"))
        }
        assertEquals(
            listOf(SentryLogLevel.INFO, SentryLogLevel.WARN, SentryLogLevel.ERROR, SentryLogLevel.FATAL),
            received.map {
                it.first
            },
        )
        received.forEach {
            assertEquals("safe message", it.second)
            assertEquals(mapOf("category" to "auth"), it.third)
        }
    }

    @Test
    fun `설정이 없거나 SDK가 비활성이면 로그를 보내지 않는다`() {
        SentryConfiguration.resetForTesting()
        var calls = 0
        val sink = SentryLogSink(enabled = { SentryConfiguration.isEnabled { true } }) { _, _, _ -> calls++ }
        sink.send(FestiCoreLogEntry(FestiCoreLogLevel.ERROR, FestiCoreLogCategory.APP, "safe"))
        SentryLogSink(enabled = { false }) { _, _, _ -> calls++ }
            .send(FestiCoreLogEntry(FestiCoreLogLevel.ERROR, FestiCoreLogCategory.APP, "safe"))
        assertEquals(0, calls)
    }
}
