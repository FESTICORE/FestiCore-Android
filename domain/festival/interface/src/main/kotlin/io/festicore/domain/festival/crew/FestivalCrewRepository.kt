package io.festicore.domain.festival.crew

import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewError
import io.festicore.domain.festival.entity.FestivalCrewGatherReply
import io.festicore.domain.festival.entity.FestivalCrewGatherResponse
import io.festicore.domain.festival.entity.FestivalCrewInvite
import io.festicore.domain.festival.entity.FestivalCrewInviteCode
import io.festicore.domain.festival.entity.FestivalCrewState

/** 축제 크루 생성 · 상세 · 상태 · 초대 · 크루장 관리 통신. 실패는 [FestivalCrewError]. iOS `FestivalCrewRepositoryInterface`. */
interface FestivalCrewRepository {
    /** 선택한 공식 존으로 크루 집결 요청을 보낸다. */
    suspend fun gather(crewId: String, zoneId: String)

    suspend fun create(festivalId: String, name: String): FestivalCrew

    /** 크루 상세와 크루원 존 · 상태. 크루원만 볼 수 있다. */
    suspend fun detail(crewId: String): FestivalCrew

    /** 내 상태를 크루원에게 공유한다. `leaving`이면 서버가 존을 비운다. */
    suspend fun updateStatus(
        crewId: String,
        state: FestivalCrewState,
        zoneId: String?,
    )

    /** 초대 코드를 새로 만든다. 크루장만 부를 수 있고 이전 코드는 바로 무효가 된다. */
    suspend fun rotateInvite(crewId: String): FestivalCrewInviteCode

    /** 크루 이름을 바꾼다. 크루장만. */
    suspend fun rename(crewId: String, name: String): FestivalCrew

    /** 크루원을 내보낸다. 크루장만. */
    suspend fun removeMember(crewId: String, userId: String)

    /** 크루장을 다른 크루원에게 넘긴다. */
    suspend fun transfer(crewId: String, toUserId: String): FestivalCrew

    /** 크루를 해산한다. 크루장만. */
    suspend fun dissolve(crewId: String)

    /** 초대 코드의 크루를 로그인 없이 미리 본다. */
    suspend fun invite(code: String): FestivalCrewInvite

    /** 초대 코드로 크루에 들어간다. */
    suspend fun join(inviteCode: String): FestivalCrew

    /** 받은 모여 요청에 응답한다. 다시 응답하면 서버가 덮어쓰고, 보낸 사람은 응답할 수 없다. */
    suspend fun respondToGather(
        crewId: String,
        gatherId: String,
        response: FestivalCrewGatherResponse,
    ): FestivalCrewGatherReply

    /** 내가 크루에서 나간다. 크루장은 넘기거나 해산해야 나갈 수 있다. */
    suspend fun leave(crewId: String)
}
