package io.festicore.domain.festival.sources.model.dto

import io.festicore.core.network.FestiCoreJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FestivalPackDetailDtoTest {
    private val json = FestiCoreJson

    @Test
    fun `가는 길의 한 키 형식이 틀리면 그 키만 null이고 나머지는 남는다`() {
        val dto = json.decodeFromString(
            FestivalPackDetailDto.serializer(),
            """{"directions":{"lastTrainAt":"2026-11-23T23:30:00+09:00","memo":{"bad":1},"location":"wrong"}}""",
        )
        val directions = dto.directions
        assertNotNull(directions)
        assertEquals("2026-11-23T23:30:00+09:00", directions!!.lastTrainAt)
        assertNull(directions.memo)
    }

    @Test
    fun `좌표는 객체에서 숫자만 읽는다`() {
        val dto = json.decodeFromString(
            FestivalPackDetailDto.serializer(),
            """{"directions":{"location":{"latitude":37.5,"longitude":"127"}}}""",
        )
        val location = dto.directions?.location
        assertNotNull(location)
        assertEquals(37.5, location!!.latitude!!, 0.0)
        assertNull(location.longitude)
    }
}
