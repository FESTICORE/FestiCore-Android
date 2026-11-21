package io.festicore.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.first

/** 토큰 등 보안 문자열 저장소. iOS `SecureStorage`(Keychain). */
interface SecureStorage {
    /** 값이 없거나 복호화에 실패하면 null. 복호화에 실패한 값은 지운다. */
    suspend fun read(key: String): String?

    /** 암호화에 실패하면 [SecureStorageException]을 던진다. */
    suspend fun write(key: String, value: String)

    suspend fun remove(key: String)
}

class SecureStorageException(cause: Throwable) : Exception("보안 저장소에 쓰지 못했습니다", cause)

/** 값을 [ValueCipher]로 암호화해 Base64(IV + 암호문)로 Preferences DataStore에 둔다. */
internal class DataStoreSecureStorage(
    private val dataStore: DataStore<Preferences>,
    private val cipher: ValueCipher,
) : SecureStorage {
    override suspend fun read(key: String): String? {
        val stored = dataStore.data.first()[stringPreferencesKey(key)] ?: return null
        return try {
            cipher.decrypt(Base64.decode(stored)).decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            remove(key)
            null
        }
    }

    override suspend fun write(key: String, value: String) {
        val encrypted = try {
            Base64.encode(cipher.encrypt(value.encodeToByteArray()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SecureStorageException(e)
        }
        dataStore.edit { it[stringPreferencesKey(key)] = encrypted }
    }

    override suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }
}
