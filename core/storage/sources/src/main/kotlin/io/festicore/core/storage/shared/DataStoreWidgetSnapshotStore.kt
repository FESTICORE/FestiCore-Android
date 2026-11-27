package io.festicore.core.storage.shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.festicore.core.storage.secure.ValueCipher
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.first

internal class DataStoreWidgetSnapshotStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: ValueCipher,
) : WidgetSnapshotStore {
    override suspend fun read(): WidgetSnapshotRecord {
        val preferences = dataStore.data.first()
        val revision = preferences[REVISION] ?: 0
        val generation = preferences[GENERATION] ?: 0
        val ciphertext = preferences[PAYLOAD] ?: return WidgetSnapshotRecord(revision, generation)
        return try {
            WidgetSnapshotRecord(revision, generation, cipher.decrypt(Base64.decode(ciphertext)).decodeToString())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            WidgetSnapshotRecord(revision, generation, unavailable = true)
        }
    }

    override suspend fun compareAndSet(
        expectedRevision: Long,
        payload: String?,
        invalidate: Boolean,
    ): Boolean {
        val encrypted = payload?.let { Base64.encode(cipher.encrypt(it.encodeToByteArray())) }
        var accepted = false
        dataStore.edit { preferences ->
            if ((preferences[REVISION] ?: 0) == expectedRevision) {
                if (encrypted == null) preferences.remove(PAYLOAD) else preferences[PAYLOAD] = encrypted
                preferences[REVISION] = expectedRevision + 1
                if (invalidate) preferences[GENERATION] = (preferences[GENERATION] ?: 0) + 1
                accepted = true
            }
        }
        return accepted
    }

    private companion object {
        val REVISION = longPreferencesKey("revision")
        val GENERATION = longPreferencesKey("generation")
        val PAYLOAD = stringPreferencesKey("encrypted_snapshot")
    }
}
