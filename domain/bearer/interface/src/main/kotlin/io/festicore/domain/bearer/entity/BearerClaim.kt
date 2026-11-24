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

/** 업로드한 깃발 사진의 주소. iOS `BearerFlagPhoto`. */
data class BearerFlagPhoto(
    val photoUrl: String?,
    val thumbnailUrl: String?,
)

/** 사진 업로드 진행. 보낸 비율을 몇 번 알린 뒤 완료로 끝난다. iOS `BearerPhotoUploadEvent`. */
sealed interface BearerPhotoUploadEvent {
    /** [fraction]은 0~1 */
    data class Progress(val fraction: Double) : BearerPhotoUploadEvent

    data class Completed(val photo: BearerFlagPhoto) : BearerPhotoUploadEvent
}
