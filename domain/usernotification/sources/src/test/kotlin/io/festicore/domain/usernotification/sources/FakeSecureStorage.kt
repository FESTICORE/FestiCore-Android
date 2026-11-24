package io.festicore.domain.usernotification.sources

import io.festicore.core.storage.secure.SecureStorage
import java.util.concurrent.ConcurrentHashMap

/** 메모리 [SecureStorage]. Koin 모듈 해석 확인에만 쓴다. */
class FakeSecureStorage : SecureStorage {
    private val values = ConcurrentHashMap<String, String>()

    override suspend fun read(key: String): String? = values[key]

    override suspend fun write(key: String, value: String) {
        values[key] = value
    }

    override suspend fun remove(key: String) {
        values.remove(key)
    }
}
