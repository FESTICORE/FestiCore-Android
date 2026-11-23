package io.festicore.service.api.festival

/** 축제 API 경로. `BASE_URL`이 `/`로 끝나므로 앞 `/`를 붙이지 않는다. iOS `FestiCoreDomain.festivals` · `FestivalDetailRequest` 등. */
object FestivalApi {
    const val FESTIVALS = "api/v1/festivals"

    /** 팩 요청(`pack.get`)과 변경 알림(`pack.updated`)을 주고받는 WebSocket. 인증 없음, handshake 재시도 0. iOS `FestivalAPI.packUpdates`. */
    const val PACK_UPDATES = "$FESTIVALS/updates"

    /** 축제 상세(온라인 팩 문서). iOS `FestivalDetailRequest`. */
    fun detail(festivalId: String) = "$FESTIVALS/$festivalId"

    /** 현재 발행 팩 버전 · 지도 버전. 인증 없음. iOS `FestivalPackRequest.version`. */
    fun packVersion(festivalId: String) = "$FESTIVALS/$festivalId/pack/version"

    /** 라이브 상태. iOS `FestivalLiveRequest`. */
    fun live(festivalId: String) = "$FESTIVALS/$festivalId/live"

    /** 공개 깃발 프로필. iOS `FestivalFlagsRequest`. */
    fun flags(festivalId: String) = "$FESTIVALS/$festivalId/flags"

    /** 공식 인스타그램 최근 게시물. iOS `FestivalOfficialNewsRequest`. */
    fun instagram(festivalId: String) = "$FESTIVALS/$festivalId/instagram"

    /** 내 일정(로그인 필요). 읽기 GET · 저장 PUT. iOS `ScheduleGetRequest` · `SchedulePutRequest`. */
    fun mySchedule(festivalId: String) = "api/v1/me/schedule/$festivalId"
}
