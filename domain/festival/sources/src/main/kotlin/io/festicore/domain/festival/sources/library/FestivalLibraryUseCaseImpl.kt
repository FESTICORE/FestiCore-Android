package io.festicore.domain.festival.sources.library

import io.festicore.core.storage.RecentSearchStore
import io.festicore.domain.festival.library.FestivalLibraryUseCase
/** iOS `FestivalLibraryUseCaseImpl`(최근 검색 부분). 규칙(앞에 추가 · 중복 제거 · 10개 · 공백 무시)은 [RecentSearchStore]가 지킨다. */
internal class FestivalLibraryUseCaseImpl(private val recentSearchStore: RecentSearchStore) : FestivalLibraryUseCase {
    override suspend fun recentSearches(userId: String?): List<String> = recentSearchStore.load(userId)

    override suspend fun addRecentSearch(query: String, userId: String?): List<String> = recentSearchStore.add(query, userId)

    override suspend fun removeRecentSearch(query: String, userId: String?): List<String> = recentSearchStore.remove(query, userId)

    override suspend fun clearRecentSearches(userId: String?) = recentSearchStore.clear(userId)
}
