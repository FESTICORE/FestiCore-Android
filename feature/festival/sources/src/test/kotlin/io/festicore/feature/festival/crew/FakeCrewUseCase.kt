package io.festicore.feature.festival.crew

import io.festicore.domain.festival.crew.FestivalCrewUseCase
import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewGatherReply
import io.festicore.domain.festival.entity.FestivalCrewGatherResponse
import io.festicore.domain.festival.entity.FestivalCrewInvite
import io.festicore.domain.festival.entity.FestivalCrewInviteCode
import io.festicore.domain.festival.entity.FestivalCrewState

/** 정하지 않은 호출은 [AssertionError]로 테스트를 깨뜨린다(ViewModel의 `Exception` 처리에 묻히지 않게). iOS `CrewSpy`. */
private fun unexpected(name: String): Nothing = throw AssertionError("$name 은(는) 이 테스트에서 부르지 않는다")

/** 테스트마다 필요한 크루 호출만 정한다. 실제 서버는 부르지 않는다. */
internal class FakeCrewUseCase(
    var gather: suspend (crewId: String, zoneId: String) -> Unit = { _, _ -> unexpected("gather") },
    var create: suspend (festivalId: String, name: String) -> FestivalCrew = { _, _ -> unexpected("create") },
    var detail: suspend (crewId: String) -> FestivalCrew = { unexpected("detail") },
    var updateStatus: suspend (state: FestivalCrewState, zoneId: String?) -> Unit = { _, _ -> unexpected("updateStatus") },
    var rotateInvite: suspend () -> FestivalCrewInviteCode = { unexpected("rotateInvite") },
    var rename: suspend (name: String) -> FestivalCrew = { unexpected("rename") },
    var removeMember: suspend (userId: String) -> Unit = { unexpected("removeMember") },
    var transfer: suspend (toUserId: String) -> FestivalCrew = { unexpected("transfer") },
    var dissolve: suspend () -> Unit = { unexpected("dissolve") },
    var respond: suspend (gatherId: String, response: FestivalCrewGatherResponse) -> FestivalCrewGatherReply =
        { _, _ -> unexpected("respondToGather") },
) : FestivalCrewUseCase {
    override suspend fun gather(crewId: String, zoneId: String) = gather.invoke(crewId, zoneId)

    override suspend fun create(festivalId: String, name: String) = create.invoke(festivalId, name)

    override suspend fun detail(crewId: String) = detail.invoke(crewId)

    override suspend fun updateStatus(
        crewId: String,
        state: FestivalCrewState,
        zoneId: String?,
    ) = updateStatus.invoke(state, zoneId)

    override suspend fun rotateInvite(crewId: String) = rotateInvite.invoke()

    override suspend fun rename(crewId: String, name: String) = rename.invoke(name)

    override suspend fun removeMember(crewId: String, userId: String) = removeMember.invoke(userId)

    override suspend fun transfer(crewId: String, toUserId: String) = transfer.invoke(toUserId)

    override suspend fun dissolve(crewId: String) = dissolve.invoke()

    override suspend fun invite(code: String): FestivalCrewInvite = unexpected("invite")

    override suspend fun join(inviteCode: String): FestivalCrew = unexpected("join")

    override suspend fun respondToGather(
        crewId: String,
        gatherId: String,
        response: FestivalCrewGatherResponse,
    ) = respond.invoke(gatherId, response)

    override suspend fun leave(crewId: String) = unexpected("leave")
}
