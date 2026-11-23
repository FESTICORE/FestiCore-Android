package io.festicore.domain.auth.sources

import io.festicore.core.datastore.SecureStorage
import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.domain.auth.AuthDomainError
import io.festicore.service.apiendpoint.auth.AuthRequest
import io.festicore.service.auth.AuthSession
import io.festicore.service.auth.SessionExpiryEvents
import io.festicore.service.auth.di.serviceAuthModule
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
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

    private fun user(id: String) = """{"id":"$id","provider":"google","email":null,"nickname":"roy","nicknameConfirmed":true,""" +
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
}
