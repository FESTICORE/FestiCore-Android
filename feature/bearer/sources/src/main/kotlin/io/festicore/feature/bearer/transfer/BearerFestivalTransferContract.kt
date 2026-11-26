package io.festicore.feature.bearer.transfer

import io.festicore.domain.bearer.entity.BearerFestivalOption
import io.festicore.domain.bearer.entity.BearerFestivalTransferRequest
import io.festicore.feature.bearer.register.BearerFestivalList

/** 이 깃발을 옮길 수 있는지. 서버도 같은 규칙으로 거절한다(403 `FLAG_NOT_OWNER` · 409 `FLAG_HAS_CO_BEARERS`). */
sealed interface BearerTransferEligibility {
    data object Loading : BearerTransferEligibility

    data object Eligible : BearerTransferEligibility

    /** 처음 깃발을 만든 원래 기수가 아니다. */
    data object NotOwner : BearerTransferEligibility

    /** 공동 기수가 있다. */
    data object HasCoBearers : BearerTransferEligibility

    data class Failed(val offline: Boolean) : BearerTransferEligibility
}

/**
 * 깃발 축제 이동(Android 먼저, 백엔드 #409). iOS에 아직 없다. 페스티벌 고르기는 깃발 등록(S14 D)과 같다.
 *
 * @param fromFestivalId 깃발의 지금 페스티벌. 고를 목록에서 뺀다.
 * @param isConfirming '계획·주로 있는 존·현장 상태가 초기화돼요' 확인 팝업
 * @param pendingRequest 응답을 받지 못한 마지막 이동. 같은 페스티벌로 다시 보내면 같은 멱등키를 쓴다.
 * @param errorMessage 실패 팝업 문구
 */
data class BearerFestivalTransferState(
    val flagId: String,
    val fromFestivalId: String? = null,
    val eligibility: BearerTransferEligibility = BearerTransferEligibility.Loading,
    val festivals: BearerFestivalList = BearerFestivalList(),
    val selectedFestivalId: String? = null,
    val isConfirming: Boolean = false,
    val isTransferring: Boolean = false,
    val pendingRequest: BearerFestivalTransferRequest? = null,
    val errorMessage: String? = null,
) {
    val selectedFestival: BearerFestivalOption? get() = festivals.option(selectedFestivalId)

    val canTransfer: Boolean
        get() = eligibility == BearerTransferEligibility.Eligible && fromFestivalId != null && selectedFestival != null && !isTransferring
}

sealed interface BearerFestivalTransferSideEffect {
    /** 옮겼다. 부모가 화면을 닫고 깃발 상세를 다시 불러온다. */
    data class Transferred(
        val flagId: String,
        val festivalId: String,
    ) : BearerFestivalTransferSideEffect

    data object CloseRequested : BearerFestivalTransferSideEffect
}
