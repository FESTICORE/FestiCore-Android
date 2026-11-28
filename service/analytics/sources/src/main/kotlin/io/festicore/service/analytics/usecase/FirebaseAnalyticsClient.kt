package io.festicore.service.analytics.usecase

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

internal interface FirebaseAnalyticsClient {
    fun registerBaseProperties(properties: Map<String, Any>)
    fun identify(userId: String, method: String?)
    fun track(name: String, properties: Map<String, Any>)
    fun reset()
    fun setCollectionEnabled(enabled: Boolean)
}

internal class FirebaseAnalyticsApiClient(private val api: FirebaseAnalytics) : FirebaseAnalyticsClient {
    override fun registerBaseProperties(properties: Map<String, Any>) = api.setDefaultEventParameters(firebaseParameters(properties))
    override fun identify(userId: String, method: String?) {
        api.setUserId(userId)
        api.setUserProperty("login_provider", method)
    }
    override fun track(name: String, properties: Map<String, Any>) {
        val parameters = if (name ==
            "screen_view"
        ) {
            properties.mapKeys { if (it.key == "screen") FirebaseAnalytics.Param.SCREEN_NAME else it.key }
        } else {
            properties
        }
        api.logEvent(name, firebaseParameters(parameters))
    }
    override fun reset() {
        api.setUserId(null)
        api.setUserProperty("login_provider", null)
        api.resetAnalyticsData()
    }
    override fun setCollectionEnabled(enabled: Boolean) {
        api.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to
                    if (enabled) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            ),
        )
        api.setAnalyticsCollectionEnabled(enabled)
    }
    companion object {
        fun create(context: Context): FirebaseAnalyticsClient? =
            if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalyticsApiClient(FirebaseAnalytics.getInstance(context))
    }
}

/** Firebase는 Boolean 매개변수를 받지 않는다. 허용된 문자열·숫자만 전달한다. */
internal fun firebaseParameters(properties: Map<String, Any>): Bundle = Bundle().apply {
    properties.forEach { (key, value) ->
        when (value) {
            is String -> putString(key, value)
            is Boolean -> putLong(key, if (value) 1L else 0L)
            is Long -> putLong(key, value)
            is Int -> putLong(key, value.toLong())
            is Double -> putDouble(key, value)
        }
    }
}
