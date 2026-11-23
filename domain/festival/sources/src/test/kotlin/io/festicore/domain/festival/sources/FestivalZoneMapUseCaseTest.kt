package io.festicore.domain.festival.sources
import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.domain.festival.catalog.FestivalCatalogRepository
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalCatalogPage
import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.entity.FestivalZoneMap
import io.festicore.domain.festival.sources.pack.FestivalPackUseCaseImpl
import io.festicore.domain.festival.sources.repository.FestivalPackRepositoryImpl
import io.festicore.domain.festival.sources.repository.FestivalZoneMapRepositoryImpl
import io.festicore.domain.festival.sources.zonemap.FestivalZoneMapUseCaseImpl
import io.festicore.service.apiendpoint.festival.FestivalRequest
import io.festicore.service.pack.CachedFestivalPack as ServicePack
import io.festicore.service.pack.PackDownloadEvent as ServiceEvent
import io.festicore.service.pack.PackSyncResult as ServiceResult
import io.festicore.service.pack.PackSyncService
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `FestivalZoneMapUseCaseTests` 3건 + 팩 업데이트(설계 E5). */
class FestivalZoneMapUseCaseTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var versionBody =
        """{"code":"SUCCESS","data":{"packVersion":9,"packUpdatedAt":"2026-11-08T00:00:00Z","etag":"\"cdm7-v9\"",""" +
            """"mapVersion":"zm-new"}}"""
    private val api = FestivalRequest(
        createFestiCoreHttpClient(
            baseUrl = "https://example.test/",
            engine = MockEngine { request ->
                requests += request
                respond(versionBody, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
    )
    private val packService = FakeZoneMapPackService(listOf(zoneMapPack()))
    private val packRepository = FestivalPackRepositoryImpl(packService, FestiCoreJson)
    private val useCase = FestivalZoneMapUseCaseImpl(packRepository, FestivalZoneMapRepositoryImpl(api))

    @Test
    fun cachedMapReadsZoneAreasStageHeadersAndNowPlaying() = runTest {
        val now = java.time.OffsetDateTime.parse("2026-11-20T13:30:00+09:00").toInstant()

        val map = useCase.cachedMap("fst_a", now)

        requireNotNull(map)
        assertEquals("zm-3f2a9c1d0b7e", map.version)
        assertEquals(0.655, map.aspect)
        assertFalse(map.flagsEnabled)
        assertTrue(map.hasLayout)
        // order 순서로 정렬하고 형식이 틀린 존은 버린다.
        assertEquals(listOf("zon_a", "zon_b"), map.zones.map { it.id })
        assertEquals(FestivalZoneMap.Area(0.02, 0.07, 0.44, 0.16), map.zones[0].area)
        assertEquals("무대 앞", map.zones[0].kind)
        assertNull(map.zones[1].area)
        assertEquals("TENT 무대", map.zones[1].landmark)
        // 팩에 없는 무대의 머리글은 버린다.
        assertEquals(listOf("RIVET STAGE", "WONDER STAGE"), map.stageHeaders.map { it.name })
        assertEquals("터치드", map.stageHeaders[0].nowPlaying)
        assertNull(map.stageHeaders[1].nowPlaying)
        assertNull(useCase.cachedMap("fst_missing", now))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun mapWithoutZoneAreasShowsList() {
        val map = FestivalZoneMap(
            festivalId = "fst_a",
            version = "zm-1",
            imageUrl = null,
            aspect = null,
            flagsEnabled = true,
            zones = listOf(FestivalZoneMap.Zone("zon_a", "A", kind = null, area = null, landmark = null)),
            stageHeaders = emptyList(),
        )

        assertFalse(map.hasLayout)
    }

    @Test
    fun isMapChangedComparesServerMapVersion() = runTest {
        val map = FestivalZoneMap("fst_a", "zm-old", null, 0.6, true, emptyList(), emptyList())

        assertTrue(useCase.isMapChanged(map))
        assertFalse(useCase.isMapChanged(map.copy(version = "zm-new")))
        val request = requests.first()
        assertEquals("/api/v1/festivals/fst_a/pack/version", request.url.encodedPath)
        assertEquals(HttpMethod.Get, request.method)
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `지도 버전 응답 code가 SUCCESS가 아니면 실패한다`() = runTest {
        versionBody = """{"code":"FAIL","message":"x","data":null}"""
        val map = FestivalZoneMap("fst_a", "zm-old", null, 0.6, true, emptyList(), emptyList())

        assertTrue(runCatching { useCase.isMapChanged(map) }.isFailure)
    }

    @Test
    fun `비율이 0 이하면 지도 비율이 없고 문서를 해석하지 못한 팩은 실패한다`() = runTest {
        packService.cached = listOf(
            ServicePack(
                "fst_a",
                1,
                "\"v1\"",
                """{"id":"fst_a","mapAspect":0,"zones":[{"id":"z","name":"Z","area":{"x":0,"y":0,"w":1,"h":1}}]}""".encodeToByteArray(),
            ),
            ServicePack("fst_b", 1, "\"v1\"", """{"id":"fst_b","days":"oops"}""".encodeToByteArray()),
        )

        val map = useCase.cachedMap("fst_a", Instant.EPOCH)

        assertNull(map?.aspect)
        assertTrue(map?.flagsEnabled == true)
        assertTrue(runCatching { useCase.cachedMap("fst_b", Instant.EPOCH) }.exceptionOrNull() is FestivalPackError.InvalidPack)
    }

    @Test
    fun `팩 업데이트는 팩을 한 번 맞추고 목록을 새로 받는다`() = runTest {
        val catalog = CountingCatalogRepository()
        val packUseCase = FestivalPackUseCaseImpl(packRepository, catalog)
        packService.syncResult = ServiceResult.Updated(zoneMapPack(version = 10))

        val result = packUseCase.syncPack("fst_a")

        assertTrue(result is io.festicore.domain.festival.entity.PackSyncResult.Updated)
        assertEquals(listOf("fst_a"), packService.synced)
        assertEquals(1, catalog.refreshCount)
        assertEquals(10, packRepository.state.value.packs.getValue("fst_a").packVersion)

        packService.syncResult = ServiceResult.RetryAfter(5.seconds)
        assertTrue(packUseCase.syncPack("fst_a") is io.festicore.domain.festival.entity.PackSyncResult.RetryAfter)
        assertEquals(1, catalog.refreshCount)
    }
}

private fun zoneMapPack(version: Int = 9): ServicePack {
    val json =
        """
        {"id":"fst_a","packVersion":$version,"flagsEnabled":false,"mapImageUrl":null,"mapAspect":0.655,"mapVersion":"zm-3f2a9c1d0b7e",
         "stages":[{"id":"stg_rivet","name":"RIVET STAGE","order":1},{"id":"stg_wonder","name":"WONDER STAGE","order":2}],
         "mapStages":[{"stageId":"stg_rivet","x":0.0254,"y":0.02,"w":0.4427},{"stageId":"stg_wonder","x":0.5318,"y":0.02,"w":0.4427},
                      {"stageId":"stg_missing","x":0.1,"y":0.1,"w":0.1},{"stageId":"stg_broken"}],
         "zones":[{"id":"zon_b","name":"TENT 앞","x":0.7,"y":0.5,"order":2,"area":null,"landmark":"TENT 무대"},
                  {"id":"zon_a","name":"리벳 스테이지 앞 왼쪽","kind":"무대 앞","x":0.24,"y":0.15,"order":1,"area":{"x":0.02,"y":0.07,"w":0.44,"h":0.16}},
                  {"name":"ID 없는 존"}],
         "sets":[{"id":"set_1","stageId":"stg_rivet","artist":"터치드","startsAt":"2026-11-20T13:00:00+09:00","endsAt":"2026-11-20T14:00:00+09:00"},
                 {"id":"set_2","stageId":"stg_wonder","artist":"LUCY","startsAt":"2026-11-20T14:00:00+09:00","endsAt":"2026-11-20T15:00:00+09:00"}]}
        """.trimIndent()
    return ServicePack("fst_a", version, "\"cdm7-v$version\"", json.encodeToByteArray())
}

private class FakeZoneMapPackService(var cached: List<ServicePack>) : PackSyncService {
    var syncResult: ServiceResult = ServiceResult.RetryAfter(1.seconds)
    val synced = mutableListOf<String>()

    override suspend fun cachedPack(festivalId: String) = cached.firstOrNull { it.festivalId == festivalId }

    override suspend fun cachedPacks() = cached

    override suspend fun sync(festivalId: String): ServiceResult {
        synced += festivalId
        return syncResult
    }

    override fun download(festivalId: String) = emptyFlow<ServiceEvent>()

    override suspend fun deletePack(festivalId: String) = Unit

    override fun updates(festivalId: String) = emptyFlow<ServiceResult>()
}

private class CountingCatalogRepository : FestivalCatalogRepository {
    var refreshCount = 0

    override suspend fun cachedFestivals(): List<FestivalCatalogItem> = emptyList()

    override suspend fun refreshFestivals(): List<FestivalCatalogItem> {
        refreshCount += 1
        return emptyList()
    }

    override suspend fun festivalPage(after: String?, limit: Int): FestivalCatalogPage = error("사용하지 않음")

    override fun imageUrl(value: String?): String? = null
}
