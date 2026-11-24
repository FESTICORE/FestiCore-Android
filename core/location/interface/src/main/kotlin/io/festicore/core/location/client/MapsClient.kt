package io.festicore.core.location.client

import io.festicore.core.location.entity.LocationCoordinate
import io.festicore.core.location.entity.WalkingDuration
import io.festicore.core.location.entity.WalkingPlace

/**
 * 목적지를 찾고 도보 시간을 계산하거나 지도 앱에서 도보 길찾기를 여는 경계. iOS `AppleMapsClient`.
 * Android에는 Apple 지도가 없어 구현은 Google Routes API로 도보 · 자동차 시간을 구하고(사용자 결정, 설계 쟁점 F4),
 * 키가 없거나 실패하면 도보만 직선거리로 어림한다. 길 안내는 지도 앱 인텐트로 연다.
 */
interface MapsClient {
    /**
     * 출발 지점에서 목적지까지 걸어서 걸리는 시간.
     * 찾거나 계산하지 못하면 [io.festicore.core.location.entity.LocationClientError]를 던진다.
     */
    suspend fun walkingDuration(origin: LocationCoordinate, place: WalkingPlace): WalkingDuration

    /**
     * 출발 지점에서 목적지까지 자동차로 걸리는 시간. iOS에는 없고 사용자 요청으로 더한다(설계 쟁점 F4).
     * 분 표시 규칙이 도보와 같아 같은 값 타입을 쓴다. 계산하지 못하면 [io.festicore.core.location.entity.LocationClientError]를 던진다.
     */
    suspend fun drivingDuration(origin: LocationCoordinate, place: WalkingPlace): WalkingDuration

    /** 지도 앱에서 목적지까지 도보 길찾기를 연다. 목적지를 찾지 못했거나 열지 못하면 false다. */
    suspend fun openWalkingDirections(place: WalkingPlace): Boolean
}
