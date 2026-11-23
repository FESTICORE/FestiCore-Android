package io.festicore.domain.festival.sources.repository

import io.festicore.domain.festival.crew.FestivalCrewRepository
import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewError
import io.festicore.domain.festival.entity.FestivalCrewGatherReply
import io.festicore.domain.festival.entity.FestivalCrewGatherResponse
import io.festicore.domain.festival.entity.FestivalCrewInvite
import io.festicore.domain.festival.entity.FestivalCrewInviteCode
import io.festicore.domain.festival.entity.FestivalCrewState
import io.festicore.domain.festival.sources.model.dto.CrewDto
import io.festicore.domain.festival.sources.model.dto.CrewGatherDto
import io.festicore.domain.festival.sources.model.dto.CrewGatherReplyDto
import io.festicore.domain.festival.sources.model.dto.CrewInviteCodeDto
import io.festicore.domain.festival.sources.model.dto.CrewInviteDto
import io.festicore.domain.festival.sources.model.dto.CrewStatusDto
import io.festicore.domain.festival.sources.model.mapper.toDomain
import io.festicore.domain.festival.sources.model.mapper.toFestivalCrewCreateError
import io.festicore.domain.festival.sources.model.mapper.toFestivalCrewError
import io.festicore.domain.festival.sources.model.mapper.toFestivalCrewGatherError
import io.festicore.domain.festival.sources.model.mapper.toFestivalCrewGatherReplyError
import io.festicore.domain.festival.sources.model.mapper.toFestivalCrewLeaveError
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
 * iOS `FestivalCrewRepositoryImpl`. 응답이 요청과 맞는지 확인하고 실패를 [FestivalCrewError]로 바꾼다. [currentUserId]는 나가기에 쓴다.
 * 크루 요청을 현재 로그인 세션에 묶는다. 세션이 없으면 요청 전에 [AuthSessionError.NotAuthenticated].
 */
internal class FestivalCrewRepositoryImpl(
    private val api: CrewRequest,
    private val currentSessionId: suspend () -> String? = { null },
    private val now: () -> Instant = Instant::now,
    private val currentUserId: () -> String?,
) : FestivalCrewRepository {
    override suspend fun gather(crewId: String, zoneId: String) = request(Throwable::toFestivalCrewGatherError) {
        val result = api.gather<CrewGatherDto>(crewId, CrewGatherBody(zoneId), sessionId())
        if (result.zoneId != zoneId || result.id?.isEmpty() == true) throw FestivalCrewError.InvalidResponse()
    }

    override suspend fun create(festivalId: String, name: String): FestivalCrew = request(Throwable::toFestivalCrewCreateError) {
        api.create<CrewDto>(CrewCreateBody(festivalId, name), sessionId()).toDomain().also {
            if (it.festivalId != festivalId) throw FestivalCrewError.InvalidResponse()
        }
    }

    override suspend fun detail(crewId: String): FestivalCrew = request {
        api.detail<CrewDto>(crewId, sessionId()).toDomain().also { if (it.id != crewId) throw FestivalCrewError.InvalidResponse() }
    }

    override suspend fun updateStatus(
        crewId: String,
        state: FestivalCrewState,
        zoneId: String?,
    ) = request {
        // `clientAt`은 지금 시각(밀리초 ISO UTC), 멱등 키는 요청마다 새로 만든다.
        val sessionId = sessionId()
        val result = api.status<CrewStatusDto>(
            crewId,
            CrewStatusBody(zoneId = zoneId, state = state.rawValue, clientAt = crewClientAt(now())),
            sessionId,
        )
        if (result.state != state.rawValue) throw FestivalCrewError.InvalidResponse()
    }

    override suspend fun rotateInvite(crewId: String): FestivalCrewInviteCode = request {
        api.rotateInvite<CrewInviteCodeDto>(crewId, sessionId()).toDomain()
    }

    override suspend fun rename(crewId: String, name: String): FestivalCrew = request {
        api.rename<CrewDto>(crewId, CrewRenameBody(name), sessionId()).toDomain().also {
            if (it.id != crewId || it.name != name) throw FestivalCrewError.InvalidResponse()
        }
    }

    override suspend fun removeMember(crewId: String, userId: String) = request { api.removeMember(crewId, userId, sessionId()) }

    override suspend fun transfer(crewId: String, toUserId: String): FestivalCrew = request {
        api.transfer<CrewDto>(crewId, CrewTransferBody(toUserId), sessionId()).toDomain().also {
            if (it.id != crewId || it.ownerId != toUserId) throw FestivalCrewError.InvalidResponse()
        }
    }

    override suspend fun dissolve(crewId: String) = request { api.dissolve(crewId, sessionId()) }

    override suspend fun invite(code: String): FestivalCrewInvite = request { api.invite<CrewInviteDto>(code).toDomain(code) }

    override suspend fun join(inviteCode: String): FestivalCrew = request {
        api.join<CrewDto>(CrewJoinBody(inviteCode), sessionId()).toDomain()
    }

    override suspend fun respondToGather(
        crewId: String,
        gatherId: String,
        response: FestivalCrewGatherResponse,
    ): FestivalCrewGatherReply = request(Throwable::toFestivalCrewGatherReplyError) {
        api.respondToGather<CrewGatherReplyDto>(crewId, gatherId, CrewGatherResponseBody(response.rawValue), sessionId()).toDomain()
    }

    override suspend fun leave(crewId: String) = request(Throwable::toFestivalCrewLeaveError) {
        val userId = currentUserId()
        if (userId.isNullOrEmpty()) throw FestivalCrewError.Unauthenticated()
        api.removeMember(crewId, userId, sessionId())
    }

    private suspend fun sessionId(): String = currentSessionId() ?: throw AuthSessionError.NotAuthenticated()

    private inline fun <T> request(map: (Throwable) -> Throwable = Throwable::toFestivalCrewError, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw map(e)
    }
}
