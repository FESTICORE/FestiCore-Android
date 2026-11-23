package io.festicore.feature.profile.notice

import io.festicore.domain.notice.entity.NoticeSummary

/** 공지 읽기 상태. iOS `NoticeListFeature.Load` · `NoticeDetailFeature.Load`. */
enum class NoticeLoad { Idle, Loading, Loaded, Failed }

/** 공지 목록 화면 상태. iOS `NoticeListFeature.State`. 상세 화면은 Coordinator가 연다(iOS `destination`). */
data class NoticeListState(
    val load: NoticeLoad = NoticeLoad.Idle,
    val notices: List<NoticeSummary> = emptyList(),
)

/** iOS `NoticeListFeature.DelegateAction` · `noticeTapped`의 상세 열기. */
sealed interface NoticeListSideEffect {
    /** 설정 홈으로 돌아간다. */
    data object BackRequested : NoticeListSideEffect

    /** 목록에 있는 공지의 상세를 연다. */
    data class NoticeDetailRequested(
        val id: String,
        val title: String,
    ) : NoticeListSideEffect
}
