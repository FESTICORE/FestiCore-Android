package io.festicore.core.storage.secure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.first

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
