package io.festicore.service.festivalcatalog

import io.festicore.domain.festival.sources.model.dto.FlagFollowDto
import io.festicore.domain.festival.sources.service.FestivalFlagFollowService
import io.festicore.domain.festival.sources.service.FestivalFlagServiceError
import io.festicore.domain.festival.sources.service.isValidFlagId
import io.festicore.service.api.base.AuthSessionError
import io.festicore.service.apiendpoint.flag.FlagRequest

/**
 * 깃발 팔로우(PUT + `Idempotency-Key`) · 언팔로우(DELETE). iOS `NetworkFestivalFlagFollowService`.
 * 잘못된 ID는 [FestivalFlagServiceError.InvalidFlagId], 세션이 없으면 요청 전에 [AuthSessionError.NotAuthenticated],
 * 다른 깃발 응답은 [FestivalFlagServiceError.InvalidResponse]. 나머지 실패는 그대로 던지고 재시도하지 않는다.
 */
class NetworkFestivalFlagFollowService(
    private val api: FlagRequest,
    private val currentSessionId: suspend () -> String? = { null },
) : FestivalFlagFollowService {
    override suspend fun setFollowing(following: Boolean, flagId: String): FlagFollowDto {
        if (!flagId.isValidFlagId()) throw FestivalFlagServiceError.InvalidFlagId()
        // 연결이 끊긴 로그아웃 상태에서도 로그인 안내가 뜨도록 요청 전에 확인한다(iOS와 같음).
        val sessionId = currentSessionId() ?: throw AuthSessionError.NotAuthenticated()
        val result = if (following) api.follow<FlagFollowDto>(flagId, sessionId) else api.unfollow<FlagFollowDto>(flagId, sessionId)
        if (result.flagId != flagId) throw FestivalFlagServiceError.InvalidResponse()
        return result
    }
}
