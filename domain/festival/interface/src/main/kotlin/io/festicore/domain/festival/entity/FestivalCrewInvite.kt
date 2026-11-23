package io.festicore.domain.festival.entity

import java.time.Instant

/** 초대 코드로 미리 본 크루. 로그인 없이 받는 공개 정보만 담는다. iOS `FestivalCrewInvite`. */
data class FestivalCrewInvite(
    val code: String,
    val crewName: String,
    val festivalId: String,
    val festivalName: String?,
    val inviterNickname: String?,
    val memberCount: Int,
    val expiresAt: Instant,
    /** 서버가 주는 최대 3명. 이전 서버 응답이면 비어 있다. */
    val memberPreview: List<Member> = emptyList(),
) {
    /** 먼저 들어온 크루원. 식별 정보 없이 닉네임과 프로필 이미지 주소만 받는다. */
    data class Member(
        val nickname: String,
        val avatarUrl: String?,
    )
}

/** 크루장이 새로 받은 초대 코드. 이전 코드는 서버에서 바로 무효가 된다. iOS `FestivalCrewInviteCode`. */
data class FestivalCrewInviteCode(
    val code: String,
    val expiresAt: Instant,
)

/** 크루원이 한 번에 공유하는 내 상태. iOS `FestivalCrewState`. */
enum class FestivalCrewState(val rawValue: String) {
    HERE("here"),
    EATING("eating"),
    TOILET("toilet"),
    LEAVING("leaving"),
}
