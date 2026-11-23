package io.festicore.service.apiendpoint.auth

import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.createFestiCoreHttpClient
import io.festicore.service.api.RefreshResponse
import io.festicore.service.api.base.BearerAuthorization
import io.festicore.service.api.base.BearerAuthorizationKey
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRequestTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(body: String) = AuthRequest(
        createFestiCoreHttpClient(
            baseUrl = "https://example.test/",
            engine = MockEngine { request ->
                requests += request
                respond(body, headers = jsonHeaders)
            },
        ),
    )

    private val HttpRequestData.textBody: String get() = (body as TextContent).text

    @Test
    fun `로그인 요청은 null 필드를 보내지 않는다`() {
        val json = FestiCoreJson.encodeToString(AuthLoginBody.serializer(), AuthLoginBody(provider = "google", token = "t"))

        assertEquals("""{"provider":"google","token":"t"}""", json)
    }

    @Test
    fun `가입 요청은 acceptTerms를 객체로 보낸다`() {
        val json = FestiCoreJson.encodeToString(
            SignUpBody.serializer(),
            SignUpBody("roy", SignUpBody.AcceptTerms("2026-10"), pushOptIn = true),
        )

        assertEquals("""{"nickname":"roy","acceptTerms":{"version":"2026-10"},"pushOptIn":true}""", json)
    }

    @Test
    fun `login은 경로 · 본문을 보내고 stage 형식 응답을 읽는다`() = runTest {
        val response = api(
            """{"code":"SUCCESS","message":"ok","data":{"accessToken":"a","refreshToken":"r","newUser":true,""" +
                """"user":{"id":"u1","provider":"google","email":null,"nickname":null,"nicknameConfirmed":false,""" +
                """"termsAcceptedAt":null,"termsVersion":null,"pushOptIn":false,"profileImageUrl":null,"createdAt":"2026-11-21T00:00:00Z"}}}""",
        ).login(AuthLoginBody(provider = "google", token = "id-token"))

        assertEquals("a", response.accessToken)
        assertEquals("r", response.refreshToken)
        assertTrue(response.newUser)
        assertEquals("u1", response.user.id)
        assertNull(response.user.nickname)
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://example.test/api/v1/auth/login", request.url.toString())
        assertEquals("""{"provider":"google","token":"id-token"}""", request.textBody)
        assertNull(request.attributes.getOrNull(BearerAuthorizationKey))
    }

    @Test
    fun `refresh는 Bearer 표시 없이 refreshToken을 보낸다`() = runTest {
        val response = api("""{"code":"SUCCESS","message":"ok","data":{"accessToken":"a2","refreshToken":"r2"}}""").refresh("r1")

        assertEquals(RefreshResponse("a2", "r2"), response)
        val request = requests.single()
        assertEquals("https://example.test/api/v1/auth/refresh", request.url.toString())
        assertEquals("""{"refreshToken":"r1"}""", request.textBody)
        assertNull(request.attributes.getOrNull(BearerAuthorizationKey))
    }

    @Test
    fun `me는 Bearer 대상으로 표시하고 currentTermsVersion을 읽는다`() = runTest {
        val me = api(
            """{"code":"SUCCESS","message":"ok","data":{"id":"u1","provider":"google","email":"a@b.c","nickname":"roy",""" +
                """"nicknameConfirmed":true,"termsAcceptedAt":"2026-11-01T00:00:00Z","termsVersion":"2026-10","pushOptIn":true,""" +
                """"profileImageUrl":null,"currentTermsVersion":"2026-10"}}""",
        ).me(expectedSessionId = "s1")

        assertEquals("2026-10", me.currentTermsVersion)
        assertTrue(me.nicknameConfirmed)
        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://example.test/api/v1/me", request.url.toString())
        assertEquals(BearerAuthorization("s1"), request.attributes[BearerAuthorizationKey])
    }

    @Test
    fun `patchMe는 PATCH로 가입 본문을 보내고 user를 읽는다`() = runTest {
        val user = api(
            """{"code":"SUCCESS","message":"ok","data":{"id":"u1","provider":"google","nickname":"roy","nicknameConfirmed":true,""" +
                """"termsAcceptedAt":"2026-11-01T00:00:00Z","termsVersion":"2026-10","pushOptIn":false}}""",
        ).patchMe(SignUpBody("roy", SignUpBody.AcceptTerms("2026-10"), pushOptIn = false))

        assertEquals("roy", user.nickname)
        assertFalse(user.pushOptIn)
        val request = requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("""{"nickname":"roy","acceptTerms":{"version":"2026-10"},"pushOptIn":false}""", request.textBody)
        assertEquals(BearerAuthorization(), request.attributes[BearerAuthorizationKey])
    }
}
