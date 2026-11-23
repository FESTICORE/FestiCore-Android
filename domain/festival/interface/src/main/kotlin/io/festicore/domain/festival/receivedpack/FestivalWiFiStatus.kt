package io.festicore.domain.festival.receivedpack

/** 지금 와이파이로 연결되어 있는지. iOS `FestivalWiFiStatusClient`. */
fun interface FestivalWiFiStatus {
    suspend fun isOnWiFi(): Boolean
}
