package io.festicore.service.api.notice

/** 공지 API 경로. `BASE_URL`이 `/`로 끝나므로 앞 `/`를 붙이지 않는다. iOS `NoticeAPI`. */
object NoticeApi {
    const val NOTICES = "api/v1/notices"

    /** 공지 상세. iOS `NoticeAPI.detail(id:)`. */
    fun detail(id: String) = "$NOTICES/$id"
}
