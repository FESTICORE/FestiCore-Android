package io.festicore.service.device

import io.festicore.core.network.error.NetworkError
import io.festicore.core.storage.secure.SecureStorage
import io.festicore.service.apiendpoint.device.DeviceRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class DeviceNotificationSettingsServiceImpl(
    private val request: DeviceRequest,
    private val installation: DeviceInstallationService,
    private val storage: SecureStorage,
    private val metadata: () -> JsonObject,
) : DeviceNotificationSettingsService {
    private val mutex = Mutex()
    private var registered: Credential? = null
    private var terminal: Exception? = null
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): Map<String, Boolean> = perform { request.settings(it.deviceId, it.token) }
    override suspend fun save(changes: Map<String, Boolean>): Map<String, Boolean> = perform {
        require(changes.keys.all { key -> key in keys })
        val response = request.saveSettings(it.deviceId, it.token, changes)
        if (validSettings(response)) response else request.settings(it.deviceId, it.token)
    }

    private suspend fun perform(action: suspend (Credential) -> JsonObject): Map<String, Boolean> = mutex.withLock {
        terminal?.let { throw it }
        val id = installation.installationDeviceId()
        if (registered?.deviceId != id) registered = null
        val credential = registered ?: register(id, savedCredential(id))
        val response = try {
            action(credential)
        } catch (error: NetworkError.Http) {
            if (error.status == 401) {
                val refreshed = register(id, credential)
                try {
                    action(refreshed)
                } catch (again: NetworkError.Http) {
                    throw mapped(again)
                }
            } else {
                throw mapped(error)
            }
        }
        if (!validSettings(response)) throw DeviceSettingsDecodingException()
        keys.mapNotNull { key -> (response[key] as? JsonPrimitive)?.booleanOrNull?.let { key to it } }.toMap()
    }

    private fun validSettings(response: JsonObject) = keys.take(6).all { (response[it] as? JsonPrimitive)?.booleanOrNull != null }

    private suspend fun savedCredential(id: String): Credential? {
        val value = storage.read(KEY) ?: return null
        val saved = try {
            json.decodeFromString<Credential>(value)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (saved?.deviceId != id || saved.token.isBlank()) {
            storage.remove(KEY)
            return null
        }
        return saved
    }

    private suspend fun register(id: String, old: Credential?): Credential =
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
            val response = try {
                kotlinx.coroutines.withTimeout(60_000) { request.register(id, old?.token, metadata()) }
            } catch (error: NetworkError.Http) {
                throw mapped(error, registration = true)
            }
            val token = response["deviceAccessToken"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            if (response["deviceId"]?.jsonPrimitive?.contentOrNull != id || token == null) {
                terminal = DeviceRegistrationUnavailable()
                throw DeviceRegistrationUnavailable()
            }
            val value = Credential(id, token)
            storage.write(KEY, json.encodeToString(value))
            registered = value
            value
        }

    private fun mapped(error: NetworkError.Http, registration: Boolean = false): Exception = when {
        registration && error.status == 400 -> DeviceInvalidRegistration().also { terminal = it }
        error.status in listOf(401, 403) -> DeviceRegistrationUnavailable().also { terminal = it }
        else -> error
    }

    @Serializable private data class Credential(
        val deviceId: String,
        val token: String,
    ) {
        override fun toString() = "DeviceCredential(redacted)"
    }
    private companion object {
        const val KEY = "festicore.device.notification.authorization"
        val keys = listOf("flagStatus", "crewStatus", "crewGather", "setReminders", "homecoming", "packUpdates", "officialNews")
    }
}

private class DeviceSettingsDecodingException : Exception("Invalid notification settings response")
