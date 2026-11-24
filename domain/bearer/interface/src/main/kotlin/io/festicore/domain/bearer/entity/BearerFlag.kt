package io.festicore.domain.bearer.entity

import java.time.Instant

/** 기수가 고르는 활동. 서버 값 `slam` · `sing` · `train` · `calm`. iOS `BearerActivity`. */
enum class BearerActivity(val value: String) {
    Slam("slam"),
    Sing("sing"),
    Train("train"),
    Calm("calm"),
    ;

    companion object {
        /** 모르는 값(쉬는 중 `resting` 포함)은 null. */
        fun from(value: String?): BearerActivity? = entries.firstOrNull { it.value == value }
    }
}

/** 깃발 태그. iOS `BearerFlagTag`. */
enum class BearerFlagTag(val value: String) {
    Slam("slam"),
    Sing("sing"),
    Train("train"),
    Calm("calm"),
    Newbie("newbie"),
    ;

    companion object {
        fun from(value: String?): BearerFlagTag? = entries.firstOrNull { it.value == value }
    }
}

/** 깃발 하루 상태. iOS `BearerFlagLifecycle`. */
enum class BearerFlagLifecycle(val value: String) {
    Scheduled("scheduled"),
    Active("active"),
    Resting("resting"),
    Ended("ended"),
    ;

    companion object {
        fun from(value: String?): BearerFlagLifecycle? = entries.firstOrNull { it.value == value }
    }
}

/** 깃발 현재 상태. iOS `BearerFlagStatus`. */
data class BearerFlagStatus(
    val flagId: String,
    val lifecycle: BearerFlagLifecycle,
    val zoneId: String?,
    /** 쉬는 중이면 서버가 `resting`을 보내므로 null이다. */
    val activity: BearerActivity?,
    val updatedAt: Instant,
    /** 마지막으로 바꾼 기수(공동 기수 포함). 관리자 변경 · 변경 전이면 null이다. */
    val updatedBy: BearerCoBearer? = null,
)

/** 기수가 보는 깃발 프로필. 사진 주소는 https 절대 주소이거나 null이다. iOS `BearerFlagProfile`. */
data class BearerFlagProfile(
    val id: String,
    val name: String,
    val photoUrl: String? = null,
    val thumbnailUrl: String? = null,
    val tags: List<BearerFlagTag> = emptyList(),
    val bio: String? = null,
    val followerCount: Int = 0,
    val planVisible: Boolean = true,
    val plan: List<String> = emptyList(),
    val homeZoneId: String? = null,
)

/** 공동 기수. iOS `BearerCoBearer`. */
data class BearerCoBearer(
    val userId: String,
    val nickname: String,
)

/** 기수 보드(`GET flags/{id}/bearer`). iOS `BearerBoard`. */
data class BearerBoard(
    val profile: BearerFlagProfile,
    val status: BearerFlagStatus,
    val followerCount: Int,
    val coBearers: List<BearerCoBearer> = emptyList(),
    val hidden: Boolean = false,
    val restartUsedToday: Boolean = false,
)
