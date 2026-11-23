package io.festicore.service.festivalcatalog

import io.festicore.core.network.NetworkError
import io.festicore.domain.festival.sources.model.dto.FestivalScheduleDto
import io.festicore.domain.festival.sources.service.FestivalLiveBoardServiceError
import io.festicore.domain.festival.sources.service.FestivalScheduleService
import io.festicore.domain.festival.sources.service.FestivalScheduleServiceError
import io.festicore.domain.festival.sources.service.isValidFestivalId
import io.festicore.service.apiendpoint.festival.FestivalRequest
import io.festicore.service.apiendpoint.festival.FestivalScheduleReplaceBody
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 로그인 사용자의 내 일정 읽기 · 저장(`api/v1/me/schedule/{id}`, Bearer). iOS `NetworkFestivalScheduleService`.
 * 세션이 없으면 인증 플러그인이 보내지 않고 `AuthSessionError.NotAuthenticated`. 실패는 [io.festicore.core.network.NetworkError] ·
 * [io.festicore.service.api.AuthSessionError] 그대로, 잘못된 ID는 [FestivalLiveBoardServiceError.InvalidFestivalId](요청하지 않음),
 * 저장 충돌은 [FestivalScheduleServiceError.Stale]. 재시도하지 않는다.
 * [currentSessionId]: 저장 PUT과 충돌 뒤 다시 읽기를 같은 세션에 묶는다(중간에 계정이 바뀌면 보내지 않는다).
 */
class NetworkFestivalScheduleService(
    private val api: FestivalRequest,
    private val currentSessionId: suspend () -> String? = { null },
) : FestivalScheduleService {
    override suspend fun mySchedule(festivalId: String): FestivalScheduleDto {
        if (!festivalId.isValidFestivalId()) throw FestivalLiveBoardServiceError.InvalidFestivalId()
        return api.mySchedule<FestivalScheduleDto>(festivalId)
    }

    /**
     * 내 일정 전체를 [setIds]로 바꾼다. [clientAt]은 축제마다 이전 요청보다 늦어야 한다(서버가 ms 단위로 비교).
     * 409 + `STATUS_STALE`이면 서버 일정을 다시 읽어 [FestivalScheduleServiceError.Stale]로 던진다. 다시 읽기가 실패하면 그 오류를 던진다.
     */
    override suspend fun replace(
        festivalId: String,
        setIds: List<String>,
        clientAt: Instant,
    ): FestivalScheduleDto {
        if (!festivalId.isValidFestivalId()) throw FestivalLiveBoardServiceError.InvalidFestivalId()
        val sessionId = currentSessionId()
        val body = FestivalScheduleReplaceBody(setIds = setIds, clientAt = clientAtString(clientAt))
        return try {
            api.replaceMySchedule<FestivalScheduleDto>(festivalId, body, sessionId)
        } catch (e: NetworkError.Http) {
            if (e.status != 409 || e.code != STATUS_STALE) throw e
            throw FestivalScheduleServiceError.Stale(api.mySchedule<FestivalScheduleDto>(festivalId, sessionId))
        }
    }

    companion object {
        private const val STATUS_STALE = "STATUS_STALE"

        /** iOS `Date.isoString`(`yyyy-MM-ddTHH:mm:ss.SSS+09:00`). */
        private val clientAtFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT).withZone(ZoneId.of("Asia/Seoul"))

        fun clientAtString(clientAt: Instant): String = clientAtFormatter.format(clientAt)
    }
}
