package io.festicore.feature.bearer.board

import io.festicore.domain.bearer.entity.BearerActivity
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerCoBearer
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerStatusChange
import io.festicore.domain.bearer.entity.BearerStatusRequest
import io.festicore.domain.bearer.entity.BearerZone
import java.time.Instant

/**
 * iOS `BearerBoardFeature.State`(Figma S09 A~J 기수 보드).
 *
 * @param viewerUserId 로그인한 사용자. 다른 공동 기수가 바꿨는지 가리고, 변경의 주인으로 대기열에 넣는다. 없으면 바꾸지 않는다.
 * @param current 화면에 보이는 지금 상태. 보내기 전 변경까지 반영한다.
 * @param lastActivity 쉬는 중에서 다시 시작할 때 쓰는 마지막 활동
 * @param pending 아직 서버에 반영하지 못한 변경. 대기열(`BearerStatusOutbox`)의 목록을 비춘다.
 * @param isSendPaused 로그인 · 서버 오류로 자동 전송이 멈췄다. "다시 보내기"를 눌러야 이어진다.
 * @param lastSyncedAt 서버 상태를 마지막으로 확인한 시각(오프라인 배지 "오프라인 · {상대시각} 정보")
 * @param sheet 어떤 흐름에서 시트를 열었는지
 * @param destination 지금 띄운 시트 · 팝업(iOS `@Presents destination`)
 * @param check 누른 직후 1초 확인 오버레이(Figma S09 D)
 */
data class BearerBoardState(
    val festivalId: String,
    val flagId: String,
    val viewerUserId: String?,
    val board: BearerBoard? = null,
    val festival: BearerFestival? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val current: BearerBoardCurrent? = null,
    val lastActivity: BearerActivity? = null,
    val pending: List<BearerStatusRequest> = emptyList(),
    val isOffline: Boolean = false,
    val isSendPaused: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val errorMessage: String? = null,
    val sheet: BearerBoardSheet? = null,
    val destination: BearerBoardDestination? = null,
    val check: BearerBoardCheck? = null,
    val checkSequence: Int = 0,
) {
    val zones: List<BearerZone> get() = festival?.zones.orEmpty()

    fun zoneName(zoneId: String?): String? = festival?.zoneName(zoneId)

    /** 아래 `FCBearerFooter`의 대기 건수 */
    val queuedCount: Int get() = pending.size

    /** 보드가 없고 실패도 아니면 스켈레톤("기수 보드를 불러오는 중"). 보드가 있으면 다시 읽는 중에도 지금 보드를 유지한다. */
    val showsSkeleton: Boolean get() = board == null && !loadFailed

    /** 오늘 재시작을 이미 썼는지. 보내기 전 재시작도 포함한다. */
    val restartUsedToday: Boolean
        get() = (board?.restartUsedToday ?: false) || pending.any { it.change is BearerStatusChange.Restart }

    /** 다른 공동 기수가 마지막으로 바꿨으면 그 기수 */
    val changedByOtherBearer: BearerCoBearer?
        get() = current?.updatedBy?.takeIf { it.userId != viewerUserId }

    /** 서버 상태를 지금 상태로 받는다. iOS `adopt(_:into:)`. */
    internal fun adopting(status: BearerFlagStatus): BearerBoardState =
        copy(current = BearerBoardCurrent.of(status), lastActivity = status.activity ?: lastActivity)

    /** 보내지 못한 변경을 지금 상태 위에 차례로 얹는다. iOS `applyPending(_:)`. */
    internal fun applyingPending(): BearerBoardState {
        val base = current ?: return this
        return copy(current = pending.fold(base) { acc, request -> acc.applying(request.change, request.clientAt) })
    }
}

