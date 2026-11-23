package io.festicore.domain.festival.sources.receivedpack

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.festicore.domain.festival.receivedpack.FestivalWiFiStatus

/** 기본 네트워크가 요금이 들지 않는 와이파이인지 본다(`ACCESS_NETWORK_STATE`, 설계 쟁점 M9). 확인할 수 없으면 false. iOS `FestivalWiFiStatusClient+Live`. */
internal class ConnectivityFestivalWiFiStatus(private val context: Context) : FestivalWiFiStatus {
    override suspend fun isOnWiFi(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = try {
            manager.getNetworkCapabilities(manager.activeNetwork)
        } catch (_: SecurityException) {
            null
        } ?: return false
        // 개인 핫스팟처럼 데이터 요금이 드는 연결에서는 받지 않는다(iOS `!isExpensive`).
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
