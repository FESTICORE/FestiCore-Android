package io.festicore.domain.festival.sources.zonemap

import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.entity.FestivalZoneMap
import io.festicore.domain.festival.pack.FestivalPackRepository
import io.festicore.domain.festival.sources.model.mapper.toZoneMap
import io.festicore.domain.festival.zonemap.FestivalZoneMapRepository
import io.festicore.domain.festival.zonemap.FestivalZoneMapUseCase
import java.time.Instant

/**
 * 받아 둔 팩에서 존 지도를 읽고 서버 지도 버전과 비교한다. iOS `FestivalZoneMapUseCaseImpl`.
 * 지도 값은 팩을 한 번 해석한 문서에서 읽는다(설계 E4). 문서를 해석하지 못한 팩은 [FestivalPackError.InvalidPack].
 */
internal class FestivalZoneMapUseCaseImpl(
    private val packRepository: FestivalPackRepository,
    private val repository: FestivalZoneMapRepository,
) : FestivalZoneMapUseCase {
    override suspend fun cachedMap(festivalId: String, now: Instant): FestivalZoneMap? {
        val cached = packRepository.cachedPack(festivalId) ?: return null
        val document = cached.document ?: throw FestivalPackError.InvalidPack()
        return document.toZoneMap(festivalId, now)
    }

    override suspend fun isMapChanged(map: FestivalZoneMap): Boolean = repository.latestMapVersion(map.festivalId) != map.version
}
