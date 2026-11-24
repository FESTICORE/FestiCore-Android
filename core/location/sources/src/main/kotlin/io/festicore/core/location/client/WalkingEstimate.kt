package io.festicore.core.location.client

import io.festicore.core.location.entity.LocationCoordinate
import io.festicore.core.location.entity.WalkingDuration

/**
 * 직선거리 도보 어림(사용자 결정, 설계 R6 쟁점 F4). iOS는 Apple 지도 도보 ETA를 쓰므로 값이 다를 수 있다.
 * 거리(하버사인, [NearestPlace.distanceMeters]) × [DETOUR_FACTOR] ÷ [WALKING_METERS_PER_MINUTE].
 * 분 반올림(남는 초 올림 · 최소 1분)은 [WalkingDuration.minutes]가 iOS와 같게 한다.
 */
internal object WalkingEstimate {
    /**
     * 직선 대비 실제 보행 경로 길이(우회 계수). 도시 도로망의 경로/직선 비율(circuity)은 대체로 1.2~1.4이며
     * (Levinson & El-Geneidy, "The minimum circuity frontier and the journey to work", 2009) 그 중간값을 쓴다.
     */
    const val DETOUR_FACTOR = 1.3

    /** 보행 속도 80m/분(약 4.8km/h). 일본 부동산 표시 공정경쟁규약의 "도보 1분 = 80m" 기준과 같다. */
    const val WALKING_METERS_PER_MINUTE = 80.0

    fun duration(origin: LocationCoordinate, destination: LocationCoordinate): WalkingDuration {
        val meters = NearestPlace.distanceMeters(origin, destination) * DETOUR_FACTOR
        return WalkingDuration(seconds = meters / WALKING_METERS_PER_MINUTE * 60)
    }
}
