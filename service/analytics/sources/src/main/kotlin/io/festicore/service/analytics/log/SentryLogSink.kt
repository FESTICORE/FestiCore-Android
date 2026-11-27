package io.festicore.service.analytics.log

import io.festicore.core.logger.FestiCoreLogCategory
import io.festicore.core.logger.FestiCoreLogEntry
import io.festicore.core.logger.FestiCoreLogLevel
import io.festicore.core.logger.FestiCoreLogSink
import io.festicore.service.analytics.configuration.SentryConfiguration
import io.sentry.Sentry
import io.sentry.SentryAttributes
import io.sentry.SentryLogLevel
import io.sentry.logger.SentryLogParameters

internal class SentryLogSink(
    private val enabled: () -> Boolean = { SentryConfiguration.isEnabled() },
    private val write: (SentryLogLevel, String, Map<String, Any>) -> Unit = { level, message, attributes ->
        Sentry.logger().log(level, SentryLogParameters.create(SentryAttributes.fromMap(attributes)), message)
    },
) : FestiCoreLogSink {
    override fun send(entry: FestiCoreLogEntry) {
        // NETWORK 메시지는 URL이나 응답 본문을 포함할 수 있어 외부로 보내지 않는다.
        if (!enabled() || entry.category == FestiCoreLogCategory.NETWORK || entry.level < FestiCoreLogLevel.INFO) return
        val level = when (entry.level) {
            FestiCoreLogLevel.DEBUG -> return
            FestiCoreLogLevel.INFO -> SentryLogLevel.INFO
            FestiCoreLogLevel.NOTICE -> SentryLogLevel.WARN
            FestiCoreLogLevel.ERROR -> SentryLogLevel.ERROR
            FestiCoreLogLevel.FAULT -> SentryLogLevel.FATAL
        }
        write(level, entry.message, mapOf("category" to entry.category.name.lowercase()))
    }
}
