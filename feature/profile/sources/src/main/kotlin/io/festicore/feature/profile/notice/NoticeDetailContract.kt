package io.festicore.feature.profile.notice

import io.festicore.domain.notice.entity.NoticeDetail

/**
 * 공지 상세 화면 상태. iOS `NoticeDetailFeature.State`.
 *
 * @param id 읽을 공지 id.
 * @param title 목록에서 고른 공지 제목. 상단 바 제목은 iOS처럼 "공지사항"이다.
 */
data class NoticeDetailState(
    val id: String,
    val title: String,
    val load: NoticeLoad = NoticeLoad.Idle,
    val detail: NoticeDetail? = null,
)

/** iOS `NoticeDetailFeature.DelegateAction`. */
sealed interface NoticeDetailSideEffect {
    /** 공지 목록으로 돌아간다. */
    data object BackRequested : NoticeDetailSideEffect
}
