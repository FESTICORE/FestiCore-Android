package io.festicore.service.api

/** 깃발 API 경로. `BASE_URL`이 `/`로 끝나므로 앞 `/`를 붙이지 않는다. iOS `FlagAPI`. */
object FlagApiPath {
    const val FLAGS = "api/v1/flags"

    /** 대표 깃발(잠금화면 표시) 지정 · 해제. iOS `FlagAPI.pinnedFlag`. */
    const val PINNED_FLAG = "api/v1/me/pinned-flag"

    /** 공개 깃발 상세. iOS `FlagAPI.profile`. */
    fun detail(flagId: String) = "$FLAGS/$flagId"

    /** 팔로우(PUT) · 언팔로우(DELETE). iOS `FestivalFlagFollowRequest`. */
    fun follow(flagId: String) = "$FLAGS/$flagId/follow"

    /** 신고. iOS `FlagAPI.report`. */
    fun report(flagId: String) = "$FLAGS/$flagId/report"
}
