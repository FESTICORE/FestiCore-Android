package io.festicore.service.festivalcatalog

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.NetworkError
import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.service.api.FestivalApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FestivalCatalogServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val requests = mutableListOf<HttpRequestData>()
    private val cache by lazy { FestivalCatalogCache(File(folder.root, "festival-catalog.json"), FestiCoreJson) }

    /** [pages]: 요청한 after(첫 쪽은 null) → (축제 ID 목록, 다음 커서). 없는 커서는 500. */
    private fun service(pages: suspend (String?) -> Pair<List<String>, String?>?) = FestivalCatalogService(
        api = FestivalApi(
            createFestiCoreHttpClient(
                baseUrl = "https://example.test/",
                engine = MockEngine { request ->
                    requests += request
                    val page = pages(request.url.parameters["after"]) ?: return@MockEngine respondError(HttpStatusCode.InternalServerError)
                    respond(pageJson(page.first, page.second), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
        ),
        cache = cache,
        imageUrlResolver = FestivalImageUrlResolver("https://example.test/"),
    )

    private fun pageJson(ids: List<String>, next: String?): String {
        val items = ids.joinToString(",") { """{"id":"$it","name":"가짜 $it","packVersion":1}""" }
        val cursor = next?.let { "\"$it\"" } ?: "null"
        return """{"code":"SUCCESS","message":"ok","data":{"items":[$items],"nextCursor":$cursor}}"""
    }

    private suspend fun cachedIds() = cache.load().map { it.id }

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `쪽을 이어 받아 마지막 쪽에서 첫 쪽부터 모은 목록을 중복 없이 저장한다`() = runTest {
        val service = service {
            when (it) {
                null -> listOf("a", "b") to "c1"
                "c1" -> listOf("b", "c") to null
                else -> null
            }
        }

        val first = service.page(null, 10)
        val last = service.page("c1", 10)

        assertEquals("c1", first.nextCursor)
        assertEquals(listOf("b", "c"), last.items.map { it.id })
        assertNull(last.nextCursor)
        assertEquals(listOf("a", "b", "c"), cachedIds())
        assertEquals(listOf("10", "10"), requests.map { it.url.parameters["limit"] })
    }

    @Test
    fun `첫 쪽만 받으면 저장하지 않는다`() = runTest {
        cache.save(emptyList())
        val service = service { if (it == null) listOf("a") to "c1" else null }

        service.page(null, 10)

        assertTrue(cachedIds().isEmpty())
    }

    @Test
    fun `첫 쪽을 다시 받으면 모은 쪽을 새로 시작한다`() = runTest {
        var firstRun = true
        val service = service {
            when (it) {
                null -> (if (firstRun) listOf("a", "b") else listOf("a")) to "c1"
                "c1" -> listOf("d") to null
                else -> null
            }
        }

        service.page(null, 10)
        firstRun = false
        service.page(null, 10)
        service.page("c1", 10)

        assertEquals(listOf("a", "d"), cachedIds())
    }

    @Test
    fun `다음 커서가 보낸 커서와 같으면 RepeatedCursor이고 저장하지 않는다`() = runTest {
        val service = service { if (it == "c1") listOf("a") to "c1" else null }

        val error = failure { service.page("c1", 10) }

        assertTrue(error is FestivalCatalogServiceError.RepeatedCursor)
        assertTrue(cachedIds().isEmpty())
    }

    @Test
    fun `더 보기 진행 중 첫 쪽을 다시 받으면 늦은 옛 쪽은 모으지도 저장하지도 않는다`() = runTest {
        val staleStarted = CompletableDeferred<Unit>()
        val releaseStale = CompletableDeferred<Unit>()
        var firstPages = 0
        val service = service {
            when (it) {
                null -> if (++firstPages == 1) listOf("a", "b") to "c1" else listOf("x") to "c2"

                "c1" -> {
                    staleStarted.complete(Unit)
                    releaseStale.await()
                    listOf("c") to null
                }

                "c2" -> listOf("y") to null

                else -> null
            }
        }

        service.page(null, 10)
        val stale = async { service.page("c1", 10) }
        staleStarted.await()
        service.page(null, 10)
        releaseStale.complete(Unit)

        assertEquals(listOf("c"), stale.await().items.map { it.id })
        assertEquals(emptyList<String>(), cachedIds())

        service.page("c2", 10)
        assertEquals(listOf("x", "y"), cachedIds())
    }

    @Test
    fun `전체 갱신은 limit 50으로 마지막 쪽까지 받아 중복 없이 저장한다`() = runTest {
        val service = service {
            when (it) {
                null -> listOf("a", "b") to "c1"
                "c1" -> listOf("b", "c") to "c2"
                "c2" -> listOf("d") to null
                else -> null
            }
        }

        val items = service.refresh()

        assertEquals(listOf("a", "b", "c", "d"), items.map { it.id })
        assertEquals(listOf("a", "b", "c", "d"), cachedIds())
        assertEquals(listOf(null, "c1", "c2"), requests.map { it.url.parameters["after"] })
        assertTrue(requests.all { it.url.parameters["limit"] == "50" })
    }

    @Test
    fun `전체 갱신에서 커서가 반복되면 RepeatedCursor이다`() = runTest {
        val service = service {
            when (it) {
                null -> listOf("a") to "c1"
                "c1" -> listOf("b") to "c2"
                "c2" -> listOf("c") to "c1"
                else -> null
            }
        }

        val error = failure { service.refresh() }

        assertTrue(error is FestivalCatalogServiceError.RepeatedCursor)
        assertTrue(cachedIds().isEmpty())
    }

    @Test
    fun `전체 갱신이 100쪽 안에 끝나지 않으면 TooManyPages이다`() = runTest {
        val service = service { after -> listOf("x${after.orEmpty()}") to "c${requests.size}" }

        val error = failure { service.refresh() }

        assertTrue(error is FestivalCatalogServiceError.TooManyPages)
        assertEquals(FestivalCatalogService.MAX_PAGES, requests.size)
        assertTrue(cachedIds().isEmpty())
    }

    @Test
    fun `서버 실패는 저장된 목록을 지우지 않는다`() = runTest {
        val saved = listOf("a", "b")
        service { if (it == null) saved to null else null }.refresh()
        requests.clear()

        val error = failure { service { null }.refresh() }

        assertTrue(error is NetworkError.Http)
        assertEquals(saved, service { null }.cached().map { it.id })
    }
}
