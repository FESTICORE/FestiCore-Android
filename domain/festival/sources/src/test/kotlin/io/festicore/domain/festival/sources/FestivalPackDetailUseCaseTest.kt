package io.festicore.domain.festival.sources

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.NetworkError
import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.sources.model.dto.FestivalPackDocumentDto
import io.festicore.domain.festival.sources.model.mapper.toDomain
import io.festicore.domain.festival.sources.packdetail.FestivalPackDetailUseCaseImpl
import io.festicore.domain.festival.sources.repository.FestivalPackChangesRepositoryImpl
import io.festicore.service.apiendpoint.festival.FestivalRequest
import io.festicore.service.pack.CachedFestivalPack as ServicePack
import io.festicore.service.pack.PackSyncError
import io.festicore.service.pack.PackSyncResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `FestivalPackDetailUseCaseTests` 8개(같은 뜻) + stage 팩으로 라이브 보드 문서와 상세를 함께 읽는 확인 1건. */
class FestivalPackDetailUseCaseTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun useCase(
        service: MutablePackService,
        catalog: FakeCatalogRepository = FakeCatalogRepository(),
        store: FakePackLocalStore = FakePackLocalStore(),
        changesBody: String = "{}",
    ) = FestivalPackDetailUseCaseImpl(
        packRepository = service.repository(),
        catalogRepository = catalog,
        changesRepository = FestivalPackChangesRepositoryImpl(
            FestivalRequest(
                createFestiCoreHttpClient(
                    baseUrl = "https://festicore.test/",
                    engine = MockEngine { request ->
                        requests += request
                        respond(changesBody, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ),
            ),
        ),
        localStore = store,
        json = FestiCoreJson,
    )

    @Test
    fun `상세는 가는 길 · 준비물 · 반입 금지를 읽고 틀린 값은 버린다`() = runTest {
        val service = MutablePackService(listOf(detailPack(12)))
        val useCase = useCase(service, FakeCatalogRepository(cached = listOf(catalogItem("fst_a", "원더리벳 2026", 13))))

        val detail = checkNotNull(useCase.packDetail("fst_a"))

        assertEquals("원더리벳 2026", detail.pack.name)
        assertTrue(detail.pack.hasUpdate)
        assertEquals("23:40", detail.directions.lastShuttleAt)
        assertEquals("23:52", detail.directions.lastTrainAt)
        assertEquals(listOf("셔틀 예약하기"), detail.directions.shuttle?.links?.map { it.label })
        assertEquals("킨텍스역", detail.directions.transit?.stationName)
        // http 지도 링크와 빈 섹션은 버린다.
        assertEquals("https://map.naver.com/p/1", detail.directions.naverMapUrl)
        assertNull(detail.directions.kakaoMapUrl)
        assertNull(detail.directions.parking)
        assertEquals("앵콜 전에 움직이세요.", detail.directions.memo)
        assertEquals("주최측 공지", detail.directions.source?.name)
        assertEquals(Instant.parse("2026-10-16T05:00:00Z"), detail.directions.source?.verifiedAt)
        assertEquals(listOf("ticket", "earplug"), detail.checklist.map { it.id })
        assertEquals(true, detail.checklist.first().required)
        assertEquals(listOf("유리병", "캔"), detail.rules)
        assertNull(useCase.packDetail("fst_missing"))
    }

    @Test
    fun `업데이트는 팩을 맞추고 목록을 새로 받는다`() = runTest {
        val service = MutablePackService(listOf(detailPack(12))).apply { onSync = { listOf(detailPack(13)) } }
        val catalog =
            FakeCatalogRepository(cached = listOf(catalogItem("fst_a", "A", 12)), refreshed = listOf(catalogItem("fst_a", "A", 13)))

        val updated = useCase(service, catalog).updatePack("fst_a")

        assertEquals(listOf("fst_a"), service.synced)
        assertEquals(13, updated?.pack?.packVersion)
        assertEquals(false, updated?.pack?.hasUpdate)
    }

    @Test
    fun `서버가 바쁘면 잠시 후 다시 시도하게 한다`() = runTest {
        val service = MutablePackService(listOf(detailPack(12))).apply { syncResult = PackSyncResult.RetryAfter(2.seconds) }

        assertTrue(failure { useCase(service).updatePack("fst_a") } is FestivalPackError.RetryLater)
    }

    @Test
    fun `전송 실패는 오프라인으로 알린다`() = runTest {
        listOf(NetworkError.Offline(IOException("연결 없음")), PackSyncError.Timeout()).forEach { error ->
            val service = MutablePackService(listOf(detailPack(12))).apply { syncError = error }

            assertTrue(failure { useCase(service).updatePack("fst_a") } is FestivalPackError.Offline)
        }
    }

    @Test
    fun `그 밖의 동기화 오류는 팩 오류로 남긴다`() = runTest {
        val service = MutablePackService(listOf(detailPack(12))).apply { syncError = PackSyncError.InvalidMessage() }

        assertTrue(failure { useCase(service).updatePack("fst_a") } is FestivalPackError.InvalidPack)
    }

    @Test
    fun `팩을 지워도 준비물과 자동 받기 설정은 남는다`() = runTest {
        val service = MutablePackService(listOf(detailPack(12)))
        val useCase = useCase(service)
        val checklist = FestivalChecklist(
            checkedIds = setOf("ticket"),
            customItems = listOf(FestivalChecklist.CustomItem("custom-1", "선크림")),
            hiddenIds = setOf("earplug"),
        )

        useCase.saveChecklist(checklist, "fst_a")
        useCase.deletePack("fst_a")

        assertEquals(listOf("fst_a"), service.deleted)
        assertEquals(checklist, useCase.checklist("fst_a"))
        useCase.setAutoDownloadOnWiFi(false)
        assertFalse(useCase.autoDownloadOnWiFi())
    }

    @Test
    fun `준비물은 iOS와 같은 키로 저장하고 없거나 깨지면 빈 기록이다`() = runTest {
        val store = FakePackLocalStore()
        val useCase = useCase(MutablePackService(emptyList()), store = store)
        assertEquals(FestivalChecklist(), useCase.checklist("fst_a"))
        assertTrue(useCase.autoDownloadOnWiFi())

        useCase.saveChecklist(
            FestivalChecklist(checkedIds = setOf("a"), customItems = listOf(FestivalChecklist.CustomItem("c", "우비"))),
            "fst_a",
        )
        val saved = FestiCoreJson.parseToJsonElement(store.checklists.getValue("fst_a")).jsonObject
        assertEquals(setOf("checkedIDs", "customItems", "hiddenIDs"), saved.keys)
        assertEquals(FestivalChecklist(), useCase.checklist("fst_b"))

        store.checklists["fst_c"] = "not json"
        assertEquals(FestivalChecklist(), useCase.checklist("fst_c"))
    }

    @Test
    fun `변경 요약은 from 버전으로 인증 없이 요청한다`() = runTest {
        val body = """{"code":"SUCCESS","message":"OK","data":{"fromVersion":12,"toVersion":13,"sizeBytes":524288,"changedSets":2,""" +
            """"lastShuttleAt":{"before":"23:40","after":"23:20"},"lastTrainAt":null,"lastDepartures":null}}"""

        val changes = useCase(MutablePackService(emptyList()), changesBody = body).packChanges("fst_a", 12)

        assertEquals(
            FestivalPackChanges(12, 13, 524_288, 2, lastShuttleAt = FestivalPackChanges.ValueChange("23:40", "23:20"), lastTrainAt = null),
            changes,
        )
        val request = requests.single()
        assertEquals("https://festicore.test/api/v1/festivals/fst_a/pack/changes?from=12", request.url.toString())
        assertEquals(HttpMethod.Get, request.method)
        assertFalse(request.headers.contains(HttpHeaders.Authorization))
    }

    @Test
    fun `stage 팩은 상세와 라이브 보드 문서가 같은 JSON을 각자 읽는다`() = runTest {
        val json = FestiCoreJson.parseToJsonElement(resource("festival-detail-stage.json")).jsonObject.getValue("data").toString()
        val pack = ServicePack("fst_00e0f4f1d8065a93", 56, "e", json.encodeToByteArray())

        val detail = useCase(MutablePackService(listOf(pack))).packDetail("fst_00e0f4f1d8065a93")!!
        val board = FestiCoreJson.decodeFromString(FestivalPackDocumentDto.serializer(), json).toDomain().directions

        assertEquals(
            FestivalPackDocument.Directions(
                lastShuttleAt = "2026-11-22T22:20:00+09:00",
                lastTrainAt = "2026-11-22T22:32:00+09:00",
                shuttleStopName = "정문 셔틀 정류장",
                transitStationName = null,
                transitWalkMinutes = null,
            ),
            board,
        )
        assertEquals(board?.lastShuttleAt, detail.directions.lastShuttleAt)
        assertEquals("정문 셔틀 정류장", detail.directions.shuttle?.stopName)
        assertEquals(37.66469544695199, detail.directions.venueLocation?.latitude ?: 0.0, 0.0)
        assertEquals("https://map.kakao.com/link/search/KINTEX%20HALL%207%2F8%2F9%2F10", detail.directions.kakaoMapUrl)
        assertNotNull(detail.directions.memo)
        assertEquals(listOf("chk_dev_ticket"), detail.checklist.map { it.id })
        assertEquals(listOf("유리병", "캔"), detail.rules)
        assertEquals("공개 수집 카탈로그", detail.source?.name)
    }

    private fun resource(name: String) = checkNotNull(javaClass.getResource("/$name")).readText()

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    private fun detailPack(version: Int): ServicePack {
        val json = """
            {"id":"fst_a","packVersion":$version,"name":"팩 이름",
             "directions":{"lastShuttleAt":"23:40","lastTrainAt":"23:52",
               "shuttle":{"body":"대화역 4번 출구 ↔ 킨텍스 셔틀 존","links":[{"label":"셔틀 예약하기","url":"https://example.com/shuttle"},{"label":"","url":"https://x"}]},
               "transit":{"body":"걸어서 10분","links":[],"stationName":"킨텍스역","walkMinutes":10},
               "parking":{"body":"  ","links":[]},
               "memo":"앵콜 전에 움직이세요.",
               "mapLinks":{"naver":"https://map.naver.com/p/1","kakao":"http://map.kakao.com"},
               "source":{"name":"주최측 공지","verifiedAt":"2026-10-16T05:00:00Z"}},
             "checklist":[{"id":"ticket","label":"모바일 티켓","required":true},{"id":"earplug","label":"귀마개","required":false},{"id":"broken"}],
             "rules":["유리병","캔"]}
        """.trimIndent()
        return ServicePack("fst_a", version, "e$version", json.encodeToByteArray())
    }
}
