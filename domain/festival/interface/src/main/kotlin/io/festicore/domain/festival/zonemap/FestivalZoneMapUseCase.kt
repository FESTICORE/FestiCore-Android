package io.festicore.domain.festival.zonemap
import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.entity.FestivalZoneMap
import java.time.Instant

/** S06 존 지도 데이터. 받아 둔 팩에서 지도를 읽고 서버 지도 버전과 비교한다. iOS `FestivalZoneMapUseCaseInterface`. */
interface FestivalZoneMapUseCase {
    /** 기기에 받은 팩의 존 지도. 팩이 없으면 null, 팩을 읽지 못하면 [FestivalPackError]. [now]에 공연 중인 아티스트를 머리글에 넣는다. */
    suspend fun cachedMap(festivalId: String, now: Instant): FestivalZoneMap?

    /** 서버 발행 팩의 지도 버전이 [map]과 다르면 참이다. */
    suspend fun isMapChanged(map: FestivalZoneMap): Boolean
}
