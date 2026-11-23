package io.festicore.feature.festival.crewjoin

import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewError
import io.festicore.domain.festival.entity.FestivalCrewInvite
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.feature.festival.crew.FakeCrewUseCase
import io.festicore.feature.festival.festivallist.FakeCurrentUserProvider
import io.festicore.feature.festival.liveboard.FakeLiveBoardUseCase
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `FestivalCrewJoinFeatureTests`. 크루 호출은 가짜로 바꾸고 서버는 부르지 않는다. */
class CrewJoinViewModelTest {
    private val invite =
        FestivalCrewInvite(
            code = "K7P2QX",
            crewName = "펜타 크루",
            festivalId = "fst_a",
            festivalName = "펜타포트",
            inviterNickname = "민지",
            memberCount = 4,
            expiresAt = Instant.ofEpochSecond(0),
        )

    private val joinedCrew =
        FestivalCrew(
            id = "crew_a",
            festivalId = "fst_a",
            name = "펜타 크루",
            ownerId = "user_b",
            members = emptyList(),
            inviteCode = null,
            inviteExpiresAt = null,
        )

    private val loadedState = CrewJoinState(code = "K7P2QX", hasSession = true, invite = invite, load = CrewJoinLoad.Loaded)

    private val pendingLeave = CrewJoinPendingLeave(crewId = "crew_old", crewName = "락린이들")

    private fun viewModel(
        crewUseCase: FakeCrewUseCase = FakeCrewUseCase(),
        liveBoardUseCase: FakeLiveBoardUseCase = FakeLiveBoardUseCase(),
        currentUser: FakeCurrentUserProvider = FakeCurrentUserProvider("user_a"),
    ) = CrewJoinViewModel(
        code = "K7P2QX",
        crewUseCase = crewUseCase,
        liveBoardUseCase = liveBoardUseCase,
        currentUserProvider = currentUser,
    )

    /** 처음 참여는 409(이미 다른 크루)로 막고, 나간 뒤 참여는 받아 준다(iOS `AlreadyInCrewSpy`). */
    private fun alreadyInCrewSpy(calls: MutableList<String>, leaveError: FestivalCrewError?) = FakeCrewUseCase(
        join = {
            val left = calls.any { it.startsWith("leave:") }
            calls += "join"
            if (!left) throw FestivalCrewError.AlreadyInCrew()
            joinedCrew
        },
        leave = { crewId ->
            calls += "leave:$crewId"
            if (leaveError != null) throw leaveError
        },
    )

    @Test
    fun loadsInviteAndAsksLoginBeforeJoining() = runTest {
        val initial = CrewJoinState(code = "K7P2QX", hasSession = false)
        viewModel(FakeCrewUseCase(invite = { invite })).test(this, initialState = initial) {
            containerHost.onAppear()
            expectState { copy(invite = this@CrewJoinViewModelTest.invite, load = CrewJoinLoad.Loaded) }
            containerHost.onJoinTapped()
            // 로그인 시트 문구에 넣을 크루 이름을 함께 보낸다(iOS 부모가 `crewName`을 읽는 것과 같다).
            expectSideEffect(CrewJoinSideEffect.LoginRequested("펜타 크루"))
        }
    }

    @Test
    fun joinsAndOpensCrew() = runTest {
        viewModel(FakeCrewUseCase(join = { joinedCrew })).test(this, initialState = loadedState) {
            containerHost.onJoinTapped()
            expectState { copy(isJoining = true) }
            expectState { copy(isJoining = false) }
            expectSideEffect(CrewJoinSideEffect.Joined(crewId = "crew_a", festivalId = "fst_a"))
        }
    }

    @Test
    fun fullCrewShowsAlert() = runTest {
        viewModel(FakeCrewUseCase(join = { throw FestivalCrewError.Full() })).test(this, initialState = loadedState) {
            containerHost.onJoinTapped()
            expectState { copy(isJoining = true) }
            expectState {
                copy(
                    isJoining = false,
                    alert = CrewJoinAlert(title = "크루에 참여하지 못했어요", message = "크루 정원 12명이 모두 찼어요.", confirmTitle = "확인", cancelTitle = "닫기"),
                )
            }
        }
    }

    @Test
    fun expiredInviteShowsUnavailable() = runTest {
        val initial = CrewJoinState(code = "K7P2QX", hasSession = true)
        viewModel(FakeCrewUseCase(invite = { throw FestivalCrewError.NotFound() })).test(this, initialState = initial) {
            containerHost.onAppear()
            expectState { copy(load = CrewJoinLoad.Unavailable) }
        }
    }

