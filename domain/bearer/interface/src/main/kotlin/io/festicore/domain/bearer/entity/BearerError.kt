package io.festicore.domain.bearer.entity

/** 기수 오류. 값이 없는 오류는 같은 종류끼리 같다고 본다. iOS `BearerError`. */
sealed class BearerError : Exception() {
    override fun equals(other: Any?): Boolean = other != null && other.javaClass == javaClass

    override fun hashCode(): Int = javaClass.hashCode()

    override fun toString(): String = javaClass.simpleName

    /** 로그인 세션이 없거나 만료됐다. */
    class NotAuthenticated : BearerError()

    /** 현재 약관 동의가 필요하다(403 `TERMS_REQUIRED`). */
    class TermsRequired : BearerError()

    /** 이 깃발의 기수가 아니다. */
    class NotBearer : BearerError()

    /** 원래 기수만 할 수 있다. */
    class NotOwner : BearerError()

    class NotFound : BearerError()

    class ZoneNotFound : BearerError()

    /** 같은 축제에 같은 이름의 깃발이 있다. */
    class NameTaken : BearerError()

    /** 다른 기기 · 공동 기수가 더 최근에 바꿨다(409 `STATUS_STALE`). */
    class Stale : BearerError()

    /** 지금 상태에서 허용되지 않는 전이 · 재시작이다. */
    class TransitionRejected : BearerError()

    /** 사진이 동시에 바뀌었거나 멱등키가 충돌했다. */
    class Conflict : BearerError()

    class CoBearerFull : BearerError()

    /** 축제당 한 깃발만 맡을 수 있다. */
    class BearerLimit : BearerError()

    /** 원래 기수는 해제할 수 없다. */
    class OwnerRequired : BearerError()

    /** 코드가 맞지 않다. 서버가 남은 횟수를 주면 함께 담는다. */
    data class InvalidCode(val remainingAttempts: Int?) : BearerError()

    /** 코드 입력이 잠겼다. [retryAfter]초 뒤 다시 할 수 있다. */
    data class CodeLocked(val retryAfter: Int) : BearerError()

    /** 상태 변경 분당 한도 초과 */
    class RateLimited : BearerError()

    class PhotoTooSmall : BearerError()

    class PhotoTooLarge : BearerError()

    class UnsupportedPhoto : BearerError()

    class InvalidRequest : BearerError()

    /** 네트워크에 연결하지 못했다. */
    class Offline : BearerError()

    class Unavailable : BearerError()

    class InvalidResponse : BearerError()
}
