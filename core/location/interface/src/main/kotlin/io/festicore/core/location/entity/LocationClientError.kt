package io.festicore.core.location.entity

/** 위치 · 지도 계산 실패. 호출자는 서버가 준 값으로 대신 보인다. iOS `LocationClientError`. */
sealed class LocationClientError(message: String) : Exception(message) {
    /** 사용 중 위치 권한이 없다. */
    data object NotAuthorized : LocationClientError("위치 권한 없음")

    /** 현재 위치를 잡지 못했다. */
    data object LocationUnavailable : LocationClientError("현재 위치 없음")

    /** 목적지 이름을 기준 위치 근처에서 찾지 못했다. */
    data object PlaceNotFound : LocationClientError("목적지 없음")

    /** 도보 경로를 계산하지 못했다. */
    data object RouteUnavailable : LocationClientError("도보 경로 없음")
}
