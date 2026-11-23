package io.festicore.domain.festival.sources.model.mapper

import io.festicore.domain.festival.FestivalFlagDetail
import io.festicore.domain.festival.FestivalFlagDetailError
import io.festicore.domain.festival.FestivalFlagFollowResult
import io.festicore.domain.festival.FestivalIsoTime
import io.festicore.domain.festival.sources.model.dto.FlagDetailDto
import io.festicore.domain.festival.sources.model.dto.FlagFollowDto

/** iOS `FestivalFlagDetailDataDTO.toDomain`. 빈 ID · 이름, 다른 깃발 상태, 모르는 상태, 해석할 수 없는 시각은 [FestivalFlagDetailError.InvalidResponse]. */
internal fun FlagDetailDto.toDomain(imageUrl: (String?) -> String?): FestivalFlagDetail {
    val lifecycle = FestivalFlagDetail.Status.entries.firstOrNull { it.value == status.status }
    val updatedAt = FestivalIsoTime.parse(status.updatedAt)
    if (id.isEmpty() || name.isEmpty() || status.flagId != id || lifecycle == null || updatedAt == null) {
        throw FestivalFlagDetailError.InvalidResponse()
    }
    return FestivalFlagDetail(
        id = id,
        name = name,
        thumbnailUrl = imageUrl(thumbUrl),
        photoUrl = imageUrl(photoUrl),
        tags = tags.filterNotNull(),
        bio = bio,
        followerCount = followerCount.coerceAtLeast(0),
        isPlanVisible = planVisible,
        planSetIds = if (planVisible) plan.filterNotNull() else emptyList(),
        homeZoneId = homeZoneId,
        status = lifecycle,
        zoneId = status.zoneId,
        activity = FestivalFlagDetail.Activity.entries.firstOrNull { it.value == status.activity },
        statusUpdatedAt = updatedAt,
        isFollowing = isFollowing,
        isBearer = isBearer,
        isPinned = isPinned,
        isHidden = hidden,
    )
}

/** iOS `FestivalFlagFollowDataDTO.toDomain`. */
internal fun FlagFollowDto.toDomain() = FestivalFlagFollowResult(flagId = flagId, isFollowing = following, followerCount = followerCount)
