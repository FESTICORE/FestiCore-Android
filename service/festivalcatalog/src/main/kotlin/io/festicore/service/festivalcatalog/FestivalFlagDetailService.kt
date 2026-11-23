package io.festicore.service.festivalcatalog

import io.festicore.service.api.FlagDetailDto
import io.festicore.service.apiendpoint.flag.FlagReportBody
import io.festicore.service.apiendpoint.flag.FlagRequest
import io.festicore.service.apiendpoint.flag.PinnedFlagBody

/**
 * 깃발 상세(익명 허용) · 신고 · 대표 깃발 지정. iOS `FestivalFlagDetailRepositoryImpl`의 요청 부분.
 * 잘못된 ID는 [FestivalFlagServiceError.InvalidFlagId](요청하지 않음), 요청과 다른 응답은 [FestivalFlagServiceError.InvalidResponse].
 * 나머지 실패는 [io.festicore.core.network.NetworkError] · [io.festicore.service.api.AuthSessionError] 그대로 던지고 재시도하지 않는다.
 */
class FestivalFlagDetailService(
    private val api: FlagRequest,
    private val currentSessionId: suspend () -> String? = { null },
) {
    /** 로그인 중이면 그 세션의 Bearer를 붙인다. 익명이면 `isFollowing` · `isBearer` · `isPinned`가 null이다. */
    suspend fun detail(flagId: String): FlagDetailDto {
        if (!flagId.isValidFlagId()) throw FestivalFlagServiceError.InvalidFlagId()
        val detail = api.detail(flagId, currentSessionId())
        if (detail.id != flagId) throw FestivalFlagServiceError.InvalidResponse()
        return detail
    }

    /** [reason]은 `fake` · `inappropriate` · `other`. */
    suspend fun report(flagId: String, reason: String) {
        if (!flagId.isValidFlagId()) throw FestivalFlagServiceError.InvalidFlagId()
        val result = api.report(flagId, FlagReportBody(reason), currentSessionId())
        if (result.reportId.isEmpty()) throw FestivalFlagServiceError.InvalidResponse()
    }

    /** 대표 깃발을 [flagId]로 바꾸고(null이면 해제) 서버가 저장한 값을 돌려준다. */
    suspend fun setPinnedFlag(flagId: String?): String? {
        if (flagId != null && !flagId.isValidFlagId()) throw FestivalFlagServiceError.InvalidFlagId()
        val result = api.setPinnedFlag(PinnedFlagBody(flagId), currentSessionId())
        if (result.pinnedFlagId != flagId) throw FestivalFlagServiceError.InvalidResponse()
        return result.pinnedFlagId
    }
}
