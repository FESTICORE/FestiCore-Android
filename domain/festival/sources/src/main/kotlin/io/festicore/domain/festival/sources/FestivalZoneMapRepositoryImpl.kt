package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalZoneMapRepository
import io.festicore.service.api.FestivalApi

/** 현재 발행 팩의 지도 버전 조회. 인증 없음, 재시도 없음. 실패는 [io.festicore.domain.festival.FestivalLiveBoardError]. iOS `FestivalZoneMapRepositoryImpl`. */
internal class FestivalZoneMapRepositoryImpl(private val api: FestivalApi) : FestivalZoneMapRepository {
    override suspend fun latestMapVersion(festivalId: String): String? = mapLiveBoardErrors { api.packVersion(festivalId).mapVersion }
}
