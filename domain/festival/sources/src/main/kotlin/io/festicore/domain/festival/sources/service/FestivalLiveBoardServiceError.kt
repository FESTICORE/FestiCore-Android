package io.festicore.domain.festival.sources.service

import kotlin.time.Duration

/** 상세 · 라이브 · 공식 소식 · 내 일정 서비스 오류. 네트워크 실패는 [io.festicore.core.network.NetworkError]로 그대로 던진다. iOS `FestivalLiveBoardError`. */
sealed class FestivalLiveBoardServiceError : Exception() {
    /** 축제 ID가 비었거나 `[A-Za-z0-9_-]` 밖의 문자가 있다. 요청을 보내지 않았다. */
    class InvalidFestivalId : FestivalLiveBoardServiceError()

    /** 응답이 요청과 맞지 않는다(다른 축제 · 캐시 없는 304 · 응답 사이 세션 바뀜 등). */
    class InvalidResponse : FestivalLiveBoardServiceError()

    /** `/live` 본문 code `FESTIVAL_NOT_FOUND`. 라이브 보드 공개 전. */
    class FestivalNotFound : FestivalLiveBoardServiceError()

    /** 429. [retryAfter] 뒤에 다시 조회한다. */
    class RateLimited(val retryAfter: Duration) : FestivalLiveBoardServiceError()
}

/** iOS 서비스들과 같은 축제 ID 문자 검사(`[A-Za-z0-9_-]`, 비지 않음). */
fun String.isValidFestivalId(): Boolean =
    isNotEmpty() && all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
