package io.festicore.domain.auth.sources
import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.core.storage.secure.SecureStorage
import io.festicore.domain.auth.entity.AuthDomainError
import io.festicore.domain.auth.sources.repository.AuthRepositoryImpl
import io.festicore.service.api.base.BearerAuthorizationKey
import io.festicore.service.apiendpoint.auth.AuthRequest
import io.festicore.service.auth.AuthSession
import io.festicore.service.auth.di.serviceAuthModule
import io.festicore.service.auth.expiry.SessionExpiryEvents
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AuthRepositoryImplTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private val responses = ArrayDeque<Pair<HttpStatusCode, String>>()
    private val storage = FakeSecureStorage()

    /** 요청을 받을 때마다 응답 전에 부른다(그 사이 세션이 바뀌는 경우). */
    private var onRequest: suspend () -> Unit = {}

    /** `true`면 응답 대신 전송 실패(IOException)를 낸다. */
    private var offline = false

    // AuthSession은 service/auth 밖에서 만들 수 없어 그 Koin 모듈로 꺼낸다. HttpClient는 꺼내지 않는다.
    private val koin = koinApplication {
        modules(serviceAuthModule("https://example.test/"), module { single<SecureStorage> { storage } })
    }.koin
    private val session: AuthSession = koin.get()

    private val repository = AuthRepositoryImpl(
        api = AuthRequest(
            createFestiCoreHttpClient(
                baseUrl = "https://example.test/",
                engine = MockEngine { request ->
                    requests += request
                    onRequest()
                    if (offline) throw IOException("offline")
                    val (status, body) = responses.removeFirst()
                    respond(body, status, jsonHeaders)
                },
            ),
        ),
        session = session,
        sessionExpiryEvents = koin.get<SessionExpiryEvents>(),
    )

    private fun enqueue(body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        responses += status to body
    }

    private fun user(id: String, nickname: String = "roy") =
        """{"id":"$id","provider":"google","email":null,"nickname":"$nickname","nicknameConfirmed":true,""" +
            """"termsAcceptedAt":"2026-11-21T00:00:00Z","termsVersion":"2026-10","pushOptIn":true,"profileImageUrl":null}"""

    private val loginResponse =
        """{"code":"SUCCESS","message":"ok","data":{"accessToken":"a","refreshToken":"r","newUser":true,"user":${user("u1")}}}"""

    private val meResponse =
        """{"code":"SUCCESS","message":"ok","data":{"id":"u1","provider":"google","nickname":"roy","nicknameConfirmed":true,""" +
            """"termsAcceptedAt":"2026-11-21T00:00:00Z","termsVersion":"2026-10","pushOptIn":true,"currentTermsVersion":"2026-10"}}"""

    private suspend inline fun <reified E : AuthDomainError> assertFails(block: suspend () -> Unit) {
        try {
            block()
            fail("${E::class.simpleName}를 기대했다")
        } catch (e: AuthDomainError) {
            assertTrue("실제 ${e::class.simpleName}", e is E)
        }
    }

    @Test
    fun `로그인하면 토큰을 저장하고 프로필을 읽는다`() = runTest {
        enqueue(loginResponse)
        enqueue(meResponse)

        val result = repository.login("id-token")
        val profile = repository.fetchProfile()

        assertTrue(result.newUser)
        assertEquals("u1", result.user.id)
        assertEquals(result.sessionId, session.currentSessionId())
        assertTrue(repository.isLoggedIn())
        assertNotNull(storage.values.values.single().takeIf { "\"accessToken\":\"a\"" in it && "\"refreshToken\":\"r\"" in it })
        assertEquals("""{"provider":"google","token":"id-token"}""", (requests[0].body as TextContent).text)
        assertEquals(HttpMethod.Get, requests[1].method)
        assertEquals("https://example.test/api/v1/me", requests[1].url.toString())
        assertEquals("u1", profile.user.id)
        assertTrue(profile.isEligible)
    }

    @Test
    fun `토큰 저장에 실패하면 CredentialSaveFailed`() = runTest {
        storage.failWrites = true
        enqueue(loginResponse)

        assertFails<AuthDomainError.CredentialSaveFailed> { repository.login("id-token") }
        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `서버 로그인 실패는 도메인 오류로 바꾼다`() = runTest {
        enqueue("""{"code":"INVALID_TOKEN","message":"x","data":null}""", HttpStatusCode.Unauthorized)

        assertFails<AuthDomainError.NotAuthenticated> { repository.login("id-token") }
        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `세션이 없으면 프로필을 요청하지 않는다`() = runTest {
        assertFails<AuthDomainError.NotAuthenticated> { repository.fetchProfile() }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `기대 세션이 현재 세션과 다르면 프로필을 요청하지 않는다`() = runTest {
        enqueue(loginResponse)
        repository.login("id-token")

        assertFails<AuthDomainError.NotAuthenticated> { repository.fetchProfile(expectedSessionId = "other") }
        assertEquals(1, requests.size)
    }

    @Test
    fun `기대 세션이 현재 세션과 같으면 프로필을 읽는다`() = runTest {
        enqueue(loginResponse)
        enqueue(meResponse)
        val login = repository.login("id-token")

        assertEquals("u1", repository.fetchProfile(expectedSessionId = login.sessionId).user.id)
    }

    @Test
    fun `가입은 공백을 뺀 닉네임과 약관 버전을 보낸다`() = runTest {
        enqueue(loginResponse)
        enqueue("""{"code":"SUCCESS","message":"ok","data":${user("u1")}}""")
        val login = repository.login("id-token")

        val user = repository.signUp(" roy ", "2026-10", expectedUserId = "u1", expectedSessionId = login.sessionId)

        assertEquals("u1", user.id)
        assertEquals(HttpMethod.Patch, requests[1].method)
        assertEquals(
            """{"nickname":"roy","acceptTerms":{"version":"2026-10"},"pushOptIn":true}""",
            (requests[1].body as TextContent).text,
        )
    }

    @Test
    fun `가입 중 약관이 바뀌면 TermsVersionOutdated`() = runTest {
        enqueue(loginResponse)
        enqueue("""{"code":"TERMS_VERSION_OUTDATED","message":"약관 버전이 다릅니다","data":null}""", HttpStatusCode.UnprocessableEntity)
        repository.login("id-token")

        assertFails<AuthDomainError.TermsVersionOutdated> { repository.signUp("roy", "2026-09", expectedUserId = "u1") }
    }

    @Test
    fun `가입 응답 사용자가 요청 계정과 다르면 Unavailable`() = runTest {
        enqueue(loginResponse)
        enqueue("""{"code":"SUCCESS","message":"ok","data":${user("u2")}}""")
        repository.login("id-token")

        assertFails<AuthDomainError.Unavailable> { repository.signUp("roy", "2026-10", expectedUserId = "u1") }
    }

    @Test
    fun `닉네임 규칙을 어기면 요청하지 않는다`() = runTest {
        enqueue(loginResponse)
        repository.login("id-token")

        assertFails<AuthDomainError.Unavailable> { repository.signUp("   ", "2026-10", expectedUserId = "u1") }
        assertEquals(1, requests.size)
    }

    @Test
    fun `clearSession은 저장한 세션을 지운다`() = runTest {
        enqueue(loginResponse)
        repository.login("id-token")

        repository.clearSession()

        assertFalse(repository.isLoggedIn())
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun `clearSessionIf는 현재 세션일 때만 지우고 다른 세션은 남긴다`() = runTest {
        enqueue(loginResponse)
        val first = repository.login("id-token").sessionId
        enqueue(loginResponse)
        val second = repository.login("id-token").sessionId

        assertFalse(repository.clearSessionIf(first))
        assertEquals(second, session.currentSessionId())
        assertTrue(repository.clearSessionIf(second))
        assertFalse(repository.isLoggedIn())
        assertFalse(repository.hasSessionExpired(second))
    }

    private val logoutResponse = """{"code":"SUCCESS","message":"ok","data":{"success":true}}"""
    private val unauthorized = """{"code":"UNAUTHORIZED","message":"x"}"""

    private suspend fun loggedIn(): String {
        enqueue(loginResponse)
        return repository.login("id-token").sessionId
    }

    @Test
    fun `닉네임 변경은 공백을 뺀 닉네임만 현재 세션의 PATCH me로 보낸다`() = runTest {
        val sessionId = loggedIn()
        enqueue("""{"code":"SUCCESS","message":"ok","data":${user("u1", nickname = "로이")}}""")

        val user = repository.updateNickname("  로이 ")

        assertEquals("로이", user.nickname)
        val request = requests[1]
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("https://example.test/api/v1/me", request.url.toString())
        assertEquals("""{"nickname":"로이"}""", (request.body as TextContent).text)
        assertEquals(sessionId, request.attributes[BearerAuthorizationKey].expectedSessionId)
    }

    @Test
    fun `닉네임이 1~12자가 아니면 요청하지 않고 InvalidNickname`() = runTest {
        loggedIn()

        for (nickname in listOf("   ", "열세글자가넘는아주긴닉네임")) {
            assertFails<AuthDomainError.InvalidNickname> { repository.updateNickname(nickname) }
        }
        assertEquals(1, requests.size)
        assertTrue(repository.isLoggedIn())
    }

    @Test
    fun `세션이 없으면 닉네임 변경을 요청하지 않는다`() = runTest {
        assertFails<AuthDomainError.NotAuthenticated> { repository.updateNickname("로이") }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `닉네임 변경 401은 세션을 지우고 NotAuthenticated`() = runTest {
        loggedIn()
        enqueue(unauthorized, HttpStatusCode.Unauthorized)

        assertFails<AuthDomainError.NotAuthenticated> { repository.updateNickname("로이") }
        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `로그아웃 성공은 서버 응답 뒤 세션을 지운다`() = runTest {
        val sessionId = loggedIn()
        enqueue(logoutResponse)

        repository.signOut()

        val request = requests[1]
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://example.test/api/v1/auth/logout", request.url.toString())
        assertTrue(request.body is OutgoingContent.NoContent)
        assertEquals(sessionId, request.attributes[BearerAuthorizationKey].expectedSessionId)
        assertFalse(repository.isLoggedIn())
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun `이미 로그아웃 상태면 요청하지 않고 끝낸다`() = runTest {
        repository.signOut()

        assertTrue(requests.isEmpty())
    }

    @Test
    fun `로그아웃 401은 세션을 지우고 성공으로 끝낸다`() = runTest {
        loggedIn()
        enqueue(unauthorized, HttpStatusCode.Unauthorized)

        repository.signOut()

        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `로그아웃 전송 실패 · 5xx · success false는 세션을 두고 오류를 던진다`() = runTest {
        val sessionId = loggedIn()
        offline = true
        assertFails<AuthDomainError.Offline> { repository.signOut() }
        offline = false
        enqueue("""{"code":"INTERNAL","message":"x"}""", HttpStatusCode.InternalServerError)
        assertFails<AuthDomainError.Unavailable> { repository.signOut() }
        enqueue("""{"code":"SUCCESS","message":"ok","data":{"success":false}}""")
        assertFails<AuthDomainError.Unavailable> { repository.signOut() }

        assertEquals(sessionId, session.currentSessionId())
    }

    @Test
    fun `토큰 갱신이 먼저 세션을 지웠으면 전송 실패여도 로그아웃을 마친다`() = runTest {
        loggedIn()
        onRequest = { if (requests.size == 2) session.clear() }
        offline = true

        repository.signOut()

        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `탈퇴 204 빈 응답 뒤 세션을 지운다`() = runTest {
        val sessionId = loggedIn()
        enqueue("", HttpStatusCode.NoContent)

        repository.withdraw()

        val request = requests[1]
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("https://example.test/api/v1/me", request.url.toString())
        assertTrue(request.body is OutgoingContent.NoContent)
        assertEquals(sessionId, request.attributes[BearerAuthorizationKey].expectedSessionId)
        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `세션이 없으면 탈퇴를 요청하지 않고 NotAuthenticated`() = runTest {
        assertFails<AuthDomainError.NotAuthenticated> { repository.withdraw() }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `탈퇴 401은 세션을 지우고 NotAuthenticated`() = runTest {
        loggedIn()
        enqueue(unauthorized, HttpStatusCode.Unauthorized)

        assertFails<AuthDomainError.NotAuthenticated> { repository.withdraw() }
        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `탈퇴 전송 실패 · 409 · 5xx는 세션을 두고 오류를 던진다`() = runTest {
        val sessionId = loggedIn()
        offline = true
        assertFails<AuthDomainError.Offline> { repository.withdraw() }
        offline = false
        enqueue("""{"code":"CONFLICT","message":"마지막 owner 계정은 탈퇴할 수 없습니다."}""", HttpStatusCode.Conflict)
        assertFails<AuthDomainError.Unavailable> { repository.withdraw() }
        enqueue("""{"code":"INTERNAL","message":"x"}""", HttpStatusCode.InternalServerError)
        assertFails<AuthDomainError.Unavailable> { repository.withdraw() }

        assertEquals(sessionId, session.currentSessionId())
    }

    @Test
    fun `이전 탈퇴 응답은 그 사이 저장된 새 로그인 세션을 지우지 않는다`() = runTest {
        loggedIn()
        var newSessionId: String? = null
        onRequest = { if (requests.size == 2) newSessionId = session.saveLogin("new-access", "new-refresh") }
        enqueue("", HttpStatusCode.NoContent)

        repository.withdraw()

        assertNotNull(newSessionId)
        assertEquals(newSessionId, session.currentSessionId())
    }
}
