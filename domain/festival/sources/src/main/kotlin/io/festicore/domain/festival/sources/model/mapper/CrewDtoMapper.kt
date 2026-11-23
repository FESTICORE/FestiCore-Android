package io.festicore.domain.festival.sources.model.mapper

import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewError
import io.festicore.domain.festival.entity.FestivalCrewGatherReply
import io.festicore.domain.festival.entity.FestivalCrewGatherResponse
import io.festicore.domain.festival.entity.FestivalCrewInvite
import io.festicore.domain.festival.entity.FestivalCrewInviteCode
import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.sources.model.dto.CrewDto
import io.festicore.domain.festival.sources.model.dto.CrewGatherReplyDto
import io.festicore.domain.festival.sources.model.dto.CrewInviteCodeDto
import io.festicore.domain.festival.sources.model.dto.CrewInviteDto
import java.time.Instant

/** iOS `FestivalCrewDataDTO.toDomain()`. 빈 ID · 이름, 틀린 날짜, 다른 크루의 live는 응답 전체를 거부한다. */
internal fun CrewDto.toDomain(): FestivalCrew {
    if (id.isEmpty() || festivalId.isEmpty() || name.isEmpty() || ownerId.isEmpty()) throw FestivalCrewError.InvalidResponse()
    val members = members.map { member ->
        val joinedAt = FestivalIsoTime.parse(member.joinedAt)
        if (member.userId.isEmpty() || member.nickname.isEmpty() || joinedAt == null) throw FestivalCrewError.InvalidResponse()
        FestivalCrew.Member(userId = member.userId, nickname = member.nickname, joinedAt = joinedAt)
    }
    if (live != null && live?.id != id) throw FestivalCrewError.InvalidResponse()
    return FestivalCrew(
        id = id,
        festivalId = festivalId,
        name = name,
        ownerId = ownerId,
        members = members,
        inviteCode = inviteCode,
        inviteExpiresAt = optionalDate(inviteExpiresAt),
        live = live?.toDomain(),
        archivedAt = optionalDate(archivedAt),
    )
}

/** iOS `FestivalCrewInviteDataDTO.toDomain(code:)`. 이름이 빈 미리보기 줄은 버린다. */
internal fun CrewInviteDto.toDomain(code: String): FestivalCrewInvite {
    val expiresAt = FestivalIsoTime.parse(expiresAt)
    if (crewName.isEmpty() || festivalId.isEmpty() || memberCount < 0 || expiresAt == null) throw FestivalCrewError.InvalidResponse()
    return FestivalCrewInvite(
        code = code,
        crewName = crewName,
        festivalId = festivalId,
        festivalName = festivalName,
        inviterNickname = ownerNickname,
        memberCount = memberCount,
        expiresAt = expiresAt,
        memberPreview = memberPreview.orEmpty()
            .filter { it.nickname.isNotEmpty() }
            .map { FestivalCrewInvite.Member(nickname = it.nickname, avatarUrl = it.avatarUrl) },
    )
}

/** iOS `FestivalCrewInviteCodeDataDTO.toDomain()`. */
internal fun CrewInviteCodeDto.toDomain(): FestivalCrewInviteCode {
    val expiresAt = FestivalIsoTime.parse(inviteExpiresAt)
    if (inviteCode.isEmpty() || expiresAt == null) throw FestivalCrewError.InvalidResponse()
    return FestivalCrewInviteCode(code = inviteCode, expiresAt = expiresAt)
}

/** iOS `FestivalCrewGatherReplyDataDTO.toDomain()`. */
internal fun CrewGatherReplyDto.toDomain(): FestivalCrewGatherReply {
    val value = FestivalCrewGatherResponse.of(response)
    val respondedAt = FestivalIsoTime.parse(respondedAt)
    if (gatherId.isEmpty() || userId.isEmpty() || value == null || respondedAt == null) throw FestivalCrewError.InvalidResponse()
    return FestivalCrewGatherReply(gatherId = gatherId, userId = userId, nickname = nickname, response = value, respondedAt = respondedAt)
}

/** 값이 있으면 반드시 날짜여야 한다. 형식이 틀리면 응답 전체를 거부한다. */
private fun optionalDate(value: String?): Instant? = value?.let { FestivalIsoTime.parse(it) ?: throw FestivalCrewError.InvalidResponse() }
