package io.festicore.feature.festival.crew

import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewError
import io.festicore.domain.festival.entity.FestivalCrewGatherReply
import io.festicore.domain.festival.entity.FestivalCrewGatherResponse
import io.festicore.domain.festival.entity.FestivalCrewState
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.feature.festival.festivallist.FakeCurrentUserProvider
import io.festicore.feature.festival.liveboard.FakeBoardSelectionUseCase
import io.festicore.feature.festival.liveboard.FakeLiveBoardUseCase
import io.festicore.feature.festival.liveboard.LiveBoardPresentation
import io.festicore.feature.festival.liveboard.MutableClock
import io.festicore.feature.festival.liveboard.TestSleeper
import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `FestivalCrewFeatureTests`. 같은 흐름을 같은 이름으로 옮긴다. */
class CrewViewModelTest {
    private val now: Instant = Instant.ofEpochSecond(1_000_000)

    private fun viewModel(crewUseCase: FakeCrewUseCase = FakeCrewUseCase(), sleep: suspend (Duration) -> Unit = { awaitCancellation() }) =
        CrewViewModel(
            festivalId = "fst_a",
            crewId = "crew_a",
            crewUseCase = crewUseCase,
            liveBoardUseCase = FakeLiveBoardUseCase(),
            boardSelectionUseCase = FakeBoardSelectionUseCase(),
            currentUserProvider = FakeCurrentUserProvider("user_a"),
            clock = MutableClock(now),
            sleep = sleep,
        )

    @Test
    fun loadsDetailAndReplacesBoardMembers() = runTest {
        val crew = crew(state = "eating")
        viewModel(FakeCrewUseCase(detail = { crew })).test(this, initialState = state(memberState = "here")) {
            containerHost.onAppear()
            expectState { copy(crew = crew, live = crew.live, load = CrewLoad.Loaded) }
            // 나가면 30초 시계를 멈춘다(남은 intent 없이 끝난다).
            containerHost.onDisappear()
        }
    }

    @Test
    fun myStateUpdatesImmediatelyAndSendsCurrentZone() = runTest {
        val sent = mutableListOf<String>()
        val useCase = FakeCrewUseCase(updateStatus = { state, zone -> sent += "${state.rawValue}/${zone ?: "-"}" })
        viewModel(useCase).test(this, initialState = state(memberState = "here").copy(crew = crew(state = "here"))) {
            containerHost.onStateTapped(FestivalCrewState.EATING)
            expectState { copy(live = live(state = "eating", zoneId = "zone_a", updatedAt = now), isSendingStatus = true) }
            expectState { copy(isSendingStatus = false) }
        }
        assertEquals(listOf("eating/zone_a"), sent)
    }

    @Test
    fun failedStatusShowsToastAndReloadsServerValue() = runTest {
        val sleeper = TestSleeper()
        val serverCrew = crew(state = "here")
        val useCase = FakeCrewUseCase(detail = { serverCrew }, updateStatus = { _, _ -> throw FestivalCrewError.Unavailable() })
        viewModel(useCase, sleeper::sleep).test(this, initialState = state(memberState = "here").copy(crew = serverCrew)) {
            containerHost.onStateTapped(FestivalCrewState.LEAVING)
            expectState { copy(live = live(state = "leaving", zoneId = null, updatedAt = now), isSendingStatus = true) }
            expectState { copy(isSendingStatus = false) }
            expectState { copy(toast = CrewToast.StatusFailed) }
            expectState { copy(live = serverCrew.live, load = CrewLoad.Loaded) }
            sleeper.release(CrewViewModel.TOAST_DURATION)
            expectState { copy(toast = null) }
        }
    }

    @Test
    fun onlyLeaderOpensMenu() = runTest {
        viewModel().test(this, initialState = state(memberState = "here").copy(crew = crew(state = "here", ownerId = "user_b"))) {
            containerHost.onMenuTapped()
        }
    }

