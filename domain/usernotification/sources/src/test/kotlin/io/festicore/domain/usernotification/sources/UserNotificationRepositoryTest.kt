package io.festicore.domain.usernotification.sources

import io.festicore.core.network.NetworkError
import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.domain.usernotification.UserNotificationError
import io.festicore.service.api.BearerAuthorizationKey
import io.festicore.service.api.UserNotificationApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** iOS `UserNotificationRepositoryTests` 4건(같은 이름) + 401. MockEngine 응답은 iOS DTO 형태의 가짜 값이다. 실서버를 부르지 않는다. */
class UserNotificationRepositoryTest {
    private val sessionId = "00000000-0000-0000-0000-000000000001"
    private val requests = mutableListOf<HttpRequestData>()

    private fun repository(
        sessionId: String? = this.sessionId,
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "",
        failure: Throwable? = null,
    ) = UserNotificationRepositoryImpl(
        api =
            UserNotificationApi(
                createFestiCoreHttpClient(
                    baseUrl = "https://festicore.test/",
                    engine =
                        MockEngine { request ->
                            requests += request
                            failure?.let { throw it }
                            if (body.isEmpty()) {
                                respond(ByteReadChannel.Empty, status)
                            } else {
                                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                            }
                        },
                ),
            ),
        currentSessionId = { sessionId },
    )

    private val HttpRequestData.boundSession: String? get() = attributes.getOrNull(BearerAuthorizationKey)?.expectedSessionId

    @Test
    fun listUsesCurrentSessionAndRejectsInvalidEnvelope() = runTest {
        val page = repository(
            body = """{"code":"SUCCESS","message":"OK","data":{"items":[],"nextCursor":null}}""",
        ).notifications("opaque-cursor")
        assertTrue(page.items.isEmpty())
        val sent = requests.single()
        assertEquals("/api/v1/me/notifications", sent.url.encodedPath)
        assertEquals("opaque-cursor", sent.url.parameters["before"])
        assertEquals(HttpMethod.Get, sent.method)
        assertEquals(sessionId, sent.boundSession)

        requests.clear()
        repository(body = """{"code":"SUCCESS","message":"OK","data":{"items":[],"nextCursor":null}}""").notifications(null)
        assertNull(requests.single().url.parameters["before"])

        val invalid = repository(body = """{"code":"ERROR","message":"failure","data":{"items":[],"nextCursor":null}}""")
        assertFails<UserNotificationError.InvalidResponse> { invalid.notifications(null) }
    }

    @Test
    fun missingSessionAndTransportFailurePropagate() = runTest {
        val unauthenticated = repository(sessionId = null, body = """{"code":"SUCCESS","data":{"unreadCount":0}}""")
        assertFails<UserNotificationError.Unauthenticated> { unauthenticated.unreadCount() }
        assertTrue(requests.isEmpty())

        assertFails<UserNotificationError.Unavailable> { repository(failure = IOException("offline")).unreadCount() }
    }

    @Test
    fun unreadCountValidatesEnvelopeAndNonnegativeCount() = runTest {
        assertEquals(3, repository(body = """{"code":"SUCCESS","message":"OK","data":{"unreadCount":3}}""").unreadCount())
        val sent = requests.single()
        assertEquals("/api/v1/me/notifications/unread-count", sent.url.encodedPath)
        assertEquals(sessionId, sent.boundSession)

        for (json in listOf(
            """{"code":"ERROR","message":"failure","data":{"unreadCount":3}}""",
            """{"code":"SUCCESS","message":"OK","data":{"unreadCount":-1}}""",
        )) {
            assertFails<UserNotificationError.InvalidResponse> { repository(body = json).unreadCount() }
        }
    }

    @Test
    fun readAndDeleteAccept204AndSplitAtBackendLimit() = runTest {
        val repository = repository(status = HttpStatusCode.NoContent)
        val ids = (0 until 101).map { "ntf_$it" }
        repository.markRead(ids)
        repository.delete(ids)
        repository.markRead(emptyList())

        assertEquals(
            listOf(
                "/api/v1/me/notifications/read",
                "/api/v1/me/notifications/read",
                "/api/v1/me/notifications/delete",
                "/api/v1/me/notifications/delete",
            ),
            requests.map { it.url.encodedPath },
        )
        assertTrue(requests.all { it.method == HttpMethod.Post && it.boundSession == sessionId })
        val batches = requests.map { Json.parseToJsonElement((it.body as TextContent).text).jsonObject.getValue("ids").jsonArray.size }
        assertEquals(listOf(100, 1, 100, 1), batches)
    }

    @Test
    fun unauthorizedResponseMapsToUnauthenticated() = runTest {
        val unauthorized =
            repository(
                status = HttpStatusCode.Unauthorized,
                body = """{"code":"UNAUTHORIZED","data":null,"message":"인증 토큰이 없습니다."}""",
            )
        assertFails<UserNotificationError.Unauthenticated> { unauthorized.notifications(null) }
        assertFails<UserNotificationError.Unauthenticated> { unauthorized.markRead(listOf("ntf_1")) }
        assertFails<UserNotificationError.Unavailable> {
            repository(status = HttpStatusCode.InternalServerError, body = """{"code":"ERROR","message":"x"}""").delete(listOf("ntf_1"))
        }
    }

    private suspend inline fun <reified E : UserNotificationError> assertFails(block: suspend () -> Unit) {
        try {
            block()
            fail("${E::class.simpleName}를 기대했다")
        } catch (e: UserNotificationError) {
            assertTrue("실제 ${e::class.simpleName}", e is E)
        } catch (e: NetworkError) {
            fail("NetworkError가 그대로 나왔다: ${e::class.simpleName}")
        }
    }
}
