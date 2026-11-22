package io.festicore.service.festivalcatalog

import io.festicore.service.api.FestivalApi
import io.festicore.service.api.FestivalScheduleDto

/**
 * 로그인 사용자의 내 일정 읽기(`GET api/v1/me/schedule/{id}`, Bearer). iOS `NetworkFestivalScheduleService.fetch`.
 * 세션이 없으면 인증 플러그인이 보내지 않고 `AuthSessionError.NotAuthenticated`. 저장(PUT)은 Timetable 문서에서 옮긴다. 실패는 [io.festicore.core.network.NetworkError] ·
 * [io.festicore.service.api.AuthSessionError] 그대로, 잘못된 ID는 [FestivalLiveBoardServiceError.InvalidFestivalId].
 */
class FestivalScheduleService(private val api: FestivalApi) {
    suspend fun mySchedule(festivalId: String): FestivalScheduleDto {
        if (!festivalId.isValidFestivalId()) throw FestivalLiveBoardServiceError.InvalidFestivalId()
        return api.mySchedule(festivalId)
    }
}