    @Test
    fun dissolveAsksConfirmationThenClosesCrew() = runTest {
        var dissolved = false
        viewModel(FakeCrewUseCase(dissolve = { dissolved = true })).test(
            this,
            initialState = state(memberState = "here").copy(crew = crew(state = "here")),
        ) {
            containerHost.onMenuTapped()
            expectState { copy(destination = CrewDestination.Menu(CrewMenuState(crewName = "펜타 크루", members = emptyList()))) }
            containerHost.onMenuDissolveTapped()
            expectState {
                copy(
                    pendingConfirm = CrewConfirm.Dissolve,
                    destination =
                        CrewDestination.Confirm(
                            title = "크루를 해산할까요?",
                            message = "모든 크루원이 크루에서 나가요. 해산한 크루는 되돌릴 수 없어요.",
                            confirmTitle = "해산하기",
                        ),
                )
            }
            containerHost.onConfirmTapped()
            expectState { copy(pendingConfirm = null, destination = null) }
            expectSideEffect(CrewSideEffect.CrewClosed)
        }
        assertTrue(dissolved)
    }

    @Test
    fun cooldownKeepsGatherClosed() = runTest {
        val initial = state(memberState = "here").let { it.copy(live = it.live?.copy(gatherAvailableAt = now.plusSeconds(60))) }
        viewModel().test(this, initialState = initial) {
            containerHost.onGatherTapped()
        }
        val presentation = CrewPresentation.of(initial, now)
        assertEquals("모여! · 1분 뒤 다시 가능", presentation.gatherTitle)
        assertFalse(presentation.canGather)
    }

    /** Figma S07 F: 보낸 사람은 응답 버튼 대신 응답 요약을 본다. */
    @Test
    fun presentationShowsSenderGatherSummaryAndInactiveMember() {
        val base = state(memberState = "leaving")
        val initial =
            base.copy(
                live =
                    base.live?.copy(
                        gatherRequest =
                            FestivalLiveBoardSnapshot.Crew.GatherRequest(
                                id = "gth_1",
                                zoneId = "zone_a",
                                byUserId = "user_a",
                                at = now.minusSeconds(120),
                                serverSummary = FestivalLiveBoardSnapshot.Crew.GatherRequest.Summary(going = 2, ack = 1),
                            ),
                    ),
            )
        val presentation = CrewPresentation.of(initial, now)
        assertEquals(
            CrewPresentation.Gather(
                title = "민지: 슬램 존으로 모여!",
                detail = "2분 전 · 크루원 모두에게 소리 알림을 보냈어요",
                actions = null,
                summary = "가는 중 2명 · 확인 1명",
            ),
            presentation.gather,
        )
        assertEquals(true, presentation.members.first().isInactive)
        assertEquals("1명 · 1/1(목) 자정까지 유지", presentation.summary)
    }

    /** Figma S07 F: 받은 크루원은 '가는 중' · '확인' 버튼을 보고, 10분이 지나면 배너가 내려간다. */
    @Test
    fun presentationShowsReceiverActionsUntilExpiry() {
        val initial = gatherState(byUserId = "user_b")
        assertEquals(
            CrewPresentation.Gather(
                title = "준호: 슬램 존으로 모여!",
                detail = "방금 · 크루원 모두에게 소리 알림을 보냈어요",
                actions = CrewPresentation.Actions(goingTitle = "슬램 존으로 가는 중", selected = null, isResponding = false),
                summary = null,
            ),
            CrewPresentation.of(initial, now).gather,
        )
        assertNull(CrewPresentation.of(initial, now.plusSeconds(10 * 60)).gather)
    }

