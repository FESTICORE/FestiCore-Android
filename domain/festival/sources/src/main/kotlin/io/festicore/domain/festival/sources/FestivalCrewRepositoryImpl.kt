package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalCrew
import io.festicore.domain.festival.FestivalCrewError
import io.festicore.domain.festival.FestivalCrewGatherReply
import io.festicore.domain.festival.FestivalCrewGatherResponse
import io.festicore.domain.festival.FestivalCrewInvite
import io.festicore.domain.festival.FestivalCrewInviteCode
import io.festicore.domain.festival.FestivalCrewRepository
import io.festicore.domain.festival.FestivalCrewState
import io.festicore.service.festivalcatalog.FestivalCrewService

/** iOS `FestivalCrewRepositoryImpl`. 응답이 요청과 맞는지 확인하고 실패를 [FestivalCrewError]로 바꾼다. [currentUserId]는 나가기에 쓴다. */
internal class FestivalCrewRepositoryImpl(
    private val service: FestivalCrewService,
    private val currentUserId: () -> String?,
) : FestivalCrewRepository {
    override suspend fun gather(crewId: String, zoneId: String) = request(Throwable::toFestivalCrewGatherError) {
        val result = service.gather(crewId, zoneId)
        if (result.zoneId != zoneId || result.id?.isEmpty() == true) throw FestivalCrewError.InvalidResponse()
    }

    override suspend fun create(festivalId: String, name: String): FestivalCrew = request(Throwable::toFestivalCrewCreateError) {
        service.create(festivalId, name).toDomain().also {
            if (it.festivalId != festivalId) throw FestivalCrewError.InvalidResponse()
        }
    }

    override suspend fun detail(crewId: String): FestivalCrew = request {
        service.detail(crewId).toDomain().also { if (it.id != crewId) throw FestivalCrewError.InvalidResponse() }
    }

    override suspend fun updateStatus(
        crewId: String,
        state: FestivalCrewState,
        zoneId: String?,
    ) = request {
        if (service.updateStatus(crewId, state.rawValue, zoneId).state != state.rawValue) throw FestivalCrewError.InvalidResponse()
    }

    override suspend fun rotateInvite(crewId: String): FestivalCrewInviteCode = request { service.rotateInvite(crewId).toDomain() }

    override suspend fun rename(crewId: String, name: String): FestivalCrew = request {
        service.rename(crewId, name).toDomain().also {
            if (it.id != crewId || it.name != name) throw FestivalCrewError.InvalidResponse()
        }
    }

    override suspend fun removeMember(crewId: String, userId: String) = request { service.removeMember(crewId, userId) }

    override suspend fun transfer(crewId: String, toUserId: String): FestivalCrew = request {
        service.transfer(crewId, toUserId).toDomain().also {
            if (it.id != crewId || it.ownerId != toUserId) throw FestivalCrewError.InvalidResponse()
        }
    }

    override suspend fun dissolve(crewId: String) = request { service.dissolve(crewId) }

    override suspend fun invite(code: String): FestivalCrewInvite = request { service.invite(code).toDomain(code) }

    override suspend fun join(inviteCode: String): FestivalCrew = request { service.join(inviteCode).toDomain() }

    override suspend fun respondToGather(
        crewId: String,
        gatherId: String,
        response: FestivalCrewGatherResponse,
    ): FestivalCrewGatherReply = request(Throwable::toFestivalCrewGatherReplyError) {
        service.respondToGather(crewId, gatherId, response.rawValue).toDomain()
    }

    override suspend fun leave(crewId: String) = request(Throwable::toFestivalCrewLeaveError) {
        val userId = currentUserId()
        if (userId.isNullOrEmpty()) throw FestivalCrewError.Unauthenticated()
        service.removeMember(crewId, userId)
    }

    private inline fun <T> request(map: (Throwable) -> Throwable = Throwable::toFestivalCrewError, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw map(e)
    }
}
