package io.festicore.domain.auth.sources

import io.festicore.core.storage.SecureStorage
import io.festicore.core.storage.SecureStorageException
import java.util.concurrent.ConcurrentHashMap

/** 메모리 [SecureStorage]. [failWrites]면 쓰기에서 [SecureStorageException]을 던진다. */
class FakeSecureStorage : SecureStorage {
    val values = ConcurrentHashMap<String, String>()
    var failWrites = false

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String) {
        if (failWrites) throw SecureStorageException(IllegalStateException())
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}
