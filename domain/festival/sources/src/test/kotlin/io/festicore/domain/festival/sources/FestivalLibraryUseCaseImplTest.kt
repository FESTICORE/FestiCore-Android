package io.festicore.domain.festival.sources
import io.festicore.core.storage.RecentSearchStore
import io.festicore.domain.festival.sources.library.FestivalLibraryUseCaseImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FestivalLibraryUseCaseImplTest {
    /** 사용자 ID별로 부른 값을 기록하는 가짜 저장소. */
    private class FakeRecentSearchStore : RecentSearchStore {
        val searches = mutableMapOf<String?, List<String>>()
        val calls = mutableListOf<String>()

        override suspend fun load(userId: String?): List<String> {
            calls += "load:$userId"
            return searches[userId].orEmpty()
        }

        override suspend fun add(query: String, userId: String?): List<String> {
            calls += "add:$query:$userId"
            return (listOf(query) + searches[userId].orEmpty()).also { searches[userId] = it }
        }

        override suspend fun remove(query: String, userId: String?): List<String> {
            calls += "remove:$query:$userId"
            return searches[userId].orEmpty().filter { it != query }.also { searches[userId] = it }
        }

        override suspend fun clear(userId: String?) {
            calls += "clear:$userId"
            searches.remove(userId)
        }
    }

    @Test
    fun `최근 검색 읽기 · 추가 · 삭제 · 전체 삭제를 같은 사용자 키로 저장소에 넘긴다`() = runTest {
        val store = FakeRecentSearchStore().apply { searches["u1"] = listOf("펜타포트") }
        val useCase = FestivalLibraryUseCaseImpl(store)

        assertEquals(listOf("펜타포트"), useCase.recentSearches("u1"))
        assertEquals(listOf("원더리벳", "펜타포트"), useCase.addRecentSearch("원더리벳", "u1"))
        assertEquals(listOf("원더리벳"), useCase.removeRecentSearch("펜타포트", "u1"))
        useCase.clearRecentSearches("u1")
        assertEquals(emptyList<String>(), useCase.recentSearches(null))

        assertEquals(
            listOf("load:u1", "add:원더리벳:u1", "remove:펜타포트:u1", "clear:u1", "load:null"),
            store.calls,
        )
    }
}
