package io.festicore.domain.festival

/** 팔로우 · 언팔로우 결과. iOS `FestivalFlagFollowResult`. */
data class FestivalFlagFollowResult(
    val flagId: String,
    val isFollowing: Boolean,
    val followerCount: Int,
)

/** 팔로우 오류. iOS `FestivalFlagFollowError`. */
sealed class FestivalFlagFollowError : Exception() {
    /** 세션이 없거나 401. */
    class LoginRequired : FestivalFlagFollowError()

    /** 403 `TERMS_REQUIRED`. */
    class TermsRequired : FestivalFlagFollowError()

    /** 409 `FOLLOW_LIMIT`. 다른 409(`IDEMPOTENCY_CONFLICT` 등)는 [Unavailable]. */
    class LimitReached : FestivalFlagFollowError()

    /** 404. */
    class NotFound : FestivalFlagFollowError()

    /** 응답이 형식에 맞지 않거나 다른 깃발이다. 경로에 넣을 수 없는 ID도 iOS처럼 여기로 보낸다(요청하지 않음). */
    class InvalidResponse : FestivalFlagFollowError()

    /** 요청을 만들 수 없다. iOS `invalidRequest`. */
    class InvalidRequest : FestivalFlagFollowError()

    /** 연결 오류 · 5xx · 429처럼 결과를 알 수 없다. */
    class Unavailable : FestivalFlagFollowError()
}