    /** Figma S08 B · C: 만료된 초대와 정원이 찬 크루는 참여 대신 닫기만 둔다. */
    @Test
    fun blockerFollowsExpiryAndCapacity() {
        var state = CrewJoinState(code = "K7P2QX", hasSession = true)
        assertNull(state.blocker(Instant.ofEpochSecond(0)))
        state = state.copy(invite = invite)
        assertNull(state.blocker(Instant.ofEpochSecond(-60)))
        assertEquals(CrewJoinBlocker.Expired, state.blocker(Instant.ofEpochSecond(0)))
        state = state.copy(invite = invite.copy(memberCount = FestivalCrew.MAX_MEMBERS))
        assertEquals(CrewJoinBlocker.Full, state.blocker(Instant.ofEpochSecond(-60)))
    }

    /** Figma S08 D: 이미 다른 크루에 있으면 확인 뒤 기존 크루를 나가고 참여한다. */
    @Test
    fun alreadyInCrewLeavesCurrentCrewThenJoins() = runTest {
        val calls = mutableListOf<String>()
        val board =
            FakeLiveBoardUseCase(liveBoard = { festivalId ->
                FakeLiveBoardUseCase.snapshot(festivalId).copy(
                    crew = FestivalLiveBoardSnapshot.Crew(id = "crew_old", name = "락린이들", members = emptyList()),
                )
            })
        viewModel(alreadyInCrewSpy(calls, leaveError = null), board).test(this, initialState = loadedState) {
            containerHost.onJoinTapped()
            expectState { copy(isJoining = true) }
            expectState {
                copy(
                    isJoining = false,
                    pendingLeave = this@CrewJoinViewModelTest.pendingLeave,
                    alert =
                        CrewJoinAlert(
                            title = "기존 크루에서 나갈까요?",
                            message = "지금 \"락린이들\"에 있어요. 페스티벌마다 크루는 하나만 들 수 있어서, 나가야 펜타 크루에 들어갈 수 있어요.",
                            confirmTitle = "나가고 참여하기",
                            cancelTitle = "취소",
                        ),
                )
            }
            containerHost.onAlertConfirmed()
            expectState { copy(alert = null, pendingLeave = null, isJoining = true) }
            expectState { copy(isJoining = false) }
            expectSideEffect(CrewJoinSideEffect.Joined(crewId = "crew_a", festivalId = "fst_a"))
        }
        assertEquals(listOf("fst_a"), (board.liveBoardCalls))
        assertEquals(listOf("join", "leave:crew_old", "join"), calls)
    }

    @Test
    fun leaveFailureDoesNotJoin() = runTest {
        val calls = mutableListOf<String>()
        val initial = loadedState.copy(pendingLeave = pendingLeave, alert = CrewJoinAlert(title = "기존 크루에서 나갈까요?", message = ""))
        viewModel(alreadyInCrewSpy(calls, leaveError = FestivalCrewError.Forbidden())).test(this, initialState = initial) {
            containerHost.onAlertConfirmed()
            expectState { copy(alert = null, pendingLeave = null, isJoining = true) }
            expectState {
                copy(
                    isJoining = false,
                    alert =
                        CrewJoinAlert(
                            title = "기존 크루에서 나가지 못했어요",
                            message = "크루장은 크루장을 넘기거나 크루를 해산한 뒤 나갈 수 있어요.",
                            confirmTitle = "확인",
                            cancelTitle = "닫기",
                        ),
                )
            }
        }
        assertEquals(listOf("leave:crew_old"), calls)
    }

    @Test
    fun cancelingLeaveConfirmKeepsCurrentCrew() = runTest {
        val initial = loadedState.copy(pendingLeave = pendingLeave, alert = CrewJoinAlert(title = "기존 크루에서 나갈까요?", message = ""))
        viewModel().test(this, initialState = initial) {
            containerHost.onAlertDismissed()
            expectState { copy(alert = null, pendingLeave = null) }
        }
    }

    /** 로그인 상태가 바뀌면 `hasSession`만 바꾸고 자동으로 참여하지 않는다(iOS `sessionChanged`, 설계 S08). */
    @Test
    fun sessionChangeOnlyUpdatesHasSession() = runTest {
        val currentUser = FakeCurrentUserProvider(null)
        viewModel(currentUser = currentUser).test(this, initialState = loadedState.copy(hasSession = false)) {
            val subscription = runOnCreate()
            currentUser.currentUserId.value = "user_a"
            expectState { copy(hasSession = true) }
            subscription.cancel()
        }
    }
}
