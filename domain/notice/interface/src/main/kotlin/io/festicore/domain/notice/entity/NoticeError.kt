package io.festicore.domain.notice.entity

/** 공지 오류. iOS `NoticeError`. */
sealed class NoticeError : Exception() {
    /** `code != "SUCCESS"` · 게시 시각 형식 오류 · 본문 해석 실패. */
    class InvalidResponse : NoticeError()

    /** 연결 실패 · 서버 오류(없는 공지 404 포함). */
    class Unavailable : NoticeError()
}
