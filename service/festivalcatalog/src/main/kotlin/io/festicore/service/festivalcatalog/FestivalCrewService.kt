package io.festicore.service.festivalcatalog

import io.festicore.service.api.CrewDto
import io.festicore.service.api.CrewGatherDto
import io.festicore.service.api.CrewGatherReplyDto
import io.festicore.service.api.CrewInviteCodeDto
import io.festicore.service.api.CrewInviteDto
import io.festicore.service.api.CrewStatusDto
import io.festicore.service.api.base.AuthSessionError
import io.festicore.service.apiendpoint.crew.CrewCreateBody
import io.festicore.service.apiendpoint.crew.CrewGatherBody
import io.festicore.service.apiendpoint.crew.CrewGatherResponseBody
import io.festicore.service.apiendpoint.crew.CrewJoinBody
import io.festicore.service.apiendpoint.crew.CrewRenameBody
import io.festicore.service.apiendpoint.crew.CrewRequest
import io.festicore.service.apiendpoint.crew.CrewStatusBody
import io.festicore.service.apiendpoint.crew.CrewTransferBody
import io.festicore.service.apiendpoint.crew.crewClientAt
import java.time.Instant

/**
 * 크루 요청을 현재 로그인 세션에 묶는다. 세션이 없으면 요청 전에 [AuthSessionError.NotAuthenticated].
 * 입력 검증 · 응답 확인 · 오류 매핑은 도메인이 한다. 실패는 [io.festicore.core.network.NetworkError] 그대로 던지고 재시도하지 않는다.
 * iOS `FestivalCrewRepositoryImpl`의 요청 부분.
 */
class FestivalCrewService(
    private val api: CrewRequest,
    private val currentSessionId: suspend () -> String? = { null },
    private val now: () -> Instant = Instant::now,
) {
    suspend fun create(festivalId: String, name: String): CrewDto = api.create(CrewCreateBody(festivalId, name), sessionId())

    suspend fun detail(crewId: String): CrewDto = api.detail(crewId, sessionId())

    suspend fun rename(crewId: String, name: String): CrewDto = api.rename(crewId, CrewRenameBody(name), sessionId())

    suspend fun dissolve(crewId: String) = api.dissolve(crewId, sessionId())

    /** `clientAt`은 지금 시각(밀리초 ISO UTC), 멱등 키는 요청마다 새로 만든다. */
    suspend fun updateStatus(
        crewId: String,
        state: String,
        zoneId: String?,
    ): CrewStatusDto {
        val sessionId = sessionId()
        return api.status(crewId, CrewStatusBody(zoneId = zoneId, state = state, clientAt = crewClientAt(now())), sessionId)
    }

    suspend fun rotateInvite(crewId: String): CrewInviteCodeDto = api.rotateInvite(crewId, sessionId())

    suspend fun transfer(crewId: String, toUserId: String): CrewDto = api.transfer(crewId, CrewTransferBody(toUserId), sessionId())

    suspend fun removeMember(crewId: String, userId: String) = api.removeMember(crewId, userId, sessionId())

    /** 로그인 없이 본다. */
    suspend fun invite(code: String): CrewInviteDto = api.invite(code)

    suspend fun join(inviteCode: String): CrewDto = api.join(CrewJoinBody(inviteCode), sessionId())

    suspend fun gather(crewId: String, zoneId: String): CrewGatherDto = api.gather(crewId, CrewGatherBody(zoneId), sessionId())

    suspend fun respondToGather(
        crewId: String,
        gatherId: String,
        response: String,
    ): CrewGatherReplyDto = api.respondToGather(crewId, gatherId, CrewGatherResponseBody(response), sessionId())

    private suspend fun sessionId(): String = currentSessionId() ?: throw AuthSessionError.NotAuthenticated()
}
