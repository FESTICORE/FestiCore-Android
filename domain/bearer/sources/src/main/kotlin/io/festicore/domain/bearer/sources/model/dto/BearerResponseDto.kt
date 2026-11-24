package io.festicore.domain.bearer.sources.model.dto

import kotlinx.serialization.Serializable

/** 원시 응답(코드 인증)의 `{data}`. 나머지 키는 보지 않는다. */
@Serializable
internal data class BearerEnvelopeDto<T>(val data: T)

/** 깃발 프로필. iOS `BearerFlagProfileDTO`. */
@Serializable
internal data class BearerFlagProfileDto(
    val id: String,
    val name: String,
    val thumbUrl: String? = null,
    val photoUrl: String? = null,
    val tags: List<String>? = null,
    val bio: String? = null,
    val followerCount: Int? = null,
    val planVisible: Boolean? = null,
    val plan: List<String>? = null,
    val homeZoneId: String? = null,
)

/** 깃발 상태. 시각은 ISO-8601 문자열이고 매퍼가 해석한다. iOS `BearerFlagStatusDTO`. */
@Serializable
internal data class BearerFlagStatusDto(
    val flagId: String,
    val status: String,
    val zoneId: String? = null,
    val activity: String? = null,
    val updatedAt: String,
    val updatedBy: BearerCoBearerDto? = null,
)

@Serializable
internal data class BearerCoBearerDto(
    val userId: String,
    val nickname: String? = null,
)

/** `GET flags/{id}/bearer` 응답 `data`. iOS `BearerBoardDataDTO`. */
@Serializable
internal data class BearerBoardDataDto(
    val flag: BearerFlagProfileDto,
    val status: BearerFlagStatusDto,
    val followerCount: Int? = null,
    val coBearers: List<BearerCoBearerDto>? = null,
    val hidden: Boolean? = null,
    val restartUsedToday: Boolean? = null,
)

/** 코드 인증 · 등록 응답 `data`. iOS `BearerClaimDataDTO`. */
@Serializable
internal data class BearerClaimDataDto(
    val flagId: String,
    val flag: BearerFlagProfileDto,
    val isNew: Boolean? = null,
    val coBearers: List<String>? = null,
)

/** 공동 기수 초대 응답 `data`. iOS `BearerInviteDataDTO`. */
@Serializable
internal data class BearerInviteDataDto(
    val code: String,
    val expiresAt: String,
)

/** `GET me/bearer-flags` 응답 `data`. iOS `BearerFlagsDataDTO`. */
@Serializable
internal data class BearerFlagsDataDto(val items: List<Item>) {
    @Serializable
    data class Item(
        val flagId: String,
        val festivalId: String,
        val isOwner: Boolean? = null,
        val joinedAt: String? = null,
        val flag: BearerFlagProfileDto,
        val status: BearerFlagStatusDto? = null,
        val hidden: Boolean? = null,
    )
}

/** `GET me/pinned-flag` 응답 `data`. iOS `BearerPinnedFlagDataDTO`. */
@Serializable
internal data class BearerPinnedFlagDataDto(
    val pinnedFlagId: String? = null,
    val flag: Flag? = null,
) {
    @Serializable
    data class Flag(
        val id: String,
        val name: String,
        val thumbUrl: String? = null,
        val festivalId: String? = null,
    )
}

/** `GET me/follows` 응답 `data`. iOS `BearerFollowsDataDTO`. */
@Serializable
internal data class BearerFollowsDataDto(val items: List<Item>) {
    @Serializable
    data class Item(
        val flagId: String,
        val festivalId: String,
        val followedAt: String? = null,
    )
}

/** 팔로우 목록에 이름을 붙이려고 읽는 공개 상세 일부. iOS `BearerFollowedFlagDetailDTO`. */
@Serializable
internal data class BearerFollowedFlagDetailDto(
    val id: String,
    val name: String,
    val thumbUrl: String? = null,
)

/** 실패 본문. 잠금 남은 시간 · 남은 횟수를 `data`에서 읽는다. iOS `BearerErrorBodyDTO`. */
@Serializable
internal data class BearerErrorBodyDto(
    val code: String? = null,
    val data: Detail? = null,
) {
    @Serializable
    data class Detail(
        val retryAfter: Int? = null,
        val remainingAttempts: Int? = null,
    )
}

/** 사진 업로드 응답. 주소는 서버 상대 경로다. iOS `BearerPhotoResponseDTO`. */
@Serializable
internal data class BearerPhotoResponseDto(
    val photoUrl: String? = null,
    val thumbUrl: String? = null,
)
