package io.festicore.service.api.flag

/** 깃발 API 경로. `BASE_URL`이 `/`로 끝나므로 앞 `/`를 붙이지 않는다. iOS `FlagAPI`. */
object FlagApi {
    const val FLAGS = "api/v1/flags"

    /** 대표 깃발(잠금화면 표시) 조회 · 지정 · 해제. iOS `FlagAPI.pinnedFlag`. */
    const val PINNED_FLAG = "api/v1/me/pinned-flag"

    /** 기수 코드 인증. iOS `FlagAPI.claim`. */
    const val BEARER_CLAIM = "api/v1/bearer/claim"

    /** 코드 없는 기수 등록. iOS `FlagAPI.register`. */
    const val BEARER_REGISTER = "api/v1/bearer/register"

    /** 내가 기수인 깃발 목록. iOS `FlagAPI.bearerFlags`. */
    const val BEARER_FLAGS = "api/v1/me/bearer-flags"

    /** 내가 팔로우한 깃발 목록. iOS `FlagAPI.follows`. */
    const val FOLLOWS = "api/v1/me/follows"

    /** 공개 깃발 상세. iOS `FlagAPI.profile`. */
    fun detail(flagId: String) = "$FLAGS/$flagId"

    /** 팔로우(PUT) · 언팔로우(DELETE). iOS `FestivalFlagFollowRequest`. */
    fun follow(flagId: String) = "$FLAGS/$flagId/follow"

    /** 신고. iOS `FlagAPI.report`. */
    fun report(flagId: String) = "$FLAGS/$flagId/report"

    /** 기수 보드. iOS `FlagAPI.bearer`. */
    fun bearer(flagId: String) = "$FLAGS/$flagId/bearer"

    /** 기수 상태 변경. iOS `FlagAPI.status`. */
    fun status(flagId: String) = "$FLAGS/$flagId/status"

    /** 기존 깃발을 다른 발행 축제로 옮긴다(Android 먼저, 백엔드 #409). */
    fun festivalTransfer(flagId: String) = "$FLAGS/$flagId/festival-transfer"

    /** 깃발 사진 업로드. iOS `FlagAPI.photo`. */
    fun photo(flagId: String) = "$FLAGS/$flagId/photo"

    /** 공동 기수 초대 코드 발급. iOS `FlagAPI.inviteCoBearer`. */
    fun coBearerInvite(flagId: String) = "$FLAGS/$flagId/co-bearers/invite"

    /** 공동 기수 해제. iOS `FlagAPI.coBearer`. */
    fun coBearer(flagId: String, userId: String) = "$FLAGS/$flagId/co-bearers/$userId"
}
