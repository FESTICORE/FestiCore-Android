package io.festicore.service.analytics.event

/** iOS `AnalyticsPackSyncResult`. */
enum class AnalyticsPackSyncResult(val rawValue: String) {
    Updated("updated"),
    NotModified("not_modified"),
    Failed("failed"),
}

/** iOS `PackSyncData`. */
data class PackSyncData(
    val festivalId: String,
    val result: AnalyticsPackSyncResult,
)
