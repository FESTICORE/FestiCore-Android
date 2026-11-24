package io.festicore.core.location.client

import android.content.ContextWrapper
import io.festicore.core.location.entity.LocationClientError
import io.festicore.core.location.entity.LocationCoordinate
import io.festicore.core.location.entity.WalkingPlace
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 직선거리 도보 어림. Routes API 키가 없거나 실패할 때의 대체 경로(사용자 결정, 설계 쟁점 F4). */
class WalkingEstimateTest {
    private val origin = LocationCoordinate(37.3900, 127.1200)
    private val station = LocationCoordinate(37.3836, 127.1268)

    @Test
    fun `직선거리에 우회 계수 1_3을 곱해 분당 80m로 나눈다`() {
        val meters = NearestPlace.distanceMeters(origin, station)
        val duration = WalkingEstimate.duration(origin, station)
        assertEquals(meters * 1.3 / 80.0 * 60, duration.seconds, 1e-6)
        // 약 935m → 1216m → 15.2분 → 16분(남는 초 올림, iOS 표시 규칙)
        assertEquals(16, duration.minutes)
    }

    @Test
    fun `같은 지점이면 최소 1분으로 보인다`() {
        assertEquals(1, WalkingEstimate.duration(origin, origin).minutes)
    }

    @Test
    fun `1km 직선은 1300m 보행으로 17분이다`() {
        // 위도 1도 ≈ 111.195km(평균 반지름 기준) → 1km ≈ 0.0089932도
        val north = LocationCoordinate(origin.latitude + 1_000.0 / 111_195.08, origin.longitude)
        assertEquals(1_000.0, NearestPlace.distanceMeters(origin, north), 1.0)
        assertEquals(17, WalkingEstimate.duration(origin, north).minutes)
    }

    @Test
    fun `목적지 좌표도 검색 기준도 없으면 찾지 못해 호출자가 서버 값을 쓴다`() = runTest {
        val client = IntentMapsClient(ContextWrapper(null), { null }, routesWithoutKey())
        val error = runCatching { client.walkingDuration(origin, WalkingPlace("서현역")) }.exceptionOrNull()
        assertEquals(LocationClientError.PlaceNotFound, error)
    }

    @Test
    fun `키가 없으면 서버 좌표까지 도보는 직선거리로 어림하고 자동차는 계산하지 않는다`() = runTest {
        val client = IntentMapsClient(ContextWrapper(null), { null }, routesWithoutKey())
        val place = WalkingPlace("서현역", coordinate = station)
        assertEquals(16, client.walkingDuration(origin, place).minutes)
        assertEquals(LocationClientError.RouteUnavailable, runCatching { client.drivingDuration(origin, place) }.exceptionOrNull())
    }

    private fun routesWithoutKey() = GoogleRoutesClient(HttpClient(MockEngine { error("호출하면 안 된다") }), apiKey = "") { null }
}
