package io.festicore.core.location.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 도보 소요 분. iOS `WalkingDurationTests`. */
class WalkingDurationTest {
    @Test
    fun `남는 초는 1분으로 올리고 최소 1분으로 보인다`() {
        val cases = listOf(0.0 to 1, 1.0 to 1, 59.0 to 1, 60.0 to 1, 61.0 to 2, 599.0 to 10, 600.0 to 10, 601.0 to 11)
        cases.forEach { (seconds, minutes) -> assertEquals("$seconds", minutes, WalkingDuration(seconds).minutes) }
    }

    @Test
    fun `잘못된 소요 시간은 분으로 바꾸지 않는다`() {
        listOf(-1.0, Double.POSITIVE_INFINITY, Double.NaN, Double.MAX_VALUE).forEach { assertNull("$it", WalkingDuration(it).minutes) }
    }

    @Test
    fun `범위 밖이거나 유한하지 않은 좌표는 쓰지 않는다`() {
        assertEquals(true, LocationCoordinate(37.385, 127.123).isValid)
        assertEquals(false, LocationCoordinate(120.0, 127.0).isValid)
        assertEquals(false, LocationCoordinate(37.0, 200.0).isValid)
        assertEquals(false, LocationCoordinate(Double.NaN, 127.0).isValid)
    }
}
