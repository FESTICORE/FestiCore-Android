package io.festicore.service.api

import kotlinx.serialization.Serializable

/** 크루 생성 · 상세 · 이름 변경 · 이전 · 참여 응답 data. [live]는 상세 · 참여 응답에만 온다. iOS `FestivalCrewDataDTO`. */
@Serializable
data class CrewDto(
    val id: String,
    val festivalId: String,
    val name: String,
    val ownerId: String,
    val members: List<Member>,
    val inviteCode: String? = null,
    val inviteExpiresAt: String? = null,
    val archivedAt: String? = null,
    val live: FestivalLiveCrewDto? = null,
) {
    @Serializable
    data class Member(
        val userId: String,
        val nickname: String,
        val joinedAt: String,
    )
}

/** 로그인 없이 보는 초대 미리보기 data. [memberPreview]는 이전 서버 응답에 없다. iOS `FestivalCrewInviteDataDTO`. */
@Serializable
data class CrewInviteDto(
    val crewName: String,
    val festivalId: String,
    val festivalName: String? = null,
    val ownerNickname: String? = null,
    val memberCount: Int,
    val expiresAt: String,
    val memberPreview: List<MemberPreview>? = null,
) {
    @Serializable
    data class MemberPreview(
        val nickname: String,
        val avatarUrl: String? = null,
    )
}

/** 초대 재생성 응답 data. iOS `FestivalCrewInviteCodeDataDTO`. */
@Serializable
data class CrewInviteCodeDto(
    val inviteCode: String,
    val inviteExpiresAt: String,
)

/** 상태 공유에서 서버가 확정한 내 상태. iOS `FestivalCrewStatusDataDTO`. */
@Serializable
data class CrewStatusDto(
    val userId: String,
    val state: String? = null,
)

/** 모여 요청에서 서버가 확정한 존 · 모여 ID. [id]는 이전 서버 응답에 없다. iOS `FestivalCrewGatherDataDTO`. */
@Serializable
data class CrewGatherDto(
    val id: String? = null,
    val zoneId: String,
)

/** 모여 응답에서 서버가 확정한 내 응답. iOS `FestivalCrewGatherReplyDataDTO`. */
@Serializable
data class CrewGatherReplyDto(
    val gatherId: String,
    val userId: String,
    val nickname: String,
    val response: String,
    val respondedAt: String,
)
