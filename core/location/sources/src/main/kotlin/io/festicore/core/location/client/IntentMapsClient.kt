package io.festicore.core.location.client

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import io.festicore.core.location.entity.LocationClientError
import io.festicore.core.location.entity.LocationCoordinate
import io.festicore.core.location.entity.WalkingDuration
import io.festicore.core.location.entity.WalkingPlace
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * iOS `MapKitAppleMapsClient` 대응. 목적지는 서버 좌표, 없으면 플랫폼 `Geocoder`로 축제 장소 근처에서 이름을 찾고([NearestPlace]),
 * 도보 길찾기는 지도 URL 인텐트(`https://www.google.com/maps/dir/?api=1&travelmode=walking`)로 기본 지도 앱이 연다.
 * 기기 안에서 무료로 도보 경로를 계산할 방법이 없어 [walkingDuration]은 늘 [LocationClientError.RouteUnavailable]이다(설계 쟁점 F4).
 * 호출자는 iOS의 "계산 못 함" 경로대로 서버 `walkMinutes`를 보인다. 위치 값은 로그 · 분석에 넣지 않는다.
 */
internal class IntentMapsClient(
    private val context: Context,
    private val activityProvider: LocationActivityProvider,
) : MapsClient {
    override suspend fun walkingDuration(origin: LocationCoordinate, place: WalkingPlace): WalkingDuration =
        throw LocationClientError.RouteUnavailable

    override suspend fun openWalkingDirections(place: WalkingPlace): Boolean {
        val destination = try {
            coordinate(place)
        } catch (_: LocationClientError) {
            return false
        }
        val uri =
            Uri.parse("https://www.google.com/maps/dir/").buildUpon()
                .appendQueryParameter("api", "1")
                .appendQueryParameter("destination", "${destination.latitude},${destination.longitude}")
                .appendQueryParameter("travelmode", "walking")
                .build()
        return withContext(Dispatchers.Main.immediate) {
            val activity = activityProvider.currentActivity()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            try {
                (activity ?: context).startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }

    /** 서버 좌표가 있으면 그대로 쓰고, 없으면 이름을 축제 장소 근처에서 찾는다. 축제 장소 좌표도 없으면 다른 지역 같은 이름을 고를 수 있어 찾지 않는다. */
    private suspend fun coordinate(place: WalkingPlace): LocationCoordinate {
        place.coordinate?.takeIf { it.isValid }?.let { return it }
        val center = place.searchCenter?.takeIf { it.isValid } ?: throw LocationClientError.PlaceNotFound
        if (!Geocoder.isPresent()) throw LocationClientError.PlaceNotFound
        val candidates = search(place.name, center).map { LocationCoordinate(it.latitude, it.longitude) }
        return NearestPlace.match(candidates, center, SEARCH_RADIUS_METERS) ?: throw LocationClientError.PlaceNotFound
    }

    /** 축제 장소 중심 반경 크기 사각형 안에서 이름을 찾는다(iOS `MKLocalSearch.Request.region`). */
    private suspend fun search(name: String, center: LocationCoordinate): List<Address> {
        val latitudeDelta = SEARCH_RADIUS_METERS / METERS_PER_LATITUDE_DEGREE
        val longitudeDelta = SEARCH_RADIUS_METERS / (METERS_PER_LATITUDE_DEGREE * cos(Math.toRadians(center.latitude)))
        val geocoder = Geocoder(context, Locale.KOREA)
        val lowerLat = center.latitude - latitudeDelta
        val lowerLon = center.longitude - longitudeDelta
        val upperLat = center.latitude + latitudeDelta
        val upperLon = center.longitude + longitudeDelta
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(
                        name,
                        MAX_RESULTS,
                        lowerLat,
                        lowerLon,
                        upperLat,
                        upperLon,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (continuation.isActive) continuation.resume(addresses)
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(emptyList())
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(name, MAX_RESULTS, lowerLat, lowerLon, upperLat, upperLon).orEmpty()
                }
            }
        } catch (_: IOException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private companion object {
        /** 이름으로 찾을 때 축제 장소에서 이 거리 안의 후보만 목적지로 쓴다(iOS `MapKitRouting.searchRadius`). */
        const val SEARCH_RADIUS_METERS = 5_000.0
        const val METERS_PER_LATITUDE_DEGREE = 111_320.0
        const val MAX_RESULTS = 10
    }
}
