package io.festicore.service.analytics.usecase

import io.festicore.service.analytics.configuration.SentryConfiguration
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryAttributes
import io.sentry.SentryLevel
import io.sentry.metrics.SentryMetricsParameters
import io.sentry.protocol.User

internal interface SentryClient {
    fun identify(userId: String, method: String?)

    fun reset()

    fun track(name: String, properties: Map<String, Any>)
}

internal class SentryApiClient(
    private val enabled: () -> Boolean = { SentryConfiguration.isEnabled() },
    private val setUser: (User?) -> Unit = Sentry::setUser,
    private val addBreadcrumb: (Breadcrumb) -> Unit = Sentry::addBreadcrumb,
    private val count: (String, Double, Map<String, Any>) -> Unit = { name, value, attributes ->
        Sentry.metrics().count(name, value, null, SentryMetricsParameters.create(SentryAttributes.fromMap(attributes)))
    },
) : SentryClient {
    override fun identify(userId: String, method: String?) {
        if (!enabled() || userId.isEmpty()) return
        setUser(
            User().apply {
                id = userId
                if (!method.isNullOrEmpty()) data = mapOf("login_provider" to method)
            },
        )
    }

    override fun reset() {
        if (!enabled()) return
        setUser(null)
    }

    override fun track(name: String, properties: Map<String, Any>) {
        if (!enabled()) return
        addBreadcrumb(
            Breadcrumb().apply {
                level = SentryLevel.INFO
                category = "user.action"
                message = name
                properties.forEach { (key, value) -> setData(key, value) }
            },
        )
        // iOS와 같이 문자열 속성만 메트릭 차원에 포함한다(Boolean 제외).
        val attributes = properties.filterValues { it is String } + ("event" to name)
        count("user.action.count", 1.0, attributes)
    }
}
