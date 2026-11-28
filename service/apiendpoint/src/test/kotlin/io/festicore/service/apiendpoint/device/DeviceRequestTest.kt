package io.festicore.service.apiendpoint.device
import io.festicore.core.network.client.createFestiCoreHttpClient
import io.festicore.core.network.error.NetworkError
import io.festicore.service.api.base.BearerAuthorizationKey
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
class DeviceRequestTest {
    @Test fun registerUsesExplicitNullAndDeviceOnlyAuthorization() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                requests += it
                respond(
                    """{"code":"SUCCESS","message":"OK","data":{"deviceId":"installation","deviceAccessToken":"device-only"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val api = DeviceRequest(client)
            val result = api.register("installation", "old-device", buildJsonObject { put("appVersion", "1.0.0") })
            assertEquals("installation", result["deviceId"]?.jsonPrimitive?.content)
            val request = requests.single()
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/v1/devices", request.url.encodedPath)
            assertEquals("installation", request.headers["X-Device-Id"])
            assertEquals("Bearer old-device", request.headers[HttpHeaders.Authorization])
            assertNull(request.attributes.getOrNull(BearerAuthorizationKey))
            val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
            assertEquals(JsonNull, body["fcmToken"])
            assertEquals(JsonPrimitive("android"), body["platform"])
            assertFalse(body.containsKey("apnsToken"))
        } finally {
            client.close()
        }
    }

    @Test fun settingsUsesPartialPutAndUnwrapsDirectData() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                requests += it
                respond(
                    """{"code":"SUCCESS","message":"OK","data":{"officialNews":false}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val api = DeviceRequest(client)
            assertEquals(JsonPrimitive(false), api.settings("id", "device")["officialNews"])
            api.saveSettings("id", "device", mapOf("officialNews" to false))
            assertEquals(listOf(HttpMethod.Get, HttpMethod.Put), requests.map { it.method })
            assertTrue(
                requests.all {
                    it.url.encodedPath == "/api/v1/devices/notification-settings" && it.headers["X-Device-Id"] == "id" &&
                        it.headers[HttpHeaders.Authorization] == "Bearer device" &&
                        it.attributes.getOrNull(BearerAuthorizationKey) == null
                },
            )
            val body = Json.parseToJsonElement(
                (requests.last().body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
            ).jsonObject
            assertEquals(setOf("officialNews"), body.keys)
        } finally {
            client.close()
        }
    }

    @Test fun unauthorizedIsNotRetriedOrReplacedByAnonymousRequest() = runTest {
        var calls = 0
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                calls++
                respond("""{"code":"UNAUTHORIZED"}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        try {
            val failure = runCatching { DeviceRequest(client).settings("id", "expired") }.exceptionOrNull()
            assertTrue(failure is NetworkError.Http)
            assertEquals(1, calls)
        } finally {
            client.close()
        }
    }
}
