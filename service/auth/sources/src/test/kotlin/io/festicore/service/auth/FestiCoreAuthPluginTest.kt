package io.festicore.service.auth
import io.festicore.core.network.client.createFestiCoreHttpClient
import io.festicore.core.network.error.NetworkError
import io.festicore.service.api.base.AuthSessionError
import io.festicore.service.apiendpoint.auth.AuthLoginBody
import io.festicore.service.apiendpoint.auth.AuthRequest
import io.festicore.service.auth.expiry.SessionExpiry
import io.festicore.service.auth.expiry.SessionExpiryEventsImpl
import io.festicore.service.auth.refresh.TokenRefresher
import io.festicore.service.auth.store.CredentialStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FestiCoreAuthPluginTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val storage = FakeSecureStorage()
    private val store = CredentialStore(storage)
    private val expiryEvents = SessionExpiryEventsImpl()
    private val session = AuthSessionImpl(store)
    private val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())
    private val refreshCount = AtomicInteger()
    private val expiredCount = AtomicInteger()

    private val meJson =
        """{"code":"SUCCESS","message":"ok","data":{"id":"u1","provider":"google","nicknameConfirmed":true,"pushOptIn":false,""" +
            """"currentTermsVersion":"v1"}}"""

    private fun MockRequestHandleScope.ok(body: String): HttpResponseData = respond(body, headers = jsonHeaders)

    private fun MockRequestHandleScope.unauthorized(): HttpResponseData =
        respond("""{"code":"UNAUTHORIZED","message":"no"}""", HttpStatusCode.Unauthorized, jsonHeaders)

    private fun MockRequestHandleScope.refreshed(access: String, refresh: String): HttpResponseData =
        ok("""{"code":"SUCCESS","message":"ok","data":{"accessToken":"$access","refreshToken":"$refresh"}}""")

    /** [onMe]는 `GET api/v1/me`, [onRefresh]는 `POST api/v1/auth/refresh` 응답. 다른 경로는 로그인 응답. */
    private fun api(
        onRefresh: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { refreshed("new-access", "new-refresh") },
        onMe: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): AuthRequest {
        lateinit var api: AuthRequest
        val refresher = TokenRefresher(store, expiryEvents) { api.refresh(it) }
        val client = createFestiCoreHttpClient(
            baseUrl = "https://example.test/",
            engine = MockEngine { request ->
                requests += request
                when (request.url.encodedPath) {
                    "/api/v1/me" -> onMe(request)

                    "/api/v1/auth/refresh" -> {
                        refreshCount.incrementAndGet()
                        onRefresh(request)
                    }

                    else -> ok(
                        """{"code":"SUCCESS","message":"ok","data":{"accessToken":"a","refreshToken":"r","newUser":false,""" +
                            """"user":{"id":"u1","provider":"google","nicknameConfirmed":true,"pushOptIn":false}}}""",
                    )
                }
            },
        ) { install(festiCoreAuthPlugin(store, refresher)) }
        api = AuthRequest(client)
        return api
    }

    private fun TestScope.collectExpiry() {
        backgroundScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            expiryEvents.events.collect { expiredCount.incrementAndGet() }
        }
    }

    private val HttpRequestData.bearer: String? get() = headers[HttpHeaders.Authorization]

    private suspend inline fun <reified E : Throwable> expect(block: () -> Unit): E {
        try {
            block()
        } catch (e: Throwable) {
            assertTrue("기대 ${E::class.simpleName}, 실제 ${e::class.simpleName}", e is E)
            return e as E
        }
        fail("오류가 나지 않았습니다")
        error("unreachable")
    }

    private fun HttpRequestData.isMe() = url.encodedPath == "/api/v1/me"

    @Test
    fun `비인증 요청에는 Authorization을 붙이지 않는다`() = runTest {
        session.saveLogin("old-access", "old-refresh")

        api { ok(meJson) }.login(AuthLoginBody(provider = "google", token = "t"))

        assertNull(requests.single().bearer)
    }

    @Test
    fun `인증 요청에는 현재 토큰을 붙인다`() = runTest {
        session.saveLogin("old-access", "old-refresh")

        api { ok(meJson) }.me()

        assertEquals("Bearer old-access", requests.single().bearer)
    }

    @Test
    fun `동시에 401을 받은 두 요청은 refresh 1회 후 둘 다 새 토큰으로 성공한다`() = runTest {
        session.saveLogin("old-access", "old-refresh")
        val bothArrived = CompletableDeferred<Unit>()
        val arrived = AtomicInteger()
        val api = api { request ->
            if (request.bearer == "Bearer old-access") {
                if (arrived.incrementAndGet() == 2) bothArrived.complete(Unit)
                bothArrived.await()
                unauthorized()
            } else {
                ok(meJson)
            }
        }

        val results = List(2) { async(Dispatchers.Default) { api.me() } }.awaitAll()

        assertEquals(2, results.size)
        assertEquals(1, refreshCount.get())
        assertEquals(listOf("Bearer new-access", "Bearer new-access"), requests.filter { it.isMe() }.drop(2).map { it.bearer })
        assertEquals(1L, store.current()!!.revision)
        assertEquals("new-refresh", store.current()!!.refreshToken)
    }

    @Test
    fun `refresh 중 새 로그인이 저장되면 refresh 결과를 버리고 재전송하지 않는다`() = runTest {
        session.saveLogin("a-access", "a-refresh")
        val api = api(
            onRefresh = {
                session.saveLogin("b-access", "b-refresh")
                refreshed("a-new-access", "a-new-refresh")
            },
        ) { unauthorized() }

        expect<AuthSessionError.NotAuthenticated> { api.me() }

        val current = store.current()!!
        assertEquals("b-access", current.accessToken)
        assertEquals(0L, current.revision)
        assertEquals(listOf("Bearer a-access"), requests.filter { it.isMe() }.map { it.bearer })
    }

    @Test
    fun `refresh가 401이면 만료 이벤트를 1회 보내고 저장소를 비운다`() = runTest {
        collectExpiry()
        val sessionId = session.saveLogin("old-access", "old-refresh")
        val api = api(onRefresh = { unauthorized() }) { unauthorized() }

        expect<AuthSessionError.NotAuthenticated> { api.me() }

        assertEquals(1, expiredCount.get())
        // 구독 전에 만료돼도 잃지 않도록 세대가 1 오르고, 갱신하던 세션 ID를 함께 남긴다.
        assertEquals(SessionExpiry(generation = 1, sessionId = sessionId), expiryEvents.expired.value)
        assertNull(store.current())
        assertTrue(storage.values.isEmpty())
        assertEquals(1, requests.count { it.isMe() })
    }

    @Test
    fun `refresh가 오프라인이면 세션을 유지하고 오프라인으로 실패한다`() = runTest {
        collectExpiry()
        session.saveLogin("old-access", "old-refresh")
        val api = api(onRefresh = { throw IOException("offline") }) { unauthorized() }

        expect<NetworkError.Offline> { api.me() }

        val current = store.current()!!
        assertEquals("old-access", current.accessToken)
        assertEquals(0L, current.revision)
        assertEquals(0, expiredCount.get())
        assertEquals(SessionExpiry(generation = 0, sessionId = null), expiryEvents.expired.value)
    }

    @Test
    fun `refresh가 5xx면 세션을 유지하고 그 오류로 실패한다`() = runTest {
        session.saveLogin("old-access", "old-refresh")
        val api = api(onRefresh = { respond("""{"code":"ERROR","message":"x"}""", HttpStatusCode.ServiceUnavailable, jsonHeaders) }) {
            unauthorized()
        }

        val error = expect<NetworkError.Http> { api.me() }

        assertEquals(503, error.status)
        assertEquals("old-access", store.current()!!.accessToken)
    }

    @Test
    fun `계정 A 요청이 401을 받은 뒤 계정 B가 로그인하면 A 요청은 재전송 없이 실패하고 B 토큰으로 나간 요청이 없다`() = runTest {
        session.saveLogin("a-access", "a-refresh")
        val api = api {
            session.saveLogin("b-access", "b-refresh")
            unauthorized()
        }

        expect<AuthSessionError.NotAuthenticated> { api.me() }

        assertEquals(listOf("Bearer a-access"), requests.map { it.bearer })
        assertEquals(0, refreshCount.get())
        assertEquals("b-access", store.current()!!.accessToken)
    }

    @Test
    fun `기대 sessionId가 현재 세션과 다르면 보내지 않는다`() = runTest {
        session.saveLogin("old-access", "old-refresh")

        expect<AuthSessionError.NotAuthenticated> { api { ok(meJson) }.me(expectedSessionId = "other-session") }

        assertTrue(requests.isEmpty())
    }

    @Test
    fun `기대 sessionId가 현재 세션이면 보낸다`() = runTest {
        val sessionId = session.saveLogin("old-access", "old-refresh")

        api { ok(meJson) }.me(expectedSessionId = sessionId)

        assertEquals("Bearer old-access", requests.single().bearer)
    }

    @Test
    fun `세션이 없으면 인증 요청을 보내지 않는다`() = runTest {
        expect<AuthSessionError.NotAuthenticated> { api { ok(meJson) }.me() }

        assertTrue(requests.isEmpty())
    }

    @Test
    fun `재요청도 401이면 더 보내지 않고 401로 실패한다`() = runTest {
        session.saveLogin("old-access", "old-refresh")

        val error = expect<NetworkError.Http> { api { unauthorized() }.me() }

        assertTrue(error.isUnauthorized)
        assertEquals(listOf("Bearer old-access", "Bearer new-access"), requests.filter { it.isMe() }.map { it.bearer })
        assertEquals(1, refreshCount.get())
    }

    @Test
    fun `다른 요청이 이미 갱신했으면 refresh 없이 현재 토큰으로 재요청한다`() = runTest {
        val sessionId = session.saveLogin("old-access", "old-refresh")
        val api = api { request ->
            if (request.bearer == "Bearer old-access") {
                store.compareAndSetTokens(sessionId, 0, "other-access", "other-refresh")
                unauthorized()
            } else {
                ok(meJson)
            }
        }

        api.me()

        assertEquals(0, refreshCount.get())
        assertEquals(listOf("Bearer old-access", "Bearer other-access"), requests.map { it.bearer })
    }
}
