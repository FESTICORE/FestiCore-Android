package io.festicore.service.apiendpoint.device

import io.festicore.core.network.endpoint.festiCoreRequest
import io.festicore.service.api.device.DeviceApi
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Device bearer only: never requests the account-auth plugin or automatic retry. */
class DeviceRequest(private val client: HttpClient) {
    suspend fun register(
        deviceId: String,
        token: String?,
        metadata: JsonObject,
    ): JsonObject = client.festiCoreRequest {
        method = HttpMethod.Put
        url(DeviceApi.REGISTER)
        header("X-Device-Id", deviceId)
        token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        contentType(ContentType.Application.Json)
        setBody(
            buildJsonObject {
                metadata.forEach { (key, value) -> put(key, value) }
                put("platform", "android")
                put("fcmToken", JsonNull)
            },
        )
    }

    suspend fun settings(deviceId: String, token: String): JsonObject = client.festiCoreRequest {
        method = HttpMethod.Get
        url(DeviceApi.SETTINGS)
        header("X-Device-Id", deviceId)
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    suspend fun saveSettings(
        deviceId: String,
        token: String,
        changes: Map<String, Boolean>,
    ): JsonObject = client.festiCoreRequest {
        method = HttpMethod.Put
        url(DeviceApi.SETTINGS)
        header("X-Device-Id", deviceId)
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(buildJsonObject { changes.forEach { (key, value) -> put(key, value) } })
    }
}
