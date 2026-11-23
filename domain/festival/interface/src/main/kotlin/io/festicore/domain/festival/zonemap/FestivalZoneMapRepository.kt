package io.festicore.domain.festival.zonemap

import io.festicore.domain.festival.entity.FestivalLiveBoardError

/** 존 지도 서버 경계. 현재 발행 팩의 지도 버전만 조회한다. iOS `FestivalZoneMapRepositoryInterface`. */
interface FestivalZoneMapRepository {
    /** `GET api/v1/festivals/{id}/pack/version`의 `mapVersion`. 실패는 [FestivalLiveBoardError]. */
    suspend fun latestMapVersion(festivalId: String): String?
}
