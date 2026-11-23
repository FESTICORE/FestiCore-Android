package io.festicore.service.apiendpoint.notice

import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.service.api.base.BearerAuthorizationKey
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `NoticeRequestTests` 2건(같은 이름). 인증 표시 없음 · 본문 없음 · 한 번만 보낸다(재시도 없음). */
class NoticeRequestTest {
    private val requests = mutableListOf<HttpRequestData>()

    private val api = NoticeRequest(
        createFestiCoreHttpClient(
            baseUrl = "https://festicore.test/",
            engine = MockEngine { request ->
                requests += request
                respond(
                    """{"code":"SUCCESS","message":"OK","data":{}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ),
    )

    @Test
    fun publicList() = runTest {
        api.list<JsonElement>()

        assertPublicGet("https://festicore.test/api/v1/notices")
    }

    @Test
    fun publicDetail() = runTest {
        api.detail<JsonElement>("2c75c8de-517d-4611-9c51-b4ff804dd597")

        assertPublicGet("https://festicore.test/api/v1/notices/2c75c8de-517d-4611-9c51-b4ff804dd597")
    }

    private fun assertPublicGet(url: String) {
        val request = requests.single()
        assertEquals(url, request.url.toString())
        assertEquals(HttpMethod.Get, request.method)
        assertTrue(request.body is OutgoingContent.NoContent)
        assertNull(request.attributes.getOrNull(BearerAuthorizationKey))
    }
}
