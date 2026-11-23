package io.festicore.service.festivalcatalog

import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.service.api.AuthSessionError
import io.festicore.service.api.BearerAuthorization
import io.festicore.service.api.BearerAuthorizationKey
import io.festicore.service.api.FlagApi
import io.festicore.service.api.IDEMPOTENCY_KEY_HEADER
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 깃발 팔로우 서비스. iOS `Service/FestiCoreFestivalCatalog/Tests/FestivalFlagFollowTests.swift`. 응답은 iOS DTO 형태의 가짜 값이다. */
class FestivalFlagFollowTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var body = """{"code":"SUCCESS","message":"OK","data":{"flagId":"flg_a","following":true,"followerCount":4}}"""
    private var sessionId: String? = "session-1"
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    private val service = FestivalFlagFollowService(
        api = FlagApi(
            createFestiCoreHttpClient(
                baseUrl = "https://example.test/",
                engine = MockEngine { request ->
                    requests += request
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
        ),
        currentSessionId = { sessionId },
    )

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `팔로우는 세션에 묶인 PUT과 멱등 키를 보낸다`() = runTest {
        val result = service.setFollowing(true, "flg_a")

        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/v1/flags/flg_a/follow", request.url.encodedPath)
        assertEquals(BearerAuthorization("session-1"), request.attributes[BearerAuthorizationKey])
        assertTrue(uuid.matches(checkNotNull(request.headers[IDEMPOTENCY_KEY_HEADER])))
        assertEquals(4, result.followerCount)
    }

    @Test
    fun `언팔로우는 멱등 키 없이 DELETE를 보낸다`() = runTest {
        body = """{"code":"SUCCESS","message":"OK","data":{"flagId":"flg_a","following":false,"followerCount":3}}"""

        val result = service.setFollowing(false, "flg_a")

        val request = requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertFalse(request.headers.contains(IDEMPOTENCY_KEY_HEADER))
        assertFalse(result.following)
    }

    @Test
    fun `로그아웃 상태면 요청하지 않고 로그인 안내 오류다`() = runTest {
        sessionId = null

        val error = failure { service.setFollowing(true, "flg_a") }

        assertTrue(error is AuthSessionError.NotAuthenticated)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `다른 깃발 응답은 응답 오류다`() = runTest {
        body = """{"code":"SUCCESS","message":"OK","data":{"flagId":"flg_b","following":true,"followerCount":1}}"""

        val error = failure { service.setFollowing(true, "flg_a") }

        assertTrue(error is FestivalFlagServiceError.InvalidResponse)
    }

    @Test
    fun `경로에 넣을 수 없는 ID는 요청하지 않는다`() = runTest {
        for (id in listOf("", "flg/a", "flg a", "깃발", "a".repeat(65))) {
            val error = failure { service.setFollowing(true, id) }
            assertTrue(error is FestivalFlagServiceError.InvalidFlagId)
        }
        assertTrue(requests.isEmpty())
        assertTrue("a".repeat(64).isValidFlagId())
    }
}
