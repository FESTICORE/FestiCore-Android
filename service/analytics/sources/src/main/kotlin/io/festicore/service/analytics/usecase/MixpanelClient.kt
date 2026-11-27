package io.festicore.service.analytics.usecase

import android.content.Context
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject

/** 이 모듈이 Mixpanel에 하는 호출만 모은 경계. 테스트는 가짜 구현으로 호출을 기록한다. */
internal interface MixpanelClient {
    fun registerSuperProperties(properties: Map<String, Any>)

    fun identify(distinctId: String)

    fun setPeopleProperty(name: String, value: Any)

    fun track(event: String, properties: Map<String, Any>)

    fun setCollectionEnabled(enabled: Boolean) = Unit

    fun reset()
}

internal class MixpanelApiClient(private val api: MixpanelAPI) : MixpanelClient {
    override fun registerSuperProperties(properties: Map<String, Any>) = api.registerSuperProperties(JSONObject(properties))

    override fun identify(distinctId: String) = api.identify(distinctId)

    override fun setPeopleProperty(name: String, value: Any) = api.people.set(name, value)

    override fun track(event: String, properties: Map<String, Any>) = api.track(event, JSONObject(properties))

    override fun setCollectionEnabled(enabled: Boolean) {
        if (enabled) {
            if (api.hasOptedOutTracking()) api.optInTracking()
        } else {
            api.optOutTracking()
        }
    }
    override fun reset() = api.reset()

    companion object {
        /** iOS `Mixpanel.initialize(token:trackAutomaticEvents: false)`. */
        fun create(context: Context, token: String): MixpanelClient =
            MixpanelApiClient(MixpanelAPI.getInstance(context, token, false).apply { setEnableLogging(false) })
    }
}
