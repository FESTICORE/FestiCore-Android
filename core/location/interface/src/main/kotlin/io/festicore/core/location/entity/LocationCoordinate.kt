package io.festicore.core.location.entity

/** 위도 · 경도 한 지점. Feature가 android.location 타입을 알지 않도록 이 값만 주고받는다. iOS `LocationCoordinate`. */
data class LocationCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    /** 위도 -90..90, 경도 -180..180 안의 유한한 값인지 */
    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}
