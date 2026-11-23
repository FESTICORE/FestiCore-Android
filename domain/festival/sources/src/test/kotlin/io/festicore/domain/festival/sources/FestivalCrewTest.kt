package io.festicore.domain.festival.sources
import io.festicore.domain.festival.crew.FestivalCrewRepository
import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewError
import io.festicore.domain.festival.entity.FestivalCrewGatherReply
import io.festicore.domain.festival.entity.FestivalCrewGatherResponse
import io.festicore.domain.festival.entity.FestivalCrewInvite
import io.festicore.domain.festival.entity.FestivalCrewInviteCode
import io.festicore.domain.festival.entity.FestivalCrewState
import io.festicore.domain.festival.sources.crew.FestivalCrewUseCaseImpl
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 크루 상수와 UseCase 입력 검증. 잘못된 입력은 저장소를 부르지 않는다. iOS `Domain/FestivalDomain/Tests/Sources/FestivalCrewTests.swift`. */
class FestivalCrewTest {
    private val calls = mutableListOf<String>()
    private val crew = FestivalCrew("crw_a", "fst_a", "우리", "usr_a", emptyList(), null, null)
    private val at = Instant.parse("2026-11-23T01:02:03Z")

    private val repository = object : FestivalCrewRepository {
        override suspend fun gather(crewId: String, zoneId: String) {
            calls += "gather $crewId $zoneId"
        }

        override suspend fun create(festivalId: String, name: String): FestivalCrew = crew.also { calls += "create $festivalId $name" }

        override suspend fun detail(crewId: String): FestivalCrew = crew.also { calls += "detail $crewId" }

        override suspend fun updateStatus(
            crewId: String,
            state: FestivalCrewState,
            zoneId: String?,
        ) {
            calls += "status $crewId ${state.rawValue} $zoneId"
        }

        override suspend fun rotateInvite(crewId: String) = FestivalCrewInviteCode("ABCD23", at).also { calls += "rotate $crewId" }

        override suspend fun rename(crewId: String, name: String): FestivalCrew = crew.also { calls += "rename $crewId $name" }

        override suspend fun removeMember(crewId: String, userId: String) {
            calls += "remove $crewId $userId"
        }

        override suspend fun transfer(crewId: String, toUserId: String): FestivalCrew = crew.also { calls += "transfer $crewId $toUserId" }

        override suspend fun dissolve(crewId: String) {
            calls += "dissolve $crewId"
        }

        override suspend fun invite(code: String) = FestivalCrewInvite(code, "우리", "fst_a", null, null, 1, at).also {
            calls +=
                "invite $code"
        }

        override suspend fun join(inviteCode: String): FestivalCrew = crew.also { calls += "join $inviteCode" }

        override suspend fun respondToGather(
            crewId: String,
            gatherId: String,
            response: FestivalCrewGatherResponse,
        ) = FestivalCrewGatherReply(gatherId, "usr_a", "로이", response, at).also { calls += "respond $crewId $gatherId" }

        override suspend fun leave(crewId: String) {
            calls += "leave $crewId"
        }
    }
    private val useCase = FestivalCrewUseCaseImpl(repository)

