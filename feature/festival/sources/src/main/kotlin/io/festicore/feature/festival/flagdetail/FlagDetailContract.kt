package io.festicore.feature.festival.flagdetail

import io.festicore.domain.festival.entity.FestivalFlagDetail
import io.festicore.domain.festival.entity.FestivalFlagReportReason
import java.time.Instant

/** 화면 본문 단계(iOS `FestivalFlagDetailFeature.State.Content`). */
sealed interface FlagDetailContent {
    data object Loading : FlagDetailContent

    data class Loaded(val detail: FestivalFlagDetail) : FlagDetailContent

    data class Failed(val failure: FlagDetailFailure) : FlagDetailContent
}

/** 첫 조회 실패 종류. */
enum class FlagDetailFailure { NotFound, Unavailable }

/** 화면 아래 짧은 안내(iOS `State.Toast`). 잠금화면 변경 실패는 라이브 액티비티 단계에서 넣는다. */
sealed interface FlagDetailToast {
    data class Followed(val name: String) : FlagDetailToast

    data object FollowLimit : FlagDetailToast

    data object FollowFailed : FlagDetailToast

    data object Reported : FlagDetailToast

    data object AlreadyReported : FlagDetailToast

    data object ReportFailed : FlagDetailToast
}

/** 팔로우 응답으로 바뀐 값. 다음 조회 결과가 오면 비운다. */
data class FlagDetailFollowOverride(
    val isFollowing: Boolean,
    val followerCount: Int,
)

/** 신고 사유 시트(iOS `FestivalFlagReportFeature.State`). */
data class FlagReportSheetState(val selectedReason: FestivalFlagReportReason? = null)

/**
 * S05 깃발 상세(iOS `FestivalFlagDetailFeature.State`). 잠금화면 대표 깃발(pin) 상태는 라이브 액티비티 단계로 뺐다.
 * 응답은 [loadGeneration]이 같을 때만 반영한다.
 */
data class FlagDetailState(
    val flagId: String,
    val context: FlagDetailContext,
    val now: Instant,
    val content: FlagDetailContent = FlagDetailContent.Loading,
    /** 마지막으로 상세를 받은 기기 시각. 오프라인 안내에 쓴다. */
    val lastLoadedAt: Instant? = null,
    /** 받은 상세가 있는데 다시 불러오지 못했다. 마지막 상세를 그대로 보인다. */
    val isOffline: Boolean = false,
    val isFollowPending: Boolean = false,
    val isReportPending: Boolean = false,
    val toast: FlagDetailToast? = null,
    val reportSheet: FlagReportSheetState? = null,
    val followOverride: FlagDetailFollowOverride? = null,
    val loadGeneration: Int = 0,
) {
    val detail: FestivalFlagDetail? get() = (content as? FlagDetailContent.Loaded)?.detail

    val isFollowing: Boolean get() = followOverride?.isFollowing ?: detail?.isFollowing ?: false

    val followerCount: Int get() = followOverride?.followerCount ?: detail?.followerCount ?: 0

    /** 공유 시트로 보내는 깃발 링크. 앱의 링크 처리가 깃발 상세로 연다. */
    val shareUrl: String get() = "festicore://flag/$flagId"
}

/** iOS `FestivalFlagDetailDelegate`(잠금화면 · 뒤로 제외). 로그인 · 약관은 경계로만 알린다. */
sealed interface FlagDetailSideEffect {
    /** 비로그인 팔로우. 부모가 팔로우 로그인 시트를 띄운다(iOS `loginRequested`). */
    data object LoginRequested : FlagDetailSideEffect

    /** 비로그인 신고. 부모가 일반 로그인 시트를 띄운다(iOS `reportLoginRequested`). */
    data object ReportLoginRequested : FlagDetailSideEffect

    /** 약관 재동의가 필요한 팔로우 · 신고. */
    data object TermsAcceptanceRequested : FlagDetailSideEffect

    /** 상세에서 팔로우를 바꿨다. 부모가 깃발 목록 카드에 반영한다. */
    data class FollowChanged(
        val flagId: String,
        val isFollowing: Boolean,
        val followerCount: Int,
    ) : FlagDetailSideEffect

    /** 깃발 링크 공유(iOS `ShareLink`). Screen이 시스템 공유 시트를 연다. */
    data class ShareRequested(
        val url: String,
        val title: String,
    ) : FlagDetailSideEffect
}
