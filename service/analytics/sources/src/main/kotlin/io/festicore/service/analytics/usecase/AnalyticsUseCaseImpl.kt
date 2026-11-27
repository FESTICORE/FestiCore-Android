package io.festicore.service.analytics.usecase

import io.festicore.core.logger.FestiCoreLogCategory
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.service.analytics.event.AnalyticsEvent

/** iOS `AnalyticsUseCase.liveValue`. 각 SDK는 다른 SDK의 설정 여부와 독립적으로 동작한다. */
internal class AnalyticsUseCaseImpl(
    private val mixpanel: () -> MixpanelClient?,
    private val baseSuperProperties: Map<String, Any>,
    private val sentry: SentryClient? = null,
) : AnalyticsUseCase {
    override fun registerBaseProperties() {
        mixpanel()?.registerSuperProperties(baseSuperProperties)
    }

    override fun identify(userId: String, method: String?) {
        if (userId.isEmpty()) return
        mixpanel()?.let { client ->
            client.identify(userId)
            val superProperties = mutableMapOf<String, Any>("is_logged_in" to true)
            if (!method.isNullOrEmpty()) {
                superProperties["login_provider"] = method
                client.setPeopleProperty("provider", method)
            }
            client.registerSuperProperties(superProperties)
        }
        sentry?.identify(userId, method)
    }

    override fun track(event: AnalyticsEvent) {
        val name = eventName(event)
        val properties = eventProperties(event)
        FestiCoreLogger.debug(analyticsDebugMessage(name, properties), FestiCoreLogCategory.APP)
        mixpanel()?.track(name, properties)
        sentry?.track(name, properties)
    }

    override fun reset() {
        mixpanel()?.let { client ->
            // 계정을 분리하고 공통 프로퍼티만 다시 등록한다(is_logged_in 없음 = 로그아웃).
            client.reset()
            client.registerSuperProperties(baseSuperProperties)
        }
        sentry?.reset()
    }
}

internal fun analyticsDebugMessage(name: String, properties: Map<String, Any>): String =
    "Analytics event — name: $name, property_keys: ${properties.keys.sorted()}"
