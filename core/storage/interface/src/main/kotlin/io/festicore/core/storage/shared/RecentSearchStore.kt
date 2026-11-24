package io.festicore.core.storage.shared

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