/** iOS `BearerBoardFeature.Current`. */
data class BearerBoardCurrent(
    val lifecycle: BearerFlagLifecycle,
    val zoneId: String?,
    val activity: BearerActivity?,
    val updatedAt: Instant,
    val updatedBy: BearerCoBearer?,
) {
    /** 변경 하나를 반영한다. 내가 바꾼 것이라 바꾼 사람은 지운다. iOS `apply(_:to:at:)`. */
    internal fun applying(change: BearerStatusChange, at: Instant): BearerBoardCurrent {
        val changed =
            when (change) {
                is BearerStatusChange.Start ->
                    copy(lifecycle = BearerFlagLifecycle.Active, zoneId = change.zoneId, activity = change.activity)

                is BearerStatusChange.Restart ->
                    copy(lifecycle = BearerFlagLifecycle.Active, zoneId = change.zoneId, activity = change.activity)

                is BearerStatusChange.Move -> copy(zoneId = change.zoneId)

                is BearerStatusChange.Activity -> copy(lifecycle = BearerFlagLifecycle.Active, activity = change.activity)

                BearerStatusChange.Rest -> copy(lifecycle = BearerFlagLifecycle.Resting, activity = null)

                BearerStatusChange.End -> copy(lifecycle = BearerFlagLifecycle.Ended, zoneId = null, activity = null)
            }
        return changed.copy(updatedAt = at, updatedBy = null)
    }

    companion object {
        fun of(status: BearerFlagStatus): BearerBoardCurrent = BearerBoardCurrent(
            lifecycle = status.lifecycle,
            zoneId = status.zoneId,
            activity = status.activity,
            updatedAt = status.updatedAt,
            updatedBy = status.updatedBy,
        )
    }
}

/** 어떤 흐름에서 시트를 열었는지. iOS `BearerBoardFeature.Sheet`. */
sealed interface BearerBoardSheet {
    data object Move : BearerBoardSheet

    data object ChangeActivity : BearerBoardSheet

    data object StartZone : BearerBoardSheet

    data class StartActivity(val zoneId: String) : BearerBoardSheet

    data object RestartZone : BearerBoardSheet

    data class RestartActivity(val zoneId: String) : BearerBoardSheet
}

/** 지금 띄운 시트 · 팝업. iOS `BearerBoardFeature.Destination`(존 시트 · 활동 시트는 iOS에서도 고르면 알리기만 하는 Feature라 값만 둔다). */
sealed interface BearerBoardDestination {
    /** 존 시트(iOS `BearerZonePickerFeature`). 고르면 [BearerBoardViewModel.onZonePicked]. */
    data class ZonePicker(
        val zones: List<BearerZone>,
        val selectedZoneId: String?,
    ) : BearerBoardDestination

    /** 활동 시트(iOS `BearerActivityPickerFeature`). 고르면 [BearerBoardViewModel.onActivityPicked]. */
    data class ActivityPicker(val selected: BearerActivity?) : BearerBoardDestination

    /** 종료 확인(iOS `FCConfirmAlert.bearerEndToday`). 확인 → [BearerBoardViewModel.onEndConfirmed]. */
    data object EndConfirm : BearerBoardDestination

    /** 못 보낸 변경이 있을 때 닫기 확인(iOS `bearerClosePending(count:)`). 확인 → [BearerBoardViewModel.onCloseConfirmed]. */
    data class CloseConfirm(val count: Int) : BearerBoardDestination
}

/** Figma S09 D 누른 직후 1초 확인. iOS `BearerBoardFeature.Check`. */
data class BearerBoardCheck(
    val id: Int,
    val title: String,
    val message: String,
)

/** iOS `BearerBoardDelegate` + 화면이 처리할 플랫폼 일. */
sealed interface BearerBoardSideEffect {
    /** 닫기(상단 · 시스템 뒤로). 대기열은 닫아도 이어 보낸다. */
    data object CloseRequested : BearerBoardSideEffect

    /** 확인 오버레이를 띄웠다. 화면이 성공 햅틱을 낸다(iOS `.sensoryFeedback(.success, trigger: check)`). */
    data object CheckHaptic : BearerBoardSideEffect
}
