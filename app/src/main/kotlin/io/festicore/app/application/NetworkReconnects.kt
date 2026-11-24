package io.festicore.app.application

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 기본 네트워크가 연결될 때마다 값을 낸다(등록 직후 이미 연결돼 있으면 한 번). 기수 상태 대기열 자동 재개에 쓴다(설계 쟁점 B4).
 * 연결 관리자를 얻지 못하면 아무 값도 내지 않는다.
 */
internal fun Context.networkReconnects(): Flow<Unit> = callbackFlow {
    val manager = getSystemService(ConnectivityManager::class.java)
    if (manager == null) {
        awaitClose()
        return@callbackFlow
    }
    val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(Unit)
            }
        }
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { manager.unregisterNetworkCallback(callback) }
}
