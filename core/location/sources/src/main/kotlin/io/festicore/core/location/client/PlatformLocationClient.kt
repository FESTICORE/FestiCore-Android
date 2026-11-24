package io.festicore.core.location.client

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.festicore.core.location.entity.LocationAuthorization
import io.festicore.core.location.entity.LocationClientError
import io.festicore.core.location.entity.LocationCoordinate
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 플랫폼 `LocationManager`와 androidx.core `LocationManagerCompat`으로 사용 중 위치 권한과 현재 위치를 읽는다. iOS `CoreLocationClient`.
 * 권한 대화상자는 지금 Activity의 결과 등록부로 띄우고, 동시에 여러 번 불려도 한 번만 띄운다. 권한 상태 · 위치 요청은 메인 스레드에서만 다룬다.
 */
internal class PlatformLocationClient(
    private val context: Context,
    private val activityProvider: LocationActivityProvider,
) : LocationClient {
    private var pendingRequest: CompletableDeferred<LocationAuthorization>? = null

    override suspend fun authorization(): LocationAuthorization = withContext(Dispatchers.Main.immediate) { currentAuthorization() }

    override suspend fun requestWhenInUseAuthorization(): LocationAuthorization = withContext(Dispatchers.Main.immediate) {
        val current = currentAuthorization()
        val activity = activityProvider.currentActivity() as? ComponentActivity
        // 띄울 화면이 없으면 결과가 오지 않으므로 묻지 않는다(iOS는 권한 문구가 없는 앱에서 묻지 않는다).
        if (current != LocationAuthorization.NotDetermined || activity == null) return@withContext current
        val request = pendingRequest ?: CompletableDeferred<LocationAuthorization>().also { deferred ->
            pendingRequest = deferred
            launchRequest(activity) { granted ->
                pendingRequest = null
                deferred.complete(if (granted) LocationAuthorization.Authorized else currentAuthorization())
            }
        }
        request.await()
    }

    override suspend fun currentLocation(): LocationCoordinate = withContext(Dispatchers.Main.immediate) {
        if (currentAuthorization() != LocationAuthorization.Authorized) throw LocationClientError.NotAuthorized
        val manager = context.getSystemService(LocationManager::class.java) ?: throw LocationClientError.LocationUnavailable
        val provider = provider(manager) ?: throw LocationClientError.LocationUnavailable
        val location = try {
            requestLocation(manager, provider)
        } catch (_: SecurityException) {
            throw LocationClientError.NotAuthorized
        }
        location?.let { LocationCoordinate(it.latitude, it.longitude) } ?: throw LocationClientError.LocationUnavailable
    }

    private fun currentAuthorization(): LocationAuthorization {
        val granted = PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        val activity = activityProvider.currentActivity()
        val deniedBefore = activity != null && PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        return locationAuthorization(granted = granted, deniedBefore = deniedBefore)
    }

    private fun launchRequest(activity: ComponentActivity, onResult: (Boolean) -> Unit) {
        val key = "festicore.location.permission.${UUID.randomUUID()}"
        var launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null
        launcher = activity.activityResultRegistry.register(key, ActivityResultContracts.RequestMultiplePermissions()) { result ->
            launcher?.unregister()
            onResult(result.values.any { it })
        }
        launcher.launch(PERMISSIONS)
    }

    /** 100m 정도면 충분해(iOS `kCLLocationAccuracyHundredMeters`) 통합 · 네트워크 위치를 먼저 쓰고, 없으면 GPS를 쓴다. */
    private fun provider(manager: LocationManager): String? {
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return candidates.firstOrNull { LocationManagerCompat.hasProvider(manager, it) && manager.isProviderEnabled(it) }
    }

    /** 현재 위치 한 번(iOS `requestLocation`). 30초 안에 못 잡으면 null이다(LocationManagerCompat 제한 시간). */
    @Suppress("MissingPermission") // 호출 전에 권한을 확인하고, 그 사이 회수되면 SecurityException을 받는다.
    private suspend fun requestLocation(
        manager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

/**
 * Android 권한 값을 앱 권한 상태로 바꾼다(iOS `LocationAuthorization(CLAuthorizationStatus)`).
 * 정확 · 대략 중 하나라도 허용이면 허용이다. 한 번 거부해 시스템이 설명을 권하는 상태면 iOS처럼 다시 묻지 않도록 거부로 본다.
 */
internal fun locationAuthorization(granted: Boolean, deniedBefore: Boolean): LocationAuthorization = when {
    granted -> LocationAuthorization.Authorized
    deniedBefore -> LocationAuthorization.Denied
    else -> LocationAuthorization.NotDetermined
}