    private suspend fun failure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Exception) {
        e
    }

    @Test
    fun `정원과 이름 길이는 iOS 상수와 같다`() {
        assertEquals(12, FestivalCrew.MAX_MEMBERS)
        assertEquals(12, FestivalCrew.MAX_NAME_LENGTH)
    }

    @Test
    fun `이름은 앞뒤 공백을 빼고 1~12자만 받는다`() = runTest {
        useCase.create(" fst_a ", "  가  ")
        useCase.create("fst_a", "가".repeat(12))
        useCase.rename("crw_a", " 새 이름 ")

        assertTrue(failure { useCase.create("fst_a", "") } is FestivalCrewError.InvalidInput)
        assertTrue(failure { useCase.create("fst_a", "   ") } is FestivalCrewError.InvalidInput)
        assertTrue(failure { useCase.create("fst_a", "가".repeat(13)) } is FestivalCrewError.InvalidInput)
        assertTrue(failure { useCase.rename("crw_a", "가".repeat(13)) } is FestivalCrewError.InvalidInput)
        assertTrue(failure { useCase.create("", "우리") } is FestivalCrewError.InvalidInput)
        assertTrue(failure { useCase.create("f".repeat(33), "우리") } is FestivalCrewError.InvalidInput)
        assertEquals(listOf("create fst_a 가", "create fst_a ${"가".repeat(12)}", "rename crw_a 새 이름"), calls)
    }

    @Test
    fun `ID는 ASCII 영숫자 · 하이픈 · 밑줄 1~64자만 보낸다`() = runTest {
        useCase.detail("A".repeat(64))
        useCase.removeMember("crw-a", "usr_B9")

        listOf("", "a".repeat(65), "crw/a", "crw a", "크루", "crw.a", "ｃrw").forEach { id ->
            assertTrue(id, failure { useCase.detail(id) } is FestivalCrewError.InvalidInput)
        }
        assertTrue(failure { useCase.transfer("crw_a", "usr/b") } is FestivalCrewError.InvalidInput)
        assertTrue(failure { useCase.respondToGather("crw_a", "", FestivalCrewGatherResponse.ACK) } is FestivalCrewError.InvalidInput)
        assertTrue(failure { useCase.updateStatus("crw_a", FestivalCrewState.HERE, "zon/a") } is FestivalCrewError.InvalidInput)
        assertTrue(failure { useCase.gather("crw/a", "zon_a") } is FestivalCrewError.InvalidInput)
        assertTrue(failure { useCase.gather("crw_a", "") } is FestivalCrewError.InvalidInput)
        assertEquals(listOf("detail ${"A".repeat(64)}", "remove crw-a usr_B9"), calls)
    }

    @Test
    fun `초대 코드는 공백을 빼고 대문자로 맞추며 헷갈리는 글자와 길이가 틀리면 NotFound다`() = runTest {
        useCase.invite(" abcd23 ")
        useCase.join("zyxw98")

        listOf("ABCD2", "ABCD234", "ABCD20", "ABCDO2", "ABCD12", "ABCDI2", "ABCD-2", "").forEach { code ->
            assertTrue(code, failure { useCase.invite(code) } is FestivalCrewError.NotFound)
            assertTrue(code, failure { useCase.join(code) } is FestivalCrewError.NotFound)
        }
        assertEquals(listOf("invite ABCD23", "join ZYXW98"), calls)
    }

    @Test
    fun `귀가 중 상태는 존을 보내지 않는다`() = runTest {
        useCase.updateStatus("crw_a", FestivalCrewState.LEAVING, "zon_a")
        useCase.updateStatus("crw_a", FestivalCrewState.HERE, "zon_a")
        useCase.updateStatus("crw_a", FestivalCrewState.EATING, null)

        assertEquals(listOf("status crw_a leaving null", "status crw_a here zon_a", "status crw_a eating null"), calls)
    }

    @Test
    fun `모여 응답이 요청과 다르면 InvalidResponse다`() = runTest {
        val reply = useCase.respondToGather("crw_a", "gth_a", FestivalCrewGatherResponse.GOING)

        assertEquals("gth_a", reply.gatherId)
        val other = FestivalCrewUseCaseImpl(
            object : FestivalCrewRepository by repository {
                override suspend fun respondToGather(
                    crewId: String,
                    gatherId: String,
                    response: FestivalCrewGatherResponse,
                ) = FestivalCrewGatherReply("gth_b", "usr_a", "로이", response, at)
            },
        )
        assertTrue(
            failure {
                other.respondToGather("crw_a", "gth_a", FestivalCrewGatherResponse.GOING)
            } is FestivalCrewError.InvalidResponse,
        )
    }
}
