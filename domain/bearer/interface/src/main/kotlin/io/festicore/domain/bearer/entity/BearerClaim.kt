package io.festicore.domain.bearer.entity

import java.time.Instant

/** 코드 인증 · 코드 없는 등록 결과. iOS `BearerClaim`. */
data class BearerClaim(
    val flagId: String,
    val profile: BearerFlagProfile,
    /** 이 사용자가 깃발을 새로 만든 원래 기수인지 */
    val isNew: Boolean,
    val coBearerIds: List<String>,
)

/** 공동 기수 초대 코드. iOS `BearerInvite`. */
data class BearerInvite(
    val code: String,
    val expiresAt: Instant,
)

/** 프로필 수정 · 등록 입력. iOS `BearerProfileChange`. */
data class BearerProfileChange(
    val name: String,
    val bio: String?,
    val tags: List<BearerFlagTag>,
    val planVisible: Boolean,
    val homeZoneId: String?,
    /** 바꾼 오늘 계획(셋 ID). 고르지 않았으면 null이라 서버 값을 건드리지 않는다. */
    val plan: List<String>? = null,
)
