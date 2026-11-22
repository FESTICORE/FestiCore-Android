package io.festicore.domain.festival

import kotlin.time.Duration

/** 라이브 보드 조회 오류(라이브 · 상세 · 공식 소식). iOS `FestivalLiveBoardError`. */
sealed class FestivalLiveBoardError : Exception() {
    /** 공개 전 축제(본문 `FESTIVAL_NOT_FOUND`). 보드는 "라이브 보드 공개 전이에요"를 보인다. */
    class FestivalNotFound : FestivalLiveBoardError()

    class InvalidFestivalId : FestivalLiveBoardError()

    /** 응답이 요청과 맞지 않거나 필수 값(`serverAt` · 깃발 `updatedAt`)이 없다. */
    class InvalidResponse : FestivalLiveBoardError()

    /** 429. [retryAfter] 뒤 다시 조회하고 보이던 값은 유지한다. */
    class RateLimited(val retryAfter: Duration) : FestivalLiveBoardError()

    /** 연결 실패 · 서버 오류 · 인증 실패. */
    class Unavailable : FestivalLiveBoardError()
}
