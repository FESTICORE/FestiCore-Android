package io.festicore.core.storage.secure

/** 토큰 등 보안 문자열 저장소. iOS `SecureStorage`(Keychain). */
interface SecureStorage {
    /** 값이 없거나 복호화에 실패하면 null. 복호화에 실패한 값은 지운다. */
    suspend fun read(key: String): String?

    /** 암호화에 실패하면 [SecureStorageException]을 던진다. */
    suspend fun write(key: String, value: String)

    suspend fun remove(key: String)
}

class SecureStorageException(cause: Throwable) : Exception("보안 저장소에 쓰지 못했습니다", cause)
