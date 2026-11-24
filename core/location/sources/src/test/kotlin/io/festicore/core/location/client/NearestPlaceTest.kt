package io.festicore.core.location.client

import io.festicore.core.location.entity.LocationCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 이름 검색 목적지 고르기. iOS `NearestPlaceTests`. */
class NearestPlaceTest {
    /** 서현역 근처 축제 장소 */
    private val venue = LocationCoordinate(37.3850, 127.1230)

    /** 장소에서 약 400m */
    private val nearStation = LocationCoordinate(37.3836, 127.1268)

    /** 장소에서 약 1.5km */
    private val fartherStation = LocationCoordinate(37.3990, 127.1200)

    /** 같은 이름의 다른 도시 역(부산) */
    private val otherCityStation = LocationCoordinate(35.1151, 129.0422)

    @Test
    fun `축제 장소에서 가장 가까운 후보를 고른다`() {
        assertEquals(nearStation, NearestPlace.match(listOf(fartherStation, nearStation), venue, 5_000.0))
    }

    @Test
    fun `검색 반경보다 먼 같은 이름의 장소는 목적지로 쓰지 않는다`() {
        assertNull(NearestPlace.match(listOf(otherCityStation), venue, 5_000.0))
    }

    @Test
    fun `기준 위치가 잘못됐으면 어떤 후보도 목적지로 쓰지 않는다`() {
        assertNull(NearestPlace.match(listOf(nearStation), LocationCoordinate(Double.NaN, 127.0), 5_000.0))
    }

    @Test
    fun `범위를 벗어난 좌표 후보는 버린다`() {
        assertEquals(nearStation, NearestPlace.match(listOf(LocationCoordinate(120.0, 127.0), nearStation), venue, 5_000.0))
    }

    @Test
    fun `거리는 iOS 설명의 약 400m · 1_5km와 맞는다`() {
        assertEquals(400.0, NearestPlace.distanceMeters(venue, nearStation), 50.0)
        assertEquals(1_500.0, NearestPlace.distanceMeters(venue, fartherStation), 150.0)
    }
}
