package io.festicore.service.auth

import io.festicore.core.datastore.SecureStorage
import java.util.concurrent.ConcurrentHashMap

/** 메모리 [SecureStorage]. */
class FakeSecureStorage : SecureStorage {
    val values = ConcurrentHashMap<String, String>()

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}
