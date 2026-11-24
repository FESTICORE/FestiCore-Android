package io.festicore.core.network.endpoint

import io.festicore.core.network.client.createFestiCoreHttpClient
import io.festicore.core.network.error.NetworkError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FestiCoreUploadTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val part = FestiCoreUploadPart("photo", "photo.jpg", "image/jpeg", ByteArray(64 * 1024) { it.toByte() })

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = createFestiCoreHttpClient(
        baseUrl = "https://example.test/",
        engine = MockEngine { request -> handler(request) },
    )

    @Test
    fun `multipart 파트 하나를 POST하고 진행률을 0~1로 알린다`() = runTest {
        var sent: HttpRequestData? = null
        var bodySize = 0
        val progress = mutableListOf<Double>()
        val client = client { request ->
            sent = request
            // 본문을 읽어야 보낸 바이트가 집계된다(실제 엔진이 쓰는 것과 같다).
            bodySize = (request.body as OutgoingContent).toByteArray().size
            respond("""{"code":"SUCCESS","message":"OK","data":"ok"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val data: String = client.festiCoreUpload("api/v1/upload", part, onProgress = { progress += it })

        assertEquals("ok", data)
        val request = sent!!
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/upload", request.url.encodedPath)
        assertTrue(request.body.contentType!!.match(ContentType.MultiPart.FormData))
        assertTrue(bodySize > part.bytes.size)
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.all { it in 0.0..1.0 })
        assertEquals(progress.sorted(), progress)
        assertEquals(1.0, progress.last(), 0.0)
        assertEquals(UPLOAD_TIMEOUT_MILLIS, request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis)
    }

    @Test
    fun `업로드 아닌 요청에는 제한 시간을 걸지 않는다`() = runTest {
        var sent: HttpRequestData? = null
        val client = client { request ->
            sent = request
            respond("""{"code":"SUCCESS","message":"OK","data":"ok"}""", HttpStatusCode.OK, jsonHeaders)
        }

        client.festiCoreRequest<String> { url("api/v1/me") }

        assertNull(sent!!.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis)
    }

    @Test
    fun `제한 시간을 넘으면 Timeout이다`() = runTest {
        val client = client {
            delay(5_000)
            respond("""{"code":"SUCCESS","message":"OK","data":"ok"}""", HttpStatusCode.OK, jsonHeaders)
        }

        try {
            client.festiCoreUpload<String>("api/v1/upload", part, onProgress = {}, timeoutMillis = 100)
            fail("Timeout이 나야 한다")
        } catch (e: NetworkError.Timeout) {
            // 기대한 실패
        }
    }

    @Test
    fun `실패 응답은 festiCoreRequest와 같은 Http 오류다`() = runTest {
        val client = client {
            respond("""{"code":"PAYLOAD_TOO_LARGE","message":"too large"}""", HttpStatusCode.PayloadTooLarge, jsonHeaders)
        }

        try {
            client.festiCoreUpload<String>("api/v1/upload", part, onProgress = {})
            fail("Http 오류가 나야 한다")
        } catch (e: NetworkError.Http) {
            assertEquals(413, e.status)
            assertEquals("PAYLOAD_TOO_LARGE", e.code)
        }
    }
}
