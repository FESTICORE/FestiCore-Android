package io.festicore.service.festivalcatalog

import io.festicore.core.network.NetworkError
import io.festicore.domain.festival.sources.model.dto.FestivalItemDto
import io.festicore.domain.festival.sources.model.dto.FestivalListDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackDocumentDto
import io.festicore.domain.festival.sources.service.FestivalCatalogService
import io.festicore.domain.festival.sources.service.FestivalCatalogServiceError
import io.festicore.domain.festival.sources.service.FestivalLiveBoardServiceError
import io.festicore.service.apiendpoint.festival.FestivalRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 축제 목록 조회 · 캐시. 실패는 [NetworkError] 또는 [FestivalCatalogServiceError]. iOS `NetworkFestivalCatalogService`.
 */
class NetworkFestivalCatalogService(
    private val api: FestivalRequest,
    private val cache: FestivalCatalogCache,
    private val imageUrlResolver: FestivalImageUrlResolver,
) : FestivalCatalogService {
    private val pageLock = Mutex()
    private var pageItems: List<FestivalItemDto> = emptyList()

    /** 쪽 순서 세대. 첫 쪽 요청마다 오른다. [pageCursor]는 이 세대에서 다음에 이어 붙일 쪽의 커서다. */
    private var pageGeneration = 0
    private var pageCursor: String? = null

    override fun imageUrl(value: String?): String? = imageUrlResolver.resolve(value)

    /** 저장된 목록. 네트워크를 쓰지 않는다. */
    override suspend fun cached(): List<FestivalItemDto> = cache.load()

    /**
     * 축제 상세(온라인 팩 문서). 저장하지 않는다. ID가 비었거나 `/` `?` `#`가 있거나 응답 ID가 다르면
     * [FestivalLiveBoardServiceError.InvalidResponse], 그 밖의 실패는 [NetworkError]. iOS `festivalDetail(for:)`.
     */
    override suspend fun festivalDetail(festivalId: String): FestivalPackDocumentDto {
        if (festivalId.isEmpty() || festivalId.any { it in "/?#" }) throw FestivalLiveBoardServiceError.InvalidResponse()
        val document = api.detail<FestivalPackDocumentDto>(festivalId)
        if (document.id != festivalId) throw FestivalLiveBoardServiceError.InvalidResponse()
        return document
    }

    /** 처음부터 [REFRESH_LIMIT]씩 마지막 쪽까지 받아 중복 ID를 빼고 저장한다. 최대 [MAX_PAGES]쪽. */
    override suspend fun refresh(): List<FestivalItemDto> {
        val items = mutableListOf<FestivalItemDto>()
        val seenIds = HashSet<String>()
        val seenCursors = HashSet<String>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val page = api.list<FestivalListDto>(after, REFRESH_LIMIT)
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
     * 첫 쪽 요청은 세대를 올린다. 응답이 올 때 요청 때 세대가 아니거나 [after]가 이어 붙일 커서가 아니면
     * (그 사이 첫 쪽을 다시 받았거나 같은 쪽을 이미 붙였다) 받은 쪽만 돌려주고 모으지도 저장하지도 않는다.
     * 다음 커서가 [after]와 같으면 [FestivalCatalogServiceError.RepeatedCursor].
     */
    override suspend fun page(after: String?, limit: Int): FestivalListDto {
        val generation = pageLock.withLock { if (after == null) ++pageGeneration else pageGeneration }
        val page = api.list<FestivalListDto>(after, limit)
        if (after != null && page.nextCursor == after) throw FestivalCatalogServiceError.RepeatedCursor()
        pageLock.withLock {
            if (generation != pageGeneration || (after != null && after != pageCursor)) return page
            val base = if (after == null) emptyList() else pageItems
            val known = base.mapTo(HashSet()) { it.id }
            pageItems = base + page.items.filter { known.add(it.id) }
            pageCursor = page.nextCursor
            // 늦게 끝난 저장이 새 세대의 저장을 덮지 않도록 잠근 채로 저장한다.
            if (page.nextCursor == null) cache.save(pageItems)
        }
        return page
    }

    companion object {
        const val REFRESH_LIMIT = 50
        const val MAX_PAGES = 100
    }
}
