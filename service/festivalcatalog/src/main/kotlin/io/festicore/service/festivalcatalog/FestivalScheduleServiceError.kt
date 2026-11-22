package io.festicore.service.festivalcatalog

import io.festicore.service.api.FestivalScheduleDto

/** 내 일정 저장 오류 중 서비스가 만드는 것. 나머지는 [io.festicore.core.network.NetworkError] · [io.festicore.service.api.AuthSessionError] 그대로. */
sealed class FestivalScheduleServiceError : Exception() {
    /** 409 `STATUS_STALE`. 더 최신 일정이 서버에 있어 다시 읽은 [schedule]을 함께 돌려준다. iOS `FestivalScheduleError.stale`. */
    class Stale(val schedule: FestivalScheduleDto) : FestivalScheduleServiceError()
}
