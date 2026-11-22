package io.festicore.service.api

/** 축제 API 경로. `BASE_URL`이 `/`로 끝나므로 앞 `/`를 붙이지 않는다. iOS `FestiCoreDomain.festivals` · `FestivalDetailRequest` 등. */
object FestivalApiPath {
    const val FESTIVALS = "api/v1/festivals"

    /** 축제 상세(온라인 팩 문서). iOS `FestivalDetailRequest`. */
    fun detail(festivalId: String) = "$FESTIVALS/$festivalId"

    /** 라이브 상태. iOS `FestivalLiveRequest`. */
    fun live(festivalId: String) = "$FESTIVALS/$festivalId/live"

    /** 공개 깃발 프로필. iOS `FestivalFlagsRequest`. */
    fun flags(festivalId: String) = "$FESTIVALS/$festivalId/flags"

    /** 공식 인스타그램 최근 게시물. iOS `FestivalOfficialNewsRequest`. */
    fun instagram(festivalId: String) = "$FESTIVALS/$festivalId/instagram"

    /** 내 일정(로그인 필요). iOS `ScheduleGetRequest`. */
    fun mySchedule(festivalId: String) = "api/v1/me/schedule/$festivalId"
}
