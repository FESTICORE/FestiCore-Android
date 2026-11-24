package io.festicore.feature.bearer.profile

import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerSet
import io.festicore.domain.bearer.entity.BearerZone

/**
 * iOS `BearerProfileFeature.State`(Figma S10 A~E).
 *
 * @param isNewBearer 코드로 막 기수가 된 경우. 제목과 저장 버튼 문구가 다르다.
 * @param plan 오늘 계획으로 고른 공연 ID(내 일정에서 고른 셋)
 * @param sets 오늘 계획에서 고를 수 있는 공연(받아 둔 팩)
 * @param isPlanEdited 이 화면에서 오늘 계획을 다시 골랐다. 저장할 때만 계획을 함께 보낸다.
 * @param uploadedPhotoData 마지막으로 올리기에 성공한 사진
 * @param savedProfile 저장을 마치면 완료 화면(E)을 보여 준다.
 */
data class BearerProfileState(
    val festivalId: String,
    val flagId: String,
    val isNewBearer: Boolean = false,
    val festivalName: String? = null,
    val zones: List<BearerZone> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val name: String = "",
    val bio: String = "",
    val tags: List<BearerFlagTag> = emptyList(),
    val homeZoneId: String? = null,
    val planVisible: Boolean = true,
    val plan: List<String> = emptyList(),
    val sets: List<BearerSet> = emptyList(),
    val isPlanEdited: Boolean = false,
    val bearerCount: Int = 1,
    val photoUrl: String? = null,
    val uploadedPhotoData: ByteArray? = null,
    val photoUpload: BearerPhotoUpload = BearerPhotoUpload.Idle,
    val nameError: String? = null,
    val saveError: String? = null,
    val isSaving: Boolean = false,
    val isInviting: Boolean = false,
    val savedProfile: BearerFlagProfile? = null,
    val alert: BearerProfileAlert? = null,
) {
    /** Figma S10 "공동 기수 초대 0 / 3명": 나(원래 기수)를 뺀 공동 기수 수 */
    val coBearerText: String get() = "${maxOf(0, bearerCount - 1)} / ${CO_BEARER_LIMIT}명"

    val isUploadingPhoto: Boolean get() = photoUpload is BearerPhotoUpload.Uploading

    internal val trimmedName: String get() = name.trim()

    val canSave: Boolean
        get() =
            trimmedName.codePointCount(0, trimmedName.length) in NAME_MIN..NAME_MAX &&
                bio.codePointCount(0, bio.length) <= BIO_LIMIT &&
                tags.size in 1..TAG_LIMIT &&
                nameError == null &&
                !isUploadingPhoto &&
                !isSaving &&
                !isLoading

    internal val change: BearerProfileChange
        get() =
            BearerProfileChange(
                name = trimmedName,
                bio = bio,
                tags = tags,
                planVisible = planVisible,
                homeZoneId = homeZoneId,
                plan = if (isPlanEdited) plan else null,
            )

    /** 코드 등록 직후 서버가 붙인 임시 이름(`새 깃발 …`)과 기본 태그는 빈 입력으로 보여 준다. iOS `apply(_:)`. */
    internal fun applying(profile: BearerFlagProfile): BearerProfileState {
        val isPlaceholder = isNewBearer && profile.name.startsWith(PLACEHOLDER_PREFIX)
        return copy(
            name = if (isPlaceholder) "" else profile.name,
            bio = profile.bio ?: "",
            tags = if (isPlaceholder) emptyList() else profile.tags,
            homeZoneId = profile.homeZoneId,
            planVisible = profile.planVisible,
            plan = profile.plan,
            photoUrl = profile.thumbnailUrl ?: profile.photoUrl,
        )
    }

    companion object {
        const val NAME_MIN = 2
        const val NAME_MAX = 20
        const val BIO_LIMIT = 60
        const val TAG_LIMIT = 3

        /** 원래 기수를 뺀 공동 기수 한도(서버 MAX_FLAG_BEARERS 4명 = 원래 기수 1 + 공동 기수 3) */
        const val CO_BEARER_LIMIT = 3

        private const val PLACEHOLDER_PREFIX = "새 깃발 "

        /** - [profile]: 코드 등록 응답의 프로필. 없으면 서버에서 읽는다. iOS `State.init`. */
        fun initial(
            festivalId: String,
            flagId: String,
            profile: BearerFlagProfile? = null,
            isNewBearer: Boolean = false,
        ): BearerProfileState {
            val state = BearerProfileState(festivalId = festivalId, flagId = flagId, isNewBearer = isNewBearer, isLoading = profile == null)
            return if (profile != null) state.applying(profile) else state
        }
    }
}

/** iOS `BearerProfileFeature.PhotoUpload`. */
sealed interface BearerPhotoUpload {
    data object Idle : BearerPhotoUpload

    data class Uploading(
        val data: ByteArray,
        val progress: Double,
    ) : BearerPhotoUpload

    data class Failed(val message: String) : BearerPhotoUpload
}

/** iOS `FCAlertState+Bearer`(`coBearerInvite` · `coBearerInviteFailed`). */
sealed interface BearerProfileAlert {
    data class Invite(val code: String) : BearerProfileAlert

    data class InviteFailed(val message: String) : BearerProfileAlert
}

/** iOS `BearerProfileDelegate`. */
sealed interface BearerProfileSideEffect {
    /** 저장 완료 화면에서 "완료"를 눌렀다. */
    data class Finished(val flagId: String) : BearerProfileSideEffect

    /** 뒤로. 올리던 사진은 취소했다. */
    data object CloseRequested : BearerProfileSideEffect

    /** "오늘 계획"을 눌렀다. 부모는 계획 고르기를 열고 결과를 [BearerProfileViewModel.onPlanChanged]로 넘긴다. */
    data class PlanRequested(val festivalId: String) : BearerProfileSideEffect
}