    @Test
    fun respondToGatherIgnoresDuplicateTapAndKeepsReply() = runTest {
        var calls = 0
        val reply = FestivalCrewGatherReply("gth_1", "user_a", "민지", FestivalCrewGatherResponse.GOING, now)
        val useCase =
            FakeCrewUseCase(respond = { gatherId, response ->
                calls += 1
                assertEquals("gth_1", gatherId)
                assertEquals(FestivalCrewGatherResponse.GOING, response)
                reply
            })
        viewModel(useCase).test(this, initialState = gatherState(byUserId = "user_b")) {
            containerHost.onGatherResponseTapped(FestivalCrewGatherResponse.GOING)
            expectState { copy(isRespondingGather = true) }
            expectState { copy(isRespondingGather = false, gatherReply = reply) }
            // 이미 고른 응답을 다시 눌러도 보내지 않는다.
            containerHost.onGatherResponseTapped(FestivalCrewGatherResponse.GOING)
        }
        assertEquals(1, calls)
    }

    /** 응답을 기다리는 동안 다시 눌러도 보내지 않는다. 기본 가짜 구현은 호출되면 테스트를 깨뜨린다. */
    @Test
    fun respondingGatherIgnoresAnotherTap() = runTest {
        viewModel().test(this, initialState = gatherState(byUserId = "user_b").copy(isRespondingGather = true)) {
            containerHost.onGatherResponseTapped(FestivalCrewGatherResponse.ACK)
        }
    }

    @Test
    fun senderCannotRespondToOwnGather() = runTest {
        viewModel().test(this, initialState = gatherState(byUserId = "user_a")) {
            containerHost.onGatherResponseTapped(FestivalCrewGatherResponse.GOING)
        }
    }

    @Test
    fun expiredGatherDismissesBannerAndShowsToast() = runTest {
        val sleeper = TestSleeper()
        val serverCrew = crew(state = "here")
        val useCase = FakeCrewUseCase(detail = { serverCrew }, respond = { _, _ -> throw FestivalCrewError.GatherExpired() })
        viewModel(useCase, sleeper::sleep).test(this, initialState = gatherState(byUserId = "user_b")) {
            containerHost.onGatherResponseTapped(FestivalCrewGatherResponse.ACK)
            expectState { copy(isRespondingGather = true) }
            expectState { copy(isRespondingGather = false, expiredGatherId = "gth_1") }
            val toasted = awaitState()
            assertEquals(CrewToast.GatherExpired, toasted.toast)
            assertNull(CrewPresentation.of(toasted, now).gather)
            expectState { copy(crew = serverCrew, live = serverCrew.live, load = CrewLoad.Loaded) }
            sleeper.release(CrewViewModel.TOAST_DURATION)
            expectState { copy(toast = null) }
        }
    }

    /** Figma S07 I: 끝난 크루 안내 띠에 축제 이름을 넣는다. */
    @Test
    fun festivalNameJosaFollowsLastSound() {
        assertEquals("원더리벳 2026이", "원더리벳 2026".withIGa())
        assertEquals("펜타포트가", "펜타포트".withIGa())
        assertEquals("락 페스티벌이", "락 페스티벌".withIGa())
        assertEquals("Fest 2025가", "Fest 2025".withIGa())
        assertEquals("슬램 존으로", "슬램 존".withEuro())
        assertEquals("메인 홀로", "메인 홀".withEuro())
        assertEquals("무대로", "무대".withEuro())
    }

    /** Figma S07 I: 끝난 크루는 끝난 날과 마지막 시각을 보이고, 내 줄에는 "나" 태그를 붙인다. */
    @Test
    fun presentationShowsArchivedCrewAndMe() {
        val initial =
            state(memberState = "here").copy(
                crew =
                    FestivalCrew(
                        id = "crew_a",
                        festivalId = "fst_a",
                        name = "펜타 크루",
                        ownerId = "user_a",
                        members = emptyList(),
                        inviteCode = null,
                        inviteExpiresAt = null,
                        archivedAt = now,
                    ),
            )
        val presentation = CrewPresentation.of(initial, now)
        assertTrue(presentation.isArchived)
        assertEquals("1 / ${FestivalCrew.MAX_MEMBERS}", presentation.memberCount)
        assertEquals("1명 · ${CrewPresentation.festivalDay(now)}에 끝남", presentation.summary)
        assertEquals(LiveBoardPresentation.monthDayTime(now.minusSeconds(60)), presentation.members.first().time)
        assertTrue(presentation.members.first().isMe)
    }

