package io.festicore.domain.festival.sources

import io.festicore.domain.festival.FestivalCrew
import io.festicore.domain.festival.FestivalCrewError
import io.festicore.domain.festival.FestivalCrewGatherReply
import io.festicore.domain.festival.FestivalCrewGatherResponse
import io.festicore.domain.festival.FestivalCrewInvite
import io.festicore.domain.festival.FestivalCrewInviteCode
import io.festicore.domain.festival.FestivalCrewRepository
import io.festicore.domain.festival.FestivalCrewState
import io.festicore.domain.festival.FestivalCrewUseCase

/** iOS `FestivalCrewUseCaseImpl`. 입력을 정규화 · 검증한 뒤 저장소에 넘긴다(`CUc:19-22 · 144-159`). */
internal class FestivalCrewUseCaseImpl(private val repository: FestivalCrewRepository) : FestivalCrewUseCase {
    override suspend fun gather(crewId: String, zoneId: String) {
        // iOS와 같이 크루 ID는 문자 종류만, 존 ID는 비어 있지 않은지만 본다.
        if (crewId.isEmpty() || zoneId.isEmpty() || !crewId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            throw FestivalCrewError.InvalidInput()
        }
        repository.gather(crewId, zoneId)
    }

    override suspend fun create(festivalId: String, name: String): FestivalCrew {
        val festivalId = festivalId.trim()
        val name = name.trim()
        if (festivalId.length !in 1..MAX_FESTIVAL_ID_LENGTH || name.length !in 1..FestivalCrew.MAX_NAME_LENGTH) {
            throw FestivalCrewError.InvalidInput()
        }
        return repository.create(festivalId, name)
    }

    override suspend fun detail(crewId: String): FestivalCrew = repository.detail(validId(crewId))

    override suspend fun updateStatus(
        crewId: String,
        state: FestivalCrewState,
        zoneId: String?,
    ) {
        validId(crewId)
        zoneId?.let(::validId)
        // 귀가 중은 서버가 존을 비우므로 존을 함께 보내지 않는다.
        repository.updateStatus(crewId, state, if (state == FestivalCrewState.LEAVING) null else zoneId)
    }

    override suspend fun rotateInvite(crewId: String): FestivalCrewInviteCode = repository.rotateInvite(validId(crewId))

    override suspend fun rename(crewId: String, name: String): FestivalCrew {
        validId(crewId)
        val name = name.trim()
        if (name.length !in 1..FestivalCrew.MAX_NAME_LENGTH) throw FestivalCrewError.InvalidInput()
        return repository.rename(crewId, name)
    }

    override suspend fun removeMember(crewId: String, userId: String) = repository.removeMember(validId(crewId), validId(userId))

    override suspend fun transfer(crewId: String, toUserId: String): FestivalCrew = repository.transfer(validId(crewId), validId(toUserId))

    override suspend fun dissolve(crewId: String) = repository.dissolve(validId(crewId))

    override suspend fun invite(code: String): FestivalCrewInvite = repository.invite(inviteCode(code))

    override suspend fun join(inviteCode: String): FestivalCrew = repository.join(inviteCode(inviteCode))

    override suspend fun respondToGather(
        crewId: String,
        gatherId: String,
        response: FestivalCrewGatherResponse,
    ): FestivalCrewGatherReply {
        val reply = repository.respondToGather(validId(crewId), validId(gatherId), response)
        if (reply.gatherId != gatherId || reply.response != response) throw FestivalCrewError.InvalidResponse()
        return reply
    }

    override suspend fun leave(crewId: String) = repository.leave(validId(crewId))

    private companion object {
        const val MAX_FESTIVAL_ID_LENGTH = 32
        const val MAX_ID_LENGTH = 64
        const val INVITE_CODE_LENGTH = 6
        const val INVITE_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** 서버 ID는 ASCII 영숫자 · `-` · `_` 1~64자. 경로 한 칸을 벗어나는 값은 보내지 않는다. */
        fun validId(id: String): String {
            val valid = id.length in 1..MAX_ID_LENGTH &&
                id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
            if (!valid) throw FestivalCrewError.InvalidInput()
            return id
        }

        /** 초대 코드는 헷갈리는 글자(I · O · 0 · 1)를 뺀 6자. 대문자로 맞춰 보낸다. 틀리면 없는 코드로 본다. */
        fun inviteCode(value: String): String {
            val code = value.trim().uppercase()
            if (code.length != INVITE_CODE_LENGTH || !code.all { it in INVITE_CODE_CHARACTERS }) throw FestivalCrewError.NotFound()
            return code
        }
    }
}
