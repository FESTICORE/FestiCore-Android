package io.festicore.core.storage.shared

/**
 * 위치 권한 요청을 사용자가 거부했는지(설계 R6c 점검 R6-LOC-003). iOS는 `CLAuthorizationStatus.denied`를 시스템이 알려 주지만,
 * Android는 여러 번 거부해 시스템이 더 묻지 않는 영구 거부와 아직 묻지 않음을 구별하지 못해 앱이 기록한다.
 */
interface LocationPermissionStore {
    suspend fun isDenied(): Boolean

    suspend fun setDenied(denied: Boolean)
}
