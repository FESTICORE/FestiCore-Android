package io.festicore.domain.bearer.entity

import java.time.Instant

/** 코드 없는 등록 한 건. 같은 내용을 다시 보낼 때는 같은 값(같은 멱등키)을 쓴다. iOS `BearerRegisterRequest`. */
data class BearerRegisterRequest(
    val festivalId: String,
    val change: BearerProfileChange,
    val idempotencyKey: String,
)

/** 내가 기수인 깃발(`GET me/bearer-flags`). iOS `BearerOwnedFlag`. */
data class BearerOwnedFlag(
    val festivalId: String,
    /** 처음 깃발을 만든 원래 기수인지 */
    val isOwner: Boolean,
    val joinedAt: Instant?,
    val profile: BearerFlagProfile,
    /** 서버가 상태를 주지 않았거나 모르는 값이면 null이다. */
    val lifecycle: BearerFlagLifecycle?,
    val isHidden: Boolean,
) {
    val id: String get() = profile.id
}

/** 내 대표 깃발(`GET me/pinned-flag`). iOS `BearerPinnedFlag`. */
data class BearerPinnedFlag(
    val flagId: String,
    val name: String,
    val festivalId: String?,
    val thumbnailUrl: String?,
)

/** 팔로우한 깃발(`GET me/follows` + 공개 상세 이름). iOS `BearerFollowedFlag`. */
data class BearerFollowedFlag(
    val flagId: String,
    val festivalId: String,
    val name: String,
    val thumbnailUrl: String?,
    /** 팔로우 시각. 형식을 읽지 못하면 null이다. */
    val followedAt: Instant?,
)

/** 등록 화면 페스티벌 한 줄. iOS `BearerFestivalOption`. */
data class BearerFestivalOption(
    val id: String,
    val name: String,
    /** 예) `11/20-22`. 날짜를 모르면 null이다. */
    val dateText: String?,
    val hasPack: Boolean,
) {
    /** 목록 한 줄 문구. 예) `원더리벳 2026 · 11/20-22` */
    val title: String get() = dateText?.let { "$name · $it" } ?: name
}
