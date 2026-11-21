package io.festicore.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * 사용자별 최근 검색어. iOS `FestivalRecentSearchStore`(+Live). [userId]가 null이거나 비면 비로그인(익명) 기록이다.
 * 값은 iOS와 같이 검색어를 `\n`으로 이은 문자열 하나로 둔다.
 */
interface RecentSearchStore {
    suspend fun load(userId: String?): List<String>

    /** 앞뒤 공백을 지운 [query]를 맨 앞에 넣고(같은 값은 앞으로 옮김) 최대 [MAX_COUNT]개를 돌려준다. 비면 저장하지 않는다. */
    suspend fun add(query: String, userId: String?): List<String>

    /** [query]와 같은 값을 지우고 남은 목록을 돌려준다. */
    suspend fun remove(query: String, userId: String?): List<String>

    suspend fun clear(userId: String?)

    companion object {
        const val MAX_COUNT = 10
    }
}

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
