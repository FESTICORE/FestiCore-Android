package io.festicore.service.api

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/** 크루 생성 본문. iOS는 `nickname`을 보내지 않으므로 두지 않는다. iOS `CrewCreateBody`. */
@Serializable
data class CrewCreateBody(
    val festivalId: String,
    val name: String,
)

/** 내 상태 공유 본문. [zoneId]가 없으면 서버는 존을 바꾸지 않는다. [clientAt]은 [crewClientAt]. iOS `CrewStatusBody`. */
@Serializable
data class CrewStatusBody(
    val zoneId: String? = null,
    val state: String,
    val clientAt: String,
)

/** iOS `CrewRenameBody`. */
@Serializable
data class CrewRenameBody(val name: String)

/** iOS `CrewTransferBody`. */
@Serializable
data class CrewTransferBody(val toUserId: String)

/** iOS `CrewJoinBody`. */
@Serializable
data class CrewJoinBody(val inviteCode: String)

/** iOS `CrewGatherBody`. */
@Serializable
data class CrewGatherBody(val zoneId: String)

/** [response]는 `going`(가는 중) 또는 `ack`(확인). iOS `CrewGatherResponseBody`. */
@Serializable
data class CrewGatherResponseBody(val response: String)

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

private val clientAtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** 서버가 받는 `clientAt`: 밀리초가 있는 ISO 8601 UTC 문자열(`2026-11-23T01:02:03.456Z`). iOS `ISO8601DateFormatter` + `withFractionalSeconds`. */
fun crewClientAt(instant: Instant): String = clientAtFormatter.format(instant)
