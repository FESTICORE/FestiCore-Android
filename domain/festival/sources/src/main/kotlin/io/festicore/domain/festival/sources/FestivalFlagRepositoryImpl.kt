package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalFlagFollowResult
import io.festicore.domain.festival.FestivalFlagRepository
import io.festicore.service.festivalcatalog.FestivalFlagFollowService

/** 팔로우 서비스 결과를 엔티티로, 오류를 [io.festicore.domain.festival.FestivalFlagFollowError]로 바꾼다. */
internal class FestivalFlagRepositoryImpl(private val service: FestivalFlagFollowService) : FestivalFlagRepository {
    override suspend fun setFollowing(following: Boolean, flagId: String): FestivalFlagFollowResult = try {
        service.setFollowing(following, flagId).toDomain()
    } catch (e: Exception) {
        throw e.toFestivalFlagFollowError()
    }
}
