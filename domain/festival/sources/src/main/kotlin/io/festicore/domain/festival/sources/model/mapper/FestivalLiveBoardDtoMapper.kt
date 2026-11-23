package io.festicore.domain.festival.sources.model.mapper

import io.festicore.domain.festival.entity.FestivalCrewGatherResponse
import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.entity.FestivalLiveBoardError
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.sources.model.dto.FestivalFlagProfileDto
import io.festicore.domain.festival.sources.model.dto.FestivalLiveBoardDto
import io.festicore.domain.festival.sources.model.dto.FestivalLiveCrewDto
import io.festicore.domain.festival.sources.model.dto.FestivalLiveFlagDto
import java.time.Instant

/** 라이브 목록이 담는 깃발 상한. 닿으면 빠진 깃발의 상태를 알 수 없다. */
internal const val ACTIVE_FLAG_LIMIT = 50

/**
 * 라이브 상태와 공개 깃발 프로필을 합친다. iOS `FestivalLiveBoardDataDTO.toDomain(profiles:imageURL:receivedAt:)`.
 * `serverAt`이나 활성 깃발의 `updatedAt`을 해석하지 못하면 [FestivalLiveBoardError.InvalidResponse].
 */
internal fun FestivalLiveBoardDto.toDomain(
    profiles: List<FestivalFlagProfileDto>,
    imageUrl: (String?) -> String?,
    receivedAt: Instant? = null,
): FestivalLiveBoardSnapshot {
    val serverTime = FestivalIsoTime.parse(serverAt) ?: throw FestivalLiveBoardError.InvalidResponse()
    val profileById = profiles.associateByFirst { it.id }
    val active = activeFlags.map { flag ->
        val updatedAt = FestivalIsoTime.parse(flag.updatedAt) ?: throw FestivalLiveBoardError.InvalidResponse()
        flag(flag.flagId, flag, profileById[flag.flagId], imageUrl, updatedAt)
    }
    val activeById = active.associateByFirst { it.id }
    val followed = me?.following.orEmpty().map { id -> activeById[id] ?: flag(id, null, profileById[id], imageUrl, null) }
    val missingStatus = if (activeFlags.size < ACTIVE_FLAG_LIMIT) "scheduled" else "unknown"
    val seen = HashSet<String>()
    val directory = profiles.mapNotNull { profile ->
        if (!seen.add(profile.id)) return@mapNotNull null
        activeById[profile.id] ?: flag(profile.id, null, profile, imageUrl, null, missingStatus)
    }
    return FestivalLiveBoardSnapshot(
        festivalId = festivalId,
        activeFlags = active,
        followedFlags = followed,
        crew = me?.crew?.toDomain(),
        serverAt = serverTime,
        pinnedFlagId = me?.pinnedFlagId,
        directoryFlags = directory,
        receivedAt = receivedAt,
        bearerFlagIds = me?.bearerOf.orEmpty(),
    )
}

private fun flag(
    id: String,
    status: FestivalLiveFlagDto?,
    profile: FestivalFlagProfileDto?,
    imageUrl: (String?) -> String?,
    updatedAt: Instant?,
    missingStatus: String = "unknown",
) = FestivalLiveBoardSnapshot.Flag(
    id = id,
    name = profile?.name ?: id,
    status = status?.status ?: missingStatus,
    zoneId = status?.zoneId,
    activity = status?.activity,
    thumbnailUrl = imageUrl(profile?.thumbUrl),
    tags = profile?.tags.orEmpty(),
    updatedAt = updatedAt,
    followerCount = profile?.followerCount,
    homeZoneId = profile?.homeZoneId,
    plannedSetIds = profile?.plan.orEmpty(),
)

/** 크루원 존 · 상태. 모여 응답은 모르는 응답 값이나 날짜가 틀린 줄만 버린다. iOS `FestivalLiveBoardCrewDTO.toDomain()`. */
internal fun FestivalLiveCrewDto.toDomain() = FestivalLiveBoardSnapshot.Crew(
    id = id,
    name = name,
    members = members.map {
        FestivalLiveBoardSnapshot.Crew.Member(
            id = it.userId,
            nickname = it.nickname,
            state = it.state,
            zoneId = it.zoneId,
            updatedAt = FestivalIsoTime.parse(it.updatedAt),
        )
    },
    ownerId = ownerId,
    gatherRequest = gatherRequest?.let { request ->
        FestivalIsoTime.parse(request.at)?.let { at ->
            FestivalLiveBoardSnapshot.Crew.GatherRequest(
                id = request.id,
                zoneId = request.zoneId,
                byUserId = request.byUserId,
                at = at,
                expiresAt = FestivalIsoTime.parse(request.expiresAt),
                responses = request.responses.orEmpty().mapNotNull { response ->
                    val value = FestivalCrewGatherResponse.of(response.response) ?: return@mapNotNull null
                    val respondedAt = FestivalIsoTime.parse(response.respondedAt) ?: return@mapNotNull null
                    FestivalLiveBoardSnapshot.Crew.GatherRequest.Response(response.userId, response.nickname, value, respondedAt)
                },
                serverSummary = request.responseSummary?.let { FestivalLiveBoardSnapshot.Crew.GatherRequest.Summary(it.going, it.ack) },
            )
        }
    },
    gatherAvailableAt = FestivalIsoTime.parse(gatherAvailableAt),
)

/** 같은 키는 처음 것만 남긴다(iOS `uniquingKeysWith: { first, _ in first }`). */
private inline fun <T> List<T>.associateByFirst(key: (T) -> String): Map<String, T> {
    val map = HashMap<String, T>()
    forEach { map.putIfAbsent(key(it), it) }
    return map
}
