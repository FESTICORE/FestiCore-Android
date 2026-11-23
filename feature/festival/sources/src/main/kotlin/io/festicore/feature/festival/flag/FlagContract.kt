package io.festicore.feature.festival.flag

import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
/** 목록 조회 상태. iOS `FestivalFlagFeature.State.ViewState`. */
enum class FlagViewState {
    Idle,
    Loading,
    Loaded,
    Failed,

    /** iOS `deviceNotRegistered`. Android 라이브 보드 오류에는 아직 대응 값이 없어 화면 문구만 둔다. */
    DeviceNotRegistered,
    InvalidFestivalId,
    InvalidResponse,
}

/** 조회 실패 종류. iOS `FestivalFlagFeature.LoadFailure`. */
enum class FlagLoadFailure { DeviceNotRegistered, InvalidFestivalId, InvalidResponse, Unavailable }

/**
 * S04 깃발 목록. iOS `FestivalFlagFeature.State` + 자식 `FestivalFlagDirectory.State`([directory]).
 * 응답은 [festivalId] · [generation] · [isVisible]이 모두 같을 때만 반영한다.
 */
data class FlagState(
    val festivalId: String? = null,
    val snapshot: FestivalLiveBoardSnapshot? = null,
    val viewState: FlagViewState = FlagViewState.Idle,
    val isVisible: Boolean = false,
    val generation: Int = 0,
    val directory: FlagDirectoryState = FlagDirectoryState(),
) {
    /** 목록을 받지 못해 이전 스냅샷을 보이는 오프라인 상태(Figma S04-E). */
    val isOffline: Boolean get() = viewState == FlagViewState.Failed && snapshot != null

    /** 이전 목록을 보이는 중에 새로고침이 실패했으면 팔로우를 막는다. */
    val isRefreshFailed: Boolean
        get() =
            when (viewState) {
                FlagViewState.Failed,
                FlagViewState.DeviceNotRegistered,
                FlagViewState.InvalidFestivalId,
                FlagViewState.InvalidResponse,
                -> true

                FlagViewState.Idle, FlagViewState.Loading, FlagViewState.Loaded -> false
            }
}

/** iOS `FestivalFlagDirectory.Delegate`. 로그인 · 약관 재동의는 경계로만 알린다. */
sealed interface FlagSideEffect {
    data class DetailRequested(val flagId: String) : FlagSideEffect

    data object LoginRequested : FlagSideEffect

    data object TermsAcceptanceRequested : FlagSideEffect
}
