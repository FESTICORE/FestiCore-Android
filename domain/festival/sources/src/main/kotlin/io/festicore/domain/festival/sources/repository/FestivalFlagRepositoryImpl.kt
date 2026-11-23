package io.festicore.domain.festival.sources.repository

import io.festicore.domain.festival.entity.FestivalFlagFollowResult
import io.festicore.domain.festival.flagdetail.FestivalFlagRepository
import io.festicore.domain.festival.sources.model.mapper.toDomain
import io.festicore.domain.festival.sources.model.mapper.toFestivalFlagFollowError
import io.festicore.domain.festival.sources.service.FestivalFlagFollowService

/** 팔로우 서비스 결과를 엔티티로, 오류를 [io.festicore.domain.festival.entity.FestivalFlagFollowError]로 바꾼다. */
internal class FestivalFlagRepositoryImpl(private val service: FestivalFlagFollowService) : FestivalFlagRepository {
    override suspend fun setFollowing(following: Boolean, flagId: String): FestivalFlagFollowResult = try {
        service.setFollowing(following, flagId).toDomain()
    } catch (e: Exception) {
        throw e.toFestivalFlagFollowError()
    }
}
