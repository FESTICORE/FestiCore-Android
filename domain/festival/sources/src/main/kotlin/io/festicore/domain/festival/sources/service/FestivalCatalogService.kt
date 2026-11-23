package io.festicore.domain.festival.sources.service

import io.festicore.domain.festival.sources.model.dto.FestivalItemDto
import io.festicore.domain.festival.sources.model.dto.FestivalListDto
import io.festicore.domain.festival.sources.model.dto.FestivalPackDocumentDto

/** 축제 목록 조회 · 캐시. 실패는 `NetworkError` 또는 [FestivalCatalogServiceError]. iOS `FestivalCatalogService`. */
interface FestivalCatalogService {
    fun imageUrl(value: String?): String?

    /** 저장된 목록. 네트워크를 쓰지 않는다. */
    suspend fun cached(): List<FestivalItemDto>

    /** 축제 상세(온라인 팩 문서). 저장하지 않는다. iOS `festivalDetail(for:)`. */
    suspend fun festivalDetail(festivalId: String): FestivalPackDocumentDto

    /** 처음부터 마지막 쪽까지 받아 중복 ID를 빼고 저장한다. */
    suspend fun refresh(): List<FestivalItemDto>

    suspend fun page(after: String?, limit: Int): FestivalListDto
}
