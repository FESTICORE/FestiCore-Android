package io.festicore.core.location.client

import io.festicore.core.location.entity.LocationAuthorization
import io.festicore.core.location.entity.LocationCoordinate

/** 위치 권한과 현재 위치를 읽는 경계. android.location · 권한 요청은 구현(core/location/sources)에만 둔다. iOS `LocationClient`. */
interface LocationClient {
    /** 지금 권한 상태. 권한 대화상자를 띄우지 않는다. */
    suspend fun authorization(): LocationAuthorization

    /** 사용 중 위치 권한을 요청하고 사용자가 고른 결과를 돌려준다. 이미 정해졌으면 묻지 않고 그 상태를 돌려준다. */
    suspend fun requestWhenInUseAuthorization(): LocationAuthorization

    /** 현재 위치 한 번. 권한이 없거나 위치를 잡지 못하면 [io.festicore.core.location.entity.LocationClientError]를 던진다. */
    suspend fun currentLocation(): LocationCoordinate
}
