package io.festicore.service.device
import io.festicore.core.network.client.createFestiCoreHttpClient
import io.festicore.core.storage.secure.SecureStorage
import io.festicore.service.apiendpoint.device.DeviceRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class DeviceNotificationSettingsServiceTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val storage = object : SecureStorage {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key]
        override suspend fun write(key: String, value: String) {
            values[key] = value
        }
        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }
    private val installation = object : DeviceInstallationService {
        override suspend fun installationDeviceId() = "device-id"
    }

    @Test fun lazyRegistrationOnceAndNewProcessRefreshUsesPairedDeviceToken() = runTest {
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                requests += it
                val data = if (it.url.encodedPath.endsWith(
                        "notification-settings",
                    )
                ) {
                    """{"flagStatus":true,"crewStatus":true,"crewGather":true,"setReminders":true,"homecoming":false,"packUpdates":true}"""
                } else {
                    """{"deviceId":"device-id","deviceAccessToken":"refreshed-token"}"""
                }
                respond(
                    """{"code":"SUCCESS","message":"OK","data":$data}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            fun service() = DeviceNotificationSettingsServiceImpl(DeviceRequest(client), installation, storage) { JsonObject(emptyMap()) }
            val first = service()
            assertEquals(true, first.load()["flagStatus"])
            first.load()
            assertEquals(1, requests.count { it.url.encodedPath.endsWith("/devices") })
            service().load()
            val registrations = requests.filter { it.url.encodedPath.endsWith("/devices") }
            assertEquals(2, registrations.size)
            assertEquals("Bearer refreshed-token", registrations.last().headers[HttpHeaders.Authorization])
        } finally {
            client.close()
        }
    }

    @Test fun registrationConflictIsTerminalAndDoesNotRetryOnNextLoad() = runTest {
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                requests += it
                respond("""{"code":"FORBIDDEN"}""", HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        try {
            val service = DeviceNotificationSettingsServiceImpl(DeviceRequest(client), installation, storage) { JsonObject(emptyMap()) }
            repeat(2) { assertTrue(runCatching { service.load() }.exceptionOrNull() is DeviceRegistrationUnavailable) }
            assertEquals(1, requests.size)
        } finally {
            client.close()
        }
    }

    @Test fun settings401RefreshesOnceThenBecomesTerminal() = runTest {
        var registrations = 0
        var settings = 0
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                requests += it
                if (it.url.encodedPath.endsWith("notification-settings")) {
                    settings++
                    respond("{}", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    registrations++
                    respond(
                        """{"code":"SUCCESS","message":"OK","data":{"deviceId":"device-id","deviceAccessToken":"device-token"}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            },
        )
        try {
            val service = DeviceNotificationSettingsServiceImpl(DeviceRequest(client), installation, storage) { JsonObject(emptyMap()) }
            repeat(2) { assertTrue(runCatching { service.load() }.exceptionOrNull() is DeviceRegistrationUnavailable) }
            assertEquals(2, registrations)
            assertEquals(2, settings)
            assertEquals("Bearer device-token", requests[2].headers[HttpHeaders.Authorization])
        } finally {
            client.close()
        }
    }

    @Test fun mismatchedStoredPairIsDiscardedAndResponseIdMismatchIsTerminal() = runTest {
        storage.write("festicore.device.notification.authorization", """{"deviceId":"old-device","token":"old-token"}""")
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                requests += it
                respond(
                    """{"code":"SUCCESS","message":"OK","data":{"deviceId":"wrong-id","deviceAccessToken":"device-token"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val service = DeviceNotificationSettingsServiceImpl(DeviceRequest(client), installation, storage) { JsonObject(emptyMap()) }
            repeat(2) { assertTrue(runCatching { service.load() }.exceptionOrNull() is DeviceRegistrationUnavailable) }
            assertEquals(1, requests.size)
            assertEquals(null, requests.single().headers[HttpHeaders.Authorization])
            assertEquals("device-id", requests.single().headers["X-Device-Id"])
            assertTrue(storage.values.isEmpty())
        } finally {
            client.close()
        }
    }

    @Test fun storageFailureNeverMarksRegistrationCompleteOrSendsSettings() = runTest {
        val failingStorage = object : SecureStorage {
            override suspend fun read(key: String): String? = null
            override suspend fun write(key: String, value: String): Unit = throw IllegalStateException("unavailable")
            override suspend fun remove(key: String) = Unit
        }
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                requests += it
                respond(
                    """{"code":"SUCCESS","message":"OK","data":{"deviceId":"device-id","deviceAccessToken":"device-token"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val service =
                DeviceNotificationSettingsServiceImpl(DeviceRequest(client), installation, failingStorage) { JsonObject(emptyMap()) }
            repeat(2) { assertTrue(runCatching { service.load() }.isFailure) }
            assertEquals(2, requests.size)
            assertTrue(requests.all { it.url.encodedPath.endsWith("/devices") })
        } finally {
            client.close()
        }
    }

    @Test fun registration400IsTerminalButSettings400AllowsExplicitRetry() = runTest {
        var registerBad = true
        var settingsBad = false
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                requests += it
                val isSettings = it.url.encodedPath.endsWith("notification-settings")
                if (registerBad ||
                    (isSettings && settingsBad)
                ) {
                    respond("{}", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    val data = if (isSettings) {
                        """{"flagStatus":true,"crewStatus":true,"crewGather":true,"setReminders":true,"homecoming":false,"packUpdates":true}"""
                    } else {
                        """{"deviceId":"device-id","deviceAccessToken":"device-token"}"""
                    }
                    respond(
                        """{"code":"SUCCESS","message":"OK","data":$data}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            },
        )
        try {
            fun service() = DeviceNotificationSettingsServiceImpl(DeviceRequest(client), installation, storage) { JsonObject(emptyMap()) }
            val invalid = service()
            repeat(2) { assertTrue(runCatching { invalid.load() }.exceptionOrNull() is DeviceInvalidRegistration) }
            assertEquals(1, requests.size)
            registerBad = false
            settingsBad = true
            val retryable = service()
            assertTrue(runCatching { retryable.load() }.exceptionOrNull() is io.festicore.core.network.error.NetworkError.Http)
            settingsBad = false
            assertEquals(true, retryable.load()["flagStatus"])
        } finally {
            client.close()
        }
    }

    @Test fun concurrentLoadsRegisterOnlyOnceAnd500DoesNotAutomaticallyRetry() = runTest {
        var serverFails = false
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                requests += it
                kotlinx.coroutines.yield()
                if (serverFails) {
                    respond("{}", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    val data = if (it.url.encodedPath.endsWith(
                            "notification-settings",
                        )
                    ) {
                        """{"flagStatus":true,"crewStatus":true,"crewGather":true,"setReminders":true,"homecoming":false,"packUpdates":true}"""
                    } else {
                        """{"deviceId":"device-id","deviceAccessToken":"device-token"}"""
                    }
                    respond(
                        """{"code":"SUCCESS","message":"OK","data":$data}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            },
        )
        try {
            val service = DeviceNotificationSettingsServiceImpl(DeviceRequest(client), installation, storage) { JsonObject(emptyMap()) }
            kotlinx.coroutines.coroutineScope { List(4) { async { service.load() } }.forEach { it.await() } }
            assertEquals(1, requests.count { it.url.encodedPath.endsWith("/devices") })
            val before = requests.size
            serverFails = true
            assertTrue(runCatching { service.load() }.isFailure)
            assertEquals(before + 1, requests.size)
        } finally {
            client.close()
        }
    }

    @Test fun cancellationAfterRegistrationResponseStillPersistsCredential() = runTest {
        val enteredWrite = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseWrite = kotlinx.coroutines.CompletableDeferred<Unit>()
        var persisted = false
        val delayedStorage = object : SecureStorage {
            override suspend fun read(key: String): String? = null
            override suspend fun write(key: String, value: String) {
                enteredWrite.complete(Unit)
                releaseWrite.await()
                persisted = true
            }
            override suspend fun remove(key: String) = Unit
        }
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                respond(
                    """{"code":"SUCCESS","message":"OK","data":{"deviceId":"device-id","deviceAccessToken":"device-token"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val service =
                DeviceNotificationSettingsServiceImpl(DeviceRequest(client), installation, delayedStorage) { JsonObject(emptyMap()) }
            val loading = async { service.load() }
            enteredWrite.await()
            loading.cancel()
            releaseWrite.complete(Unit)
            loading.join()
            assertTrue(persisted)
        } finally {
            client.close()
        }
    }

    @Test fun malformedSuccessfulSaveReloadsAuthoritativeSettings() = runTest {
        var saved = false
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                val isSettings = it.url.encodedPath.endsWith("notification-settings")
                val data = when {
                    !isSettings -> """{"deviceId":"device-id","deviceAccessToken":"device-token"}"""

                    it.method == io.ktor.http.HttpMethod.Put -> {
                        saved = true
                        "{}"
                    }

                    else -> {
                        """{"flagStatus":${!saved},"crewStatus":true,"crewGather":true,
                        "setReminders":true,"homecoming":false,"packUpdates":true}"""
                    }
                }
                respond(
                    """{"code":"SUCCESS","message":"OK","data":$data}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val service = DeviceNotificationSettingsServiceImpl(DeviceRequest(client), installation, storage) { JsonObject(emptyMap()) }
            assertEquals(true, service.load()["flagStatus"])
            assertEquals(false, service.save(mapOf("flagStatus" to false))["flagStatus"])
        } finally {
            client.close()
        }
    }

    @Test fun screenCancellationWhileRegistrationResponsePendingStillSavesCredential() = runTest {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val client = createFestiCoreHttpClient(
            "https://festicore.test/",
            engine = MockEngine {
                started.complete(Unit)
                release.await()
                respond(
                    """{"code":"SUCCESS","message":"OK","data":{"deviceId":"device-id","deviceAccessToken":"device-token"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )
        try {
            val service = DeviceNotificationSettingsServiceImpl(DeviceRequest(client), installation, storage) { JsonObject(emptyMap()) }
            val loading = async { service.load() }
            started.await()
            loading.cancel()
            release.complete(Unit)
            loading.join()
            assertTrue("Registration credential must survive screen cancellation", storage.values.isNotEmpty())
        } finally {
            client.close()
        }
    }
}
