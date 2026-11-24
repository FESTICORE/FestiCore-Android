package io.festicore.core.location.client

import io.festicore.core.location.entity.LocationCoordinate
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 이름 검색 후보 중 실제 목적지로 쓸 곳을 고른다. 같은 이름의 먼 역을 고르지 않게 한다. iOS `NearestPlace`. */
internal object NearestPlace {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /**
     * 기준 위치에서 [radiusMeters] 안의 가장 가까운 후보.
     * 잘못된 좌표는 버리고, 기준 위치가 잘못됐거나 쓸 후보가 없으면 null이다.
     */
    fun match(
        candidates: List<LocationCoordinate>,
        center: LocationCoordinate,
        radiusMeters: Double,
    ): LocationCoordinate? {
        if (!center.isValid) return null
        return candidates
            .filter { it.isValid }
            .map { it to distanceMeters(center, it) }
            .filter { (_, distance) -> distance <= radiusMeters }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    /** 두 지점의 대권 거리(하버사인). iOS `CLLocation.distance(from:)` 자리이며 5km 반경 판단에는 차이가 없다. */
    fun distanceMeters(from: LocationCoordinate, to: LocationCoordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }
}
