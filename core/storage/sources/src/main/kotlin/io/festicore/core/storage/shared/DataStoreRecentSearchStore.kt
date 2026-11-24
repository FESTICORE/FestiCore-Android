package io.festicore.core.storage.shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

internal class DataStoreRecentSearchStore(private val dataStore: DataStore<Preferences>) : RecentSearchStore {
    override suspend fun load(userId: String?): List<String> = dataStore.data.first()[key(userId)].toSearches()

    override suspend fun add(query: String, userId: String?): List<String> {
        val trimmed = query.trim()
        val key = key(userId)
        var updated = emptyList<String>()
        dataStore.edit { preferences ->
            val existing = preferences[key].toSearches()
            if (trimmed.isEmpty()) {
                updated = existing
                return@edit
            }
            updated = (listOf(trimmed) + existing.filter { it != trimmed }).take(RecentSearchStore.MAX_COUNT)
            preferences[key] = updated.joinToString(SEPARATOR)
        }
        return updated
    }

    override suspend fun remove(query: String, userId: String?): List<String> {
        val key = key(userId)
        var updated = emptyList<String>()
        dataStore.edit { preferences ->
            updated = preferences[key].toSearches().filter { it != query }
            preferences[key] = updated.joinToString(SEPARATOR)
        }
        return updated
    }

    override suspend fun clear(userId: String?) {
        dataStore.edit { it.remove(key(userId)) }
    }

    private companion object {
        const val SEPARATOR = "\n"
        const val KEY_PREFIX = "FestiCore.festivalRecentSearches"

        /** iOS와 같은 키 이름. 로그인 `...user.{userId}`, 익명 `...anonymous`. */
        fun key(userId: String?): Preferences.Key<String> =
            stringPreferencesKey(if (userId.isNullOrEmpty()) "$KEY_PREFIX.anonymous" else "$KEY_PREFIX.user.$userId")

        /** iOS `split(separator:)`처럼 빈 조각은 버린다. */
        fun String?.toSearches(): List<String> = this?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
    }
}
