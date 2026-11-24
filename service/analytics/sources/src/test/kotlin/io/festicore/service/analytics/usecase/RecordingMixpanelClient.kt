package io.festicore.service.analytics.usecase

/** Mixpanel 호출을 순서대로 기록하는 가짜 sink. */
internal class RecordingMixpanelClient : MixpanelClient {
    val calls = mutableListOf<Pair<String, Any?>>()

    override fun registerSuperProperties(properties: Map<String, Any>) {
        calls += "registerSuperProperties" to properties
    }

    override fun identify(distinctId: String) {
        calls += "identify" to distinctId
    }

    override fun setPeopleProperty(name: String, value: Any) {
        calls += "people.set" to (name to value)
    }

    override fun track(event: String, properties: Map<String, Any>) {
        calls += "track" to (event to properties)
    }

    override fun reset() {
        calls += "reset" to null
    }
}
