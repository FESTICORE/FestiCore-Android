package io.festicore.domain.festival.sources.service

import io.festicore.domain.festival.sources.model.dto.FlagFollowDto

/** 깃발 팔로우 · 해제. iOS `FestivalFlagFollowService`. */
interface FestivalFlagFollowService {
    suspend fun setFollowing(following: Boolean, flagId: String): FlagFollowDto
}
