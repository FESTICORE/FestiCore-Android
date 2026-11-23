package io.festicore.domain.usernotification.entity

/** 내 알림 오류. iOS `UserNotificationError`. */
sealed class UserNotificationError : Exception() {
    /** 로그인 세션이 없거나 요청 중 세션이 바뀌었다(401 포함). 요청을 보내지 않았을 수 있다. */
    class Unauthenticated : UserNotificationError()

    /** `code != "SUCCESS"` · 음수 안 읽은 수 · 시각 형식 오류 · 본문 해석 실패. */
    class InvalidResponse : UserNotificationError()

    /** 연결 실패 · 서버 오류. */
    class Unavailable : UserNotificationError()
}
