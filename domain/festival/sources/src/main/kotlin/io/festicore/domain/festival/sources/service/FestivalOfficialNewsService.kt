package io.festicore.domain.festival.sources.service

import io.festicore.domain.festival.sources.model.dto.FestivalOfficialNewsDto

/** 축제 공식 소식(인스타그램). iOS `FestivalOfficialNewsService`. */
interface FestivalOfficialNewsService {
    suspend fun officialNews(festivalId: String): FestivalOfficialNewsDto
}
