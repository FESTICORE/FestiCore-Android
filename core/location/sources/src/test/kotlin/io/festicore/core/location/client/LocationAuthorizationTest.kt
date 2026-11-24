package io.festicore.core.location.client

import io.festicore.core.location.entity.LocationAuthorization
import org.junit.Assert.assertEquals
import org.junit.Test

/** 위치 권한 상태. iOS `LocationAuthorizationTests`(CoreLocation 값 → 앱 상태)의 Android 권한 대응. */
class LocationAuthorizationTest {
    @Test
    fun `허용됐으면 이전 거부 기록과 관계없이 허용이다`() {
        assertEquals(LocationAuthorization.Authorized, locationAuthorization(granted = true, deniedBefore = false))
        assertEquals(LocationAuthorization.Authorized, locationAuthorization(granted = true, deniedBefore = true))
    }

    @Test
    fun `한 번 거부했으면 다시 묻지 않도록 거부다`() {
        assertEquals(LocationAuthorization.Denied, locationAuthorization(granted = false, deniedBefore = true))
    }

    @Test
    fun `묻지 않았으면 아직 정하지 않음이다`() {
        assertEquals(LocationAuthorization.NotDetermined, locationAuthorization(granted = false, deniedBefore = false))
    }

    @Test
    fun `정확 · 대략 위치를 함께 요청한다`() {
        assertEquals(
            listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"),
            PlatformLocationClient.PERMISSIONS.toList(),
        )
    }
}
