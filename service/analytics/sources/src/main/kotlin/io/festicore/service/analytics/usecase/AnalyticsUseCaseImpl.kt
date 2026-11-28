package io.festicore.service.analytics.usecase

import io.festicore.core.logger.FestiCoreLogCategory
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.service.analytics.event.AnalyticsEvent

/** iOS `AnalyticsUseCase.liveValue`. 각 SDK는 다른 SDK의 설정 여부와 독립적으로 동작한다. */
internal class AnalyticsUseCaseImpl(
    private val mixpanel: () -> MixpanelClient?,
    private val baseSuperProperties: Map<String, Any>,
    private val sentry: SentryClient? = null,
    private val firebase: () -> FirebaseAnalyticsClient? = { null },
    private val collectionEnabled: () -> Boolean = { true },
    private val updateCollection: (Boolean) -> Boolean = { false },
) : AnalyticsUseCase {
    override val isCollectionEnabled: Boolean get() = collectionEnabled()
    override fun setCollectionEnabled(enabled: Boolean): Boolean {
        val saved = updateCollection(enabled)
        if (saved && enabled) registerBaseProperties()
        return saved
    }

    override fun registerBaseProperties() {
        if (!isCollectionEnabled) return
        runCatching { mixpanel()?.registerSuperProperties(baseSuperProperties) }
        runCatching { firebase()?.registerBaseProperties(baseSuperProperties) }
    }

    override fun identify(userId: String, method: String?) {
        if (!isCollectionEnabled || userId.isEmpty()) return
        runCatching {
            mixpanel()?.let { client ->
                client.identify(userId)
                val superProperties = mutableMapOf<String, Any>("is_logged_in" to true)
                if (!method.isNullOrEmpty()) {
                    superProperties["login_provider"] = method
                    client.setPeopleProperty("provider", method)
                }
                client.registerSuperProperties(superProperties)
            }
        }
        runCatching { sentry?.identify(userId, method) }
        runCatching { firebase()?.identify(userId, method) }
    }

    override fun track(event: AnalyticsEvent) {
        if (!isCollectionEnabled) return
        val name = eventName(event)
        val properties = eventProperties(event)
        FestiCoreLogger.debug(analyticsDebugMessage(name, properties), FestiCoreLogCategory.APP)
        runCatching { mixpanel()?.track(name, properties) }
        runCatching { sentry?.track(name, properties) }
        runCatching { firebase()?.track(name, properties) }
    }

    override fun reset() {
        runCatching {
            mixpanel()?.let { client ->
                // 계정을 분리하고 공통 프로퍼티만 다시 등록한다(is_logged_in 없음 = 로그아웃).
                client.reset()
                client.registerSuperProperties(baseSuperProperties)
            }
        }
        runCatching { sentry?.reset() }
        runCatching { firebase()?.reset() }
        if (isCollectionEnabled) runCatching { firebase()?.registerBaseProperties(baseSuperProperties) }
    }
}

internal fun analyticsDebugMessage(name: String, properties: Map<String, Any>): String =
    "Analytics event — name: $name, property_keys: ${properties.keys.sorted()}"
