package io.festicore.core.network.endpoint

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.client.createFestiCoreHttpClient
import io.festicore.core.network.error.NetworkError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FestiCoreRequestTest {
    @Serializable
    private data class Me(
        val id: Int,
        val nickname: String? = null,
    )

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private var lastRequest: HttpRequestData? = null

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = createFestiCoreHttpClient(
        baseUrl = "https://example.test/",
        engine = MockEngine { request ->
            lastRequest = request
            handler(request)
        },
    )

    private suspend inline fun <reified E : NetworkError> expectError(
        noinline handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): E {
        try {
            client(handler).festiCoreRequest<Me> { url("api/v1/me") }
        } catch (e: NetworkError) {
            assertTrue("기대 ${E::class.simpleName}, 실제 ${e::class.simpleName}", e is E)
            return e as E
        }
        fail("오류가 나지 않았습니다")
        error("unreachable")
    }

    @Test
    fun `성공 응답은 data를 돌려주고 기본 헤더와 baseUrl을 쓴다`() = runTest {
        val me = client {
            respond("""{"code":"SUCCESS","message":"ok","data":{"id":1,"nickname":"roy","extra":true}}""", headers = jsonHeaders)
        }.festiCoreRequest<Me> { url("api/v1/me") }

        assertEquals(Me(1, "roy"), me)
        val request = lastRequest!!
        assertEquals("https://example.test/api/v1/me", request.url.toString())
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
    }

    @Test
    fun `nullable 타입은 data가 null이어도 성공한다`() = runTest {
        val result = client {
            respond("""{"code":"SUCCESS","message":"ok","data":null}""", headers = jsonHeaders)
        }.festiCoreRequest<Me?> { url("api/v1/me") }

        assertNull(result)
    }

    @Test
    fun `code가 SUCCESS가 아니면 InvalidResponse`() = runTest {
        val error = expectError<NetworkError.InvalidResponse> {
            respond("""{"code":"VOTE_NOT_FOUND","message":"없음","data":null}""", headers = jsonHeaders)
        }

        assertEquals("VOTE_NOT_FOUND", error.code)
        assertEquals("없음", error.message)
    }

    @Test
    fun `401은 stage 오류 본문을 Http로 매핑한다`() = runTest {
        val error = expectError<NetworkError.Http> {
            respond(
                """{"code":"UNAUTHORIZED","data":null,"message":"인증이 필요합니다."}""",
                HttpStatusCode.Unauthorized,
                jsonHeaders,
            )
        }

        assertEquals(401, error.status)
        assertTrue(error.isUnauthorized)
        assertEquals("UNAUTHORIZED", error.code)
        assertEquals("인증이 필요합니다.", error.message)
    }

    @Test
    fun `JSON이 아닌 오류 본문은 code와 message 없이 Http`() = runTest {
        val error = expectError<NetworkError.Http> {
            respond("<html>Bad Gateway</html>", HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "text/html"))
        }

        assertEquals(502, error.status)
        assertNull(error.code)
        assertNull(error.message)
    }

    @Test
    fun `data 형식이 맞지 않으면 Decoding`() = runTest {
        expectError<NetworkError.Decoding> {
            respond("""{"code":"SUCCESS","message":"ok","data":{"id":"하나"}}""", headers = jsonHeaders)
        }
    }

    @Test
    fun `null을 허용하지 않는 타입에 data가 없으면 Decoding`() = runTest {
        expectError<NetworkError.Decoding> {
            respond("""{"code":"SUCCESS","message":"ok","data":null}""", headers = jsonHeaders)
        }
    }

    @Test
    fun `IOException은 Offline`() = runTest {
        expectError<NetworkError.Offline> { throw IOException("연결 끊김") }
    }

    @Test
    fun `요청 타임아웃은 Timeout`() = runTest {
        expectError<NetworkError.Timeout> { throw HttpRequestTimeoutException("api/v1/me", 1_000) }
    }

    @Test
    fun `그 외 예외는 Unknown`() = runTest {
        expectError<NetworkError.Unknown> { throw IllegalStateException("예상 밖") }
    }

    @Test
    fun `null 필드는 직렬화하지 않는다`() {
        assertEquals("""{"id":1}""", FestiCoreJson.encodeToString(Me(1, null)))
    }
}