    /** Figma S07: 크루는 마지막 공연이 끝난 날의 자정(KST)까지 유지한다(서버 crewArchiveTime과 같은 기준). */
    @Test
    fun archiveTimeIsMidnightAfterLastShow() {
        listOf(
            Triple("2026-11-21T22:00:00+09:00", "2026-11-22T23:00:00+09:00", "2026-11-23T00:00:00+09:00"),
            Triple("2026-11-23T01:00:00+09:00", "2026-11-23T01:00:00+09:00", "2026-11-24T00:00:00+09:00"),
            Triple("2026-11-23T00:00:00+09:00", "2026-11-23T00:00:00+09:00", "2026-11-23T00:00:00+09:00"),
        ).forEach { (firstEnd, lastEnd, expected) ->
            val festival =
                FestivalPackDocument(
                    id = "fst_a",
                    days = listOf(FestivalPackDocument.Day(null, null, firstEnd), FestivalPackDocument.Day(null, null, lastEnd)),
                )
            assertEquals(java.time.OffsetDateTime.parse(expected).toInstant(), CrewState.archiveTime(festival))
        }
    }

    // MARK: - 고정 값

    private fun state(memberState: String): CrewState {
        val festival =
            FestivalPackDocument(
                id = "fst_a",
                days = listOf(FestivalPackDocument.Day(null, null, "2026-01-01T00:00:00.000Z")),
                zones = listOf(FestivalPackDocument.Zone(id = "zone_a", name = "슬램 존")),
            )
        return CrewState(
            festivalId = "fst_a",
            crewId = "crew_a",
            zones = festival.zones.orEmpty(),
            archivesAt = CrewState.archiveTime(festival),
            festivalName = festival.name,
            sessionUserId = "user_a",
            live = live(state = memberState, zoneId = if (memberState == "leaving") null else "zone_a", updatedAt = now.minusSeconds(60)),
            now = now,
        )
    }

    /** 민지(나)와 준호가 있는 크루에 [byUserId]가 보낸 진행 중 모여. */
    private fun gatherState(byUserId: String): CrewState = state(memberState = "here").copy(
        live =
            FestivalLiveBoardSnapshot.Crew(
                id = "crew_a",
                name = "펜타 크루",
                members =
                    listOf(
                        FestivalLiveBoardSnapshot.Crew.Member("user_a", "민지", "here", "zone_a", now),
                        FestivalLiveBoardSnapshot.Crew.Member("user_b", "준호", "here", "zone_a", now),
                    ),
                ownerId = "user_a",
                gatherRequest =
                    FestivalLiveBoardSnapshot.Crew.GatherRequest(
                        id = "gth_1",
                        zoneId = "zone_a",
                        byUserId = byUserId,
                        at = now,
                        expiresAt = now.plusSeconds(10 * 60),
                    ),
            ),
    )

    private fun live(
        state: String,
        zoneId: String?,
        updatedAt: Instant,
    ) = FestivalLiveBoardSnapshot.Crew(
        id = "crew_a",
        name = "펜타 크루",
        members = listOf(FestivalLiveBoardSnapshot.Crew.Member("user_a", "민지", state, zoneId, updatedAt)),
        ownerId = "user_a",
    )

    private fun crew(state: String, ownerId: String = "user_a") = FestivalCrew(
        id = "crew_a",
        festivalId = "fst_a",
        name = "펜타 크루",
        ownerId = ownerId,
        members = emptyList(),
        inviteCode = if (ownerId == "user_a") "K7P2QX" else null,
        inviteExpiresAt = null,
        live =
            FestivalLiveBoardSnapshot.Crew(
                id = "crew_a",
                name = "펜타 크루",
                members = listOf(FestivalLiveBoardSnapshot.Crew.Member("user_a", "민지", state, "zone_a", now)),
                ownerId = ownerId,
            ),
    )
}
