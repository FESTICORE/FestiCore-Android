package io.festicore.service.analytics.event

/** iOS `AnalyticsProvider`. Android는 Apple 로그인을 옮기지 않지만 계약은 iOS와 같게 둔다. */
enum class AnalyticsProvider(val rawValue: String) {
    Google("google"),
    Apple("apple"),
    ;

    companion object {
        /** iOS `AnalyticsProvider(rawValue:)`. 모르는 값이면 null. */
        fun fromRawValue(rawValue: String): AnalyticsProvider? = entries.firstOrNull { it.rawValue == rawValue }
    }
}
