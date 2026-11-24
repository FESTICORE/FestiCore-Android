package io.festicore.service.device

import io.festicore.core.storage.secure.SecureStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInstallationServiceTest {
    private val storage = MemorySecureStorage()
    private val service = DeviceInstallationServiceImpl(storage)

    @Test
    fun `처음에는 소문자 UUID를 만들어 deviceID 키에 저장한다`() = runTest {
        val id = service.installationDeviceId()

        assertTrue(id.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")))
        assertEquals(id, storage.values["deviceID"])
    }

    @Test
    fun `저장된 값이 있으면 그대로 돌려준다`() = runTest {
        storage.values["deviceID"] = "00000000-0000-4000-8000-000000000001"

        assertEquals("00000000-0000-4000-8000-000000000001", service.installationDeviceId())
    }

    @Test
    fun `동시에 불러도 한 번만 만든다`() = runTest {
        val ids = List(5) { async { service.installationDeviceId() } }.awaitAll()

        assertEquals(1, ids.toSet().size)
        assertEquals(1, storage.writes)
    }

    private class MemorySecureStorage : SecureStorage {
        val values = mutableMapOf<String, String>()
        var writes = 0

        override suspend fun read(key: String): String? = values[key]

        override suspend fun write(key: String, value: String) {
            writes += 1
            values[key] = value
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }
}
