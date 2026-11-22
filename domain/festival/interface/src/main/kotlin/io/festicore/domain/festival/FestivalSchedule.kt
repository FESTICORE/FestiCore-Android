package io.festicore.domain.festival

/** 서버에 저장된 내 일정. 공개 팩의 공연 ID 목록. iOS `FestivalSchedule`. */
data class FestivalSchedule(val setIds: List<String>)

/** 내 일정 오류. iOS `FestivalScheduleError`(읽기에 해당하는 것만, `stale`은 저장 때 옮긴다). */
sealed class FestivalScheduleError : Exception() {
    /** 세션이 없거나 토큰 갱신 후에도 인증되지 않았다. */
    class Unauthorized : FestivalScheduleError()

    /** 서버가 요청을 거부했다(400 · 잘못된 축제 ID). */
    class Rejected : FestivalScheduleError()

    /** 연결 오류 · 5xx처럼 결과를 알 수 없다. */
    class Unavailable : FestivalScheduleError()
}
