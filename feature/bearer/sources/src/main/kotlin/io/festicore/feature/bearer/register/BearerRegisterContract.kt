package io.festicore.feature.bearer.register

import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerFestivalOption
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.entity.BearerRegisterRequest
import io.festicore.domain.bearer.entity.BearerZone
import io.festicore.feature.bearer.profile.BearerProfileState

/**
 * iOS `BearerRegisterFeature.State`(Figma S14 D1 · D2 코드 없는 등록, E1 · E2 저장 완료).
 *
 * @param preferredFestivalId 먼저 골라 둘 페스티벌(라이브 탭에서 연 페스티벌)
 * @param zones 고른 페스티벌의 받은 팩에서 읽은 존. 팩이 없으면 비어 있어 존 고르기를 숨긴다.
 * @param photoData 등록한 뒤 올릴 사진
 * @param pendingRequest 마지막으로 보낸 등록. 같은 내용을 다시 보내면 같은 멱등키를 쓴다.
 */
data class BearerRegisterState(
    val preferredFestivalId: String? = null,
    val festivals: BearerFestivalList = BearerFestivalList(),
    val selectedFestivalId: String? = null,
    val zones: List<BearerZone> = emptyList(),
    val form: BearerRegisterForm = BearerRegisterForm(),
    val photoData: ByteArray? = null,
    val isSaving: Boolean = false,
    val nameError: String? = null,
    val saveError: String? = null,
    val pendingRequest: BearerRegisterRequest? = null,
    val completion: BearerRegisterCompletion? = null,
    val alert: BearerRegisterAlert? = null,
) {
    internal val trimmedName: String get() = form.name.trim()

    val selectedFestival: BearerFestivalOption? get() = festivals.option(selectedFestivalId)

    val canSave: Boolean
        get() =
            selectedFestival != null &&
                trimmedName.codePointCount(0, trimmedName.length) in BearerProfileState.NAME_MIN..BearerProfileState.NAME_MAX &&
                form.bio.codePointCount(0, form.bio.length) <= BearerProfileState.BIO_LIMIT &&
                form.tags.size <= BearerProfileState.TAG_LIMIT &&
                nameError == null &&
                !isSaving

    /** 태그를 고르지 않으면 서버 기본값과 같은 '초보 환영'으로 보낸다. */
    internal val change: BearerProfileChange
        get() =
            BearerProfileChange(
                name = trimmedName,
                bio = form.bio,
                tags = form.tags.ifEmpty { listOf(BearerFlagTag.Newbie) },
                planVisible = form.planVisible,
                homeZoneId = form.homeZoneId,
            )

    companion object {
        /** 처음에 보이는 '앞으로 열리는 페스티벌' 수 */
        const val UPCOMING_PREVIEW_COUNT = 3
    }
}

/** 페스티벌 후보 목록. 받은 팩이 있으면 '내가 받은 팩' · '다른 페스티벌'로 나눠 보인다. iOS `FestivalList`. */
data class BearerFestivalList(
    val load: BearerFestivalLoad = BearerFestivalLoad.Loading,
    val received: List<BearerFestivalOption> = emptyList(),
    val upcoming: List<BearerFestivalOption> = emptyList(),
    val showsAllUpcoming: Boolean = false,
) {
    val visibleUpcoming: List<BearerFestivalOption>
        get() = if (showsAllUpcoming) upcoming else upcoming.take(BearerRegisterState.UPCOMING_PREVIEW_COUNT)

    val canShowMore: Boolean get() = !showsAllUpcoming && upcoming.size > BearerRegisterState.UPCOMING_PREVIEW_COUNT

    val isEmpty: Boolean get() = received.isEmpty() && upcoming.isEmpty()

    internal fun option(id: String?): BearerFestivalOption? = id?.let { target -> (received + upcoming).firstOrNull { it.id == target } }
}

/** iOS `BearerRegisterFeature.Load`. */
sealed interface BearerFestivalLoad {
    data object Loading : BearerFestivalLoad

    data object Loaded : BearerFestivalLoad

    data class Failed(val offline: Boolean) : BearerFestivalLoad
}

/** 입력한 깃발 프로필. iOS `BearerRegisterFeature.Form`. */
data class BearerRegisterForm(
    val name: String = "",
    val bio: String = "",
    val tags: List<BearerFlagTag> = emptyList(),
    val homeZoneId: String? = null,
    val planVisible: Boolean = true,
)

/** 저장 완료(E). 팩이 없으면 팩 받기를 권하고(E1), 있으면 공동 기수 초대를 둔다(E2). iOS `Completion`. */
data class BearerRegisterCompletion(
    val claim: BearerClaim,
    val festival: BearerFestivalOption,
    val photo: BearerRegisterPhoto = BearerRegisterPhoto.None,
    val isInviting: Boolean = false,
)

/** iOS `Completion.PhotoStatus`. 올린 사진 주소는 화면이 쓰지 않아 담지 않는다. */
sealed interface BearerRegisterPhoto {
    data object None : BearerRegisterPhoto

    data object Uploading : BearerRegisterPhoto

    data object Uploaded : BearerRegisterPhoto

    data object Failed : BearerRegisterPhoto
}

/** iOS `FCAlertState+Bearer`(`bearerNameTaken` · `bearerLimit` · `coBearerInvite` · `coBearerInviteFailed`). */
sealed interface BearerRegisterAlert {
    data object NameTaken : BearerRegisterAlert

    data class BearerLimit(val festivalName: String?) : BearerRegisterAlert

    data class Invite(val code: String) : BearerRegisterAlert

    data class InviteFailed(val message: String) : BearerRegisterAlert
}

/** iOS `BearerRegisterDelegate`. */
sealed interface BearerRegisterSideEffect {
    /** 깃발을 만들었다. 부모가 그 페스티벌을 핀한다. */
    data class Registered(
        val flagId: String,
        val festivalId: String,
    ) : BearerRegisterSideEffect

    /** 저장 완료(E1)의 '팩 받기'. 부모가 그 페스티벌 라이브 보드(팩 받기 카드)를 연다. */
    data class PackRequested(val festivalId: String) : BearerRegisterSideEffect

    /** 저장 완료의 '완료' · '나중에 받을게요' */
    data class Finished(val festivalId: String) : BearerRegisterSideEffect

    /** '공동 기수 초대 코드가 있어요'. 부모가 코드 인증(S13 F~I)을 연다. */
    data class ClaimRequested(val festivalId: String) : BearerRegisterSideEffect

    /** 뒤로. 부모가 한 단계 앞 화면(가입 직후면 기수 여부)으로 돌아간다. */
    data object BackRequested : BearerRegisterSideEffect

    /** '나중에 할게요'. 깃발을 만들지 않고 닫는다. */
    data object CloseRequested : BearerRegisterSideEffect
}
