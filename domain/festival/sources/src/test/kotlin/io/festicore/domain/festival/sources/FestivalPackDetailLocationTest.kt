package io.festicore.domain.festival.sources

import io.festicore.core.network.FestiCoreJson
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.sources.packdetail.FestivalPackDetailUseCaseImpl
import io.festicore.domain.festival.sources.repository.FestivalPackChangesRepositoryImpl
import io.festicore.service.apiendpoint.festival.FestivalPackRequest
import io.festicore.service.pack.CachedFestivalPack as ServicePack
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `FestivalPackDetailLocationTests` 2건. */
class FestivalPackDetailLocationTest {
    @Test
    fun `축제 장소 좌표 · 셔틀 정류장 이름과 역 · 정류장 좌표를 읽는다`() = runTest {
        val detail = packDetail(
            """
            {"location":{"latitude":37.3850,"longitude":127.1230,"label":"원더리벳 공연장"},
             "shuttle":{"body":"정문 앞에서 출발","links":[],"stopName":"정문 셔틀 정류장","location":{"latitude":37.3861,"longitude":127.1241}},
             "transit":{"links":[],"stationName":"서현역","walkMinutes":12,"location":{"latitude":37.3836,"longitude":127.1268}}}
            """.trimIndent(),
        )

        assertEquals(FestivalPackDetail.Coordinate(37.3850, 127.1230), detail.directions.venueLocation)
        assertEquals("정문 셔틀 정류장", detail.directions.shuttle?.stopName)
        assertNull(detail.directions.shuttle?.stationName)
        assertEquals(FestivalPackDetail.Coordinate(37.3861, 127.1241), detail.directions.shuttle?.location)
        assertEquals("서현역", detail.directions.transit?.stationName)
        assertEquals(12, detail.directions.transit?.walkMinutes)
        assertEquals(FestivalPackDetail.Coordinate(37.3836, 127.1268), detail.directions.transit?.location)
    }

    @Test
    fun `형식이 다르거나 범위를 벗어난 좌표는 버리고 나머지 가는 길은 남긴다`() = runTest {
        val detail = packDetail(
            """
            {"lastTrainAt":"23:52","location":"올림픽공원",
             "shuttle":{"body":"정문 앞에서 출발","links":[],"stopName":"정문 셔틀 정류장","location":{"latitude":"37.38","longitude":127.12}},
             "transit":{"links":[],"stationName":"서현역","location":{"latitude":137.0,"longitude":127.1268}}}
            """.trimIndent(),
        )

        assertEquals("23:52", detail.directions.lastTrainAt)
        assertNull(detail.directions.venueLocation)
        assertEquals("정문 셔틀 정류장", detail.directions.shuttle?.stopName)
        assertNull(detail.directions.shuttle?.location)
        assertEquals("서현역", detail.directions.transit?.stationName)
        assertNull(detail.directions.transit?.location)
    }

    private suspend fun packDetail(directions: String): FestivalPackDetail {
        val json = """{"id":"fst_a","packVersion":1,"name":"팩 이름","directions":$directions}"""
        val service = MutablePackService(listOf(ServicePack("fst_a", 1, "e1", json.encodeToByteArray())))
        val useCase = FestivalPackDetailUseCaseImpl(
            packRepository = service.repository(),
            catalogRepository = FakeCatalogRepository(cached = listOf(catalogItem("fst_a", "원더리벳 2026", 1))),
            changesRepository = FestivalPackChangesRepositoryImpl(FestivalPackRequest(HttpClient(MockEngine { error("사용하지 않음") }))),
            localStore = FakePackLocalStore(),
            json = FestiCoreJson,
        )
        return checkNotNull(useCase.packDetail("fst_a"))
    }
}
