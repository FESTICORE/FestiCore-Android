package io.festicore.domain.festival.sources.model.mapper

import io.festicore.core.network.error.NetworkError
import io.festicore.domain.festival.entity.FestivalLiveBoardError
import io.festicore.domain.festival.entity.FestivalSchedule
import io.festicore.domain.festival.entity.FestivalScheduleError
import io.festicore.domain.festival.sources.service.FestivalLiveBoardServiceError
import io.festicore.domain.festival.sources.service.FestivalScheduleServiceError
import io.festicore.service.api.base.AuthSessionError
import kotlin.coroutines.cancellation.CancellationException

private const val FESTIVAL_NOT_FOUND = "FESTIVAL_NOT_FOUND"

/** 상세 · 라이브 · 공식 소식 오류를 [FestivalLiveBoardError]로 바꾼다. 취소는 그대로 둔다. */
internal fun Throwable.toFestivalLiveBoardError(): Throwable = when (this) {
    is CancellationException, is FestivalLiveBoardError -> this

    is FestivalLiveBoardServiceError.FestivalNotFound -> FestivalLiveBoardError.FestivalNotFound()

    is FestivalLiveBoardServiceError.InvalidFestivalId -> FestivalLiveBoardError.InvalidFestivalId()

    is FestivalLiveBoardServiceError.InvalidResponse -> FestivalLiveBoardError.InvalidResponse()

    is FestivalLiveBoardServiceError.RateLimited -> FestivalLiveBoardError.RateLimited(retryAfter)

    is NetworkError.Http -> if (code ==
        FESTIVAL_NOT_FOUND
    ) {
        FestivalLiveBoardError.FestivalNotFound()
    } else {
        FestivalLiveBoardError.Unavailable()
    }

    is NetworkError.Decoding, is NetworkError.InvalidResponse -> FestivalLiveBoardError.InvalidResponse()

    else -> FestivalLiveBoardError.Unavailable()
}

internal inline fun <T> mapLiveBoardErrors(block: () -> T): T = try {
    block()
} catch (e: Exception) {
    throw e.toFestivalLiveBoardError()
}

/**
 * 내 일정 오류. iOS `NetworkFestivalScheduleService.map`: 401 · 세션 없음 → Unauthorized, 409 `STATUS_STALE`(서비스가 다시 읽음) → Stale,
 * 400 · 잘못된 ID → Rejected, 나머지(429 · `code != SUCCESS` 포함) Unavailable.
 */
internal fun Throwable.toFestivalScheduleError(): Throwable = when (this) {
    is CancellationException, is FestivalScheduleError -> this

    is AuthSessionError -> FestivalScheduleError.Unauthorized()

    is FestivalScheduleServiceError.Stale -> FestivalScheduleError.Stale(FestivalSchedule(schedule.setIds))

    is FestivalLiveBoardServiceError.InvalidFestivalId -> FestivalScheduleError.Rejected()

    is NetworkError.Http -> when {
        isUnauthorized -> FestivalScheduleError.Unauthorized()
        status == 400 -> FestivalScheduleError.Rejected()
        else -> FestivalScheduleError.Unavailable()
    }

    else -> FestivalScheduleError.Unavailable()
}
