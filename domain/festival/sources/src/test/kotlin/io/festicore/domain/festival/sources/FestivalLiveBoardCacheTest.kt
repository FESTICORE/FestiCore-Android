package io.festicore.domain.festival.sources

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.service.apiendpoint.festival.FestivalRequest
import io.festicore.service.festivalcatalog.FestivalCatalogCache
import io.festicore.service.festivalcatalog.FestivalImageUrlResolver
import io.festicore.service.festivalcatalog.NetworkFestivalCatalogService
import io.festicore.service.festivalcatalog.NetworkFestivalLiveBoardService
import io.festicore.service.festivalcatalog.NetworkFestivalOfficialNewsService
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 존 지도 첫 화면용 마지막 라이브 상태(설계 E3): 있음 · 없음 · 세션 변경 · 캐시 초기화 · 전체 필드 매핑. */
class FestivalLiveBoardCacheTest {
    private var sessionId: String? = "session-1"
    private var liveRequests = 0
    private var notFound = false
    private val received = Instant.parse("2026-11-22T00:00:05Z")

    private val api = FestivalRequest(
        createFestiCoreHttpClient(
            baseUrl = "https://example.test/",
            engine = MockEngine { request ->
                val path = request.url.encodedPath
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    path.endsWith("/live") && notFound ->
                        respond("""{"code":"FESTIVAL_NOT_FOUND","data":null,"message":"x"}""", HttpStatusCode.NotFound, json)

                    path.endsWith("/live") -> {
                        liveRequests += 1
                        respond(LIVE, headers = json)
                    }

                    else -> respond(FLAGS, headers = json)
                }
            },
        ),
    )
    private val liveBoardService = NetworkFestivalLiveBoardService(
        api = api,
        deviceId = { "00000000-0000-4000-8000-000000000001" },
        currentSessionId = { sessionId },
        now = { received },
    )
    private val repository = FestivalLiveBoardRepositoryImpl(
        liveBoardService,
        NetworkFestivalOfficialNewsService(api),
        NetworkFestivalCatalogService(
            api,
            FestivalCatalogCache(File("unused.json"), FestiCoreJson),
            FestivalImageUrlResolver("https://example.test/"),
        ),
    )

    @Test
    fun `조회한 적이 없으면 없다`() = runTest {
        assertNull(repository.cachedLiveBoard("fst_a"))
        assertEquals(0, liveRequests)
    }

    @Test
    fun `마지막 조회 결과를 새 조회와 같은 매핑으로 돌려준다`() = runTest {
        val fresh = repository.liveBoard("fst_a")

        val cached = repository.cachedLiveBoard("fst_a")

        assertEquals(fresh, cached)
        assertEquals(1, liveRequests)
        val followed = requireNotNull(cached).followedFlags.single()
        assertEquals("가짜 깃발", followed.name)
        assertEquals("https://example.test/img/a.webp", followed.thumbnailUrl)
        assertEquals(listOf("slam"), followed.tags)
        assertEquals("zon_1", cached.crew?.members?.single()?.zoneId)
        assertEquals(received, cached.receivedAt)
        assertEquals(Instant.parse("2026-11-22T00:00:00Z"), cached.serverAt)
    }

    @Test
    fun `세션이 바뀌면 이전 세션의 결과를 돌려주지 않는다`() = runTest {
        repository.liveBoard("fst_a")
        sessionId = "session-2"

        assertNull(repository.cachedLiveBoard("fst_a"))
        sessionId = null
        assertNull(repository.cachedLiveBoard("fst_a"))
    }

    @Test
    fun `미공개 응답을 받으면 캐시를 지운다`() = runTest {
        repository.liveBoard("fst_a")
        assertNotNull(repository.cachedLiveBoard("fst_a"))
        notFound = true

        runCatching { repository.liveBoard("fst_a") }

        assertNull(repository.cachedLiveBoard("fst_a"))
    }

    private companion object {
        const val LIVE =
            """{"code":"SUCCESS","message":"ok","data":{"festivalId":"fst_a","serverAt":"2026-11-22T00:00:00Z",
            "activeFlags":[{"flagId":"flg_a","status":"active","zoneId":"zon_1","activity":"slam","updatedAt":"2026-11-21T23:50:00Z"}],
            "me":{"following":["flg_a"],"crew":{"id":"crw_1","name":"가짜 크루","members":[{"userId":"usr_1","nickname":"가짜","state":"here","zoneId":"zon_1","updatedAt":"2026-11-21T23:55:00Z"}]}}}}"""
        const val FLAGS =
            """{"code":"SUCCESS","message":"ok","data":{"items":[{"id":"flg_a","name":"가짜 깃발","thumbUrl":"/img/a.webp","tags":["slam"]}]}}"""
    }
}
