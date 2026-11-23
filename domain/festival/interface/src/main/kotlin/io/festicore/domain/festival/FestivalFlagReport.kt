package io.festicore.domain.festival

/** 깃발 신고 사유. [value]를 서버에 보낸다. iOS `FestivalFlagReportReason`. */
enum class FestivalFlagReportReason(val value: String) {
    Fake("fake"),
    Inappropriate("inappropriate"),
    Other("other"),
}

/** 깃발 신고 오류. iOS `FestivalFlagReportError`. */
sealed class FestivalFlagReportError : Exception() {
    /** 세션이 없거나 401. */
    class LoginRequired : FestivalFlagReportError()

    /** 403 `TERMS_REQUIRED`. */
    class TermsRequired : FestivalFlagReportError()

    /** 409 `ALREADY_REPORTED`. */
    class AlreadyReported : FestivalFlagReportError()

    /** 404. */
    class NotFound : FestivalFlagReportError()

    /** 경로에 넣을 수 없는 ID. 요청하지 않았다. */
    class InvalidRequest : FestivalFlagReportError()

    /** 응답이 형식에 맞지 않는다. */
    class InvalidResponse : FestivalFlagReportError()

    /** 연결 오류 · 5xx · 429처럼 결과를 알 수 없다. */
    class Unavailable : FestivalFlagReportError()
}

/** 대표 깃발 지정 오류. iOS `FestivalFlagPinError`. */
sealed class FestivalFlagPinError : Exception() {
    /** 세션이 없거나 401. */
    class LoginRequired : FestivalFlagPinError()

    /** 403 `TERMS_REQUIRED`. */
    class TermsRequired : FestivalFlagPinError()

    /** 409 `PIN_NOT_FOLLOWING`. 팔로우하지 않은 깃발이다. */
    class NotFollowing : FestivalFlagPinError()

    /** 경로에 넣을 수 없는 ID. 요청하지 않았다. */
    class InvalidRequest : FestivalFlagPinError()

    /** 응답이 형식에 맞지 않거나 요청과 다른 값이다. */
    class InvalidResponse : FestivalFlagPinError()

    /** 연결 오류 · 5xx · 429처럼 결과를 알 수 없다. */
    class Unavailable : FestivalFlagPinError()
}
