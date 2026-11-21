package io.festicore.service.festivalcatalog

import io.festicore.core.network.NetworkError
import io.festicore.service.api.FestivalApi
import io.festicore.service.api.FestivalItemDto
import io.festicore.service.api.FestivalListDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 축제 목록 조회 · 캐시. 실패는 [NetworkError] 또는 [FestivalCatalogServiceError]. iOS `NetworkFestivalCatalogService`.
 */
class FestivalCatalogService(
    private val api: FestivalApi,
    private val cache: FestivalCatalogCache,
    private val imageUrlResolver: FestivalImageUrlResolver,
) {
    private val pageLock = Mutex()
    private var pageItems: List<FestivalItemDto> = emptyList()

    fun imageUrl(value: String?): String? = imageUrlResolver.resolve(value)

    /** 저장된 목록. 네트워크를 쓰지 않는다. */
    suspend fun cached(): List<FestivalItemDto> = cache.load()

    /** 처음부터 [REFRESH_LIMIT]씩 마지막 쪽까지 받아 중복 ID를 빼고 저장한다. 최대 [MAX_PAGES]쪽. */
    suspend fun refresh(): List<FestivalItemDto> {
        val items = mutableListOf<FestivalItemDto>()
        val seenIds = HashSet<String>()
        val seenCursors = HashSet<String>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val page = api.list(after, REFRESH_LIMIT)
            page.items.filterTo(items) { seenIds.add(it.id) }
            val next = page.nextCursor
            if (next == null) {
                cache.save(items)
                return items
            }
            if (!seenCursors.add(next)) throw FestivalCatalogServiceError.RepeatedCursor()
            after = next
        }
        throw FestivalCatalogServiceError.TooManyPages()
    }

    /**
     * 한 쪽을 받는다. 받은 쪽은 그대로 돌려준다.
     * 첫 쪽([after] == null)부터 받은 쪽들을 중복 ID 없이 모아 두고, 마지막 쪽을 받으면 모은 전체를 저장한다.
     * 다음 커서가 [after]와 같으면 [FestivalCatalogServiceError.RepeatedCursor].
     */
    suspend fun page(after: String?, limit: Int): FestivalListDto {
        val page = api.list(after, limit)
        if (after != null && page.nextCursor == after) throw FestivalCatalogServiceError.RepeatedCursor()
        val completed = pageLock.withLock {
            val base = if (after == null) emptyList() else pageItems
            val known = base.mapTo(HashSet()) { it.id }
            pageItems = base + page.items.filter { known.add(it.id) }
            pageItems.takeIf { page.nextCursor == null }
        }
        completed?.let { cache.save(it) }
        return page
    }

    companion object {
        const val REFRESH_LIMIT = 50
        const val MAX_PAGES = 100
    }
}
