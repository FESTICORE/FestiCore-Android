package io.festicore.domain.festival.entity

/** 서버에 저장된 내 일정. 공개 팩의 공연 ID 목록. iOS `FestivalSchedule`. */
data class FestivalSchedule(val setIds: List<String>)

/** 내 일정 오류. iOS `FestivalScheduleError`. */
sealed class FestivalScheduleError : Exception() {
    /** 세션이 없거나 토큰 갱신 후에도 인증되지 않았다. */
    class Unauthorized : FestivalScheduleError()

    /** 저장: 더 최신 일정이 서버에 있다(409 `STATUS_STALE`). 다시 읽은 서버 값을 함께 돌려준다. */
    class Stale(val schedule: FestivalSchedule) : FestivalScheduleError()

    /** 서버가 요청을 거부했다(400 · 잘못된 축제 ID). */
    class Rejected : FestivalScheduleError()

    /** 연결 오류 · 5xx · 429처럼 결과를 알 수 없다. */
    class Unavailable : FestivalScheduleError()
}

/** 내 일정 저장 알림(설계 T2, iOS `festival(.myScheduleSaved)`). 타임테이블 · 고르기가 보내고 보드가 같은 축제면 내 일정을 바꾼다. */
data class FestivalScheduleSaved(
    val festivalId: String,
    val setIds: Set<String>,
)
