package io.festicore.domain.festival

/**
 * 사용자별 최근 검색어. [userId]가 null이면 비로그인 기록이다. iOS `FestivalLibraryUseCaseInterface`(최근 검색 부분).
 * 예매 축제(`bookedFestivalIDs`)는 북마크 단계에서 붙인다.
 */
interface FestivalLibraryUseCase {
    /** 기기에 저장된 최근 검색어. 최신이 앞이다. */
    suspend fun recentSearches(userId: String?): List<String>

    /** 검색어를 맨 앞에 넣고 바뀐 목록을 돌려준다. 공백뿐이면 저장하지 않는다. */
    suspend fun addRecentSearch(query: String, userId: String?): List<String>

    /** 검색어를 지우고 바뀐 목록을 돌려준다. */
    suspend fun removeRecentSearch(query: String, userId: String?): List<String>

    /** 최근 검색어를 모두 지운다. */
    suspend fun clearRecentSearches(userId: String?)
}
