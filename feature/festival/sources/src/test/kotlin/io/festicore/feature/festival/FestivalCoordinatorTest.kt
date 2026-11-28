package io.festicore.feature.festival

import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.entity.FestivalPackState
import io.festicore.domain.festival.entity.FestivalSchedule
import io.festicore.domain.festival.entity.FestivalScheduleSaved
import io.festicore.domain.festival.schedule.FestivalScheduleUseCase
import io.festicore.feature.festival.festivallist.FakeCurrentUserProvider
import io.festicore.feature.festival.festivallist.FakePackUseCase
import io.festicore.feature.festival.flag.FlagBoard
import io.festicore.feature.festival.flagdetail.FlagDetailContext
import io.festicore.feature.festival.liveboard.LiveBoardBoundary
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

class FestivalCoordinatorTest {
    private val board = FestivalRoute.LiveBoard("fst_1")

    private class PrepScheduleUseCase(val read: suspend () -> FestivalSchedule = { FestivalSchedule(emptyList()) }) :
        FestivalScheduleUseCase {
        val saved = mutableListOf<List<String>>()
        val notified = mutableListOf<FestivalScheduleSaved>()
        override val savedSchedules = emptyFlow<FestivalScheduleSaved>()
        override suspend fun mySchedule(festivalId: String) = read()
        override suspend fun saveMySchedule(
            festivalId: String,
            setIds: List<String>,
            clientAt: Instant,
        ): FestivalSchedule {
            saved += setIds
            return FestivalSchedule(setIds)
        }
        override suspend fun notifyMyScheduleSaved(saved: FestivalScheduleSaved) {
            notified += saved
        }
    }

    @Test
    fun prepEntryThenBackInvalidatesOtherDownloadsFromSameListVisit() = runTest {
        val vm = FestivalCoordinator(FakeCurrentUserProvider("user"))
        val originalToken = vm.listVisitToken
        vm.test(this) {
            containerHost.onPrepRequested("festival-a", originalToken) { true }
            expectState { copy(routes = routes + FestivalRoute.PrepFlags("festival-a")) }
            containerHost.goBack()
            expectState { copy(routes = listOf(FestivalRoute.List)) }
            containerHost.onPrepRequested("festival-b", originalToken) { true }
            testScheduler.runCurrent()
            expectNoItems()
        }
    }

    @Test
    fun successfulCachedLookupOpensScheduleAndDuplicateRequestIsIgnored() = runTest {
        val release = CompletableDeferred<Unit>()
        val readStarted = CompletableDeferred<Unit>()
        var reads = 0
        val schedule = PrepScheduleUseCase {
            reads++
            readStarted.complete(Unit)
            release.await()
            FestivalSchedule(listOf("set-a"))
        }
        val document = FestivalPackDocument(id = "festival", name = "축제")
        val pack = CachedFestivalPack("festival", 1, "v1", null, document)
        val packs = FakePackUseCase().apply { state.value = FestivalPackState(packs = mapOf("festival" to pack)) }
        FestivalCoordinator(FakeCurrentUserProvider("user"), packUseCase = packs, scheduleUseCase = schedule).test(this) {
            containerHost.onPrepRequested("festival", 0) { it == document }
            readStarted.await()
            containerHost.onPrepRequested("festival", 0) { it == document }.join()
            assertEquals(1, reads)
            release.complete(Unit)
            expectState { copy(routes = routes + FestivalRoute.PrepSchedule("festival", listOf("set-a"))) }
            containerHost.onPrepRequested("festival", 0) { true }.join()
            expectNoItems()
        }
        assertTrue(schedule.saved.isEmpty())
    }

    @Test
    fun accountChangeRejectsOldAccountLookup() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val user = FakeCurrentUserProvider("old-user")
        val schedule = PrepScheduleUseCase {
            withContext(NonCancellable) {
                started.complete(Unit)
                release.await()
                FestivalSchedule(emptyList())
            }
        }
        FestivalCoordinator(user, scheduleUseCase = schedule).test(this) {
            val subscription = runOnCreate()
            containerHost.onPrepRequested("festival", 0) { true }
            started.await()
            user.currentUserId.value = "new-user"
            testScheduler.runCurrent()
            release.complete(Unit)
            testScheduler.runCurrent()
            expectNoItems()
            subscription.cancel()
        }
        assertTrue(schedule.saved.isEmpty())
    }

    @Test
    fun lookupCannotReopenAfterBoardVisitAndReturnToList() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val schedule = PrepScheduleUseCase {
            withContext(NonCancellable) {
                started.complete(Unit)
                release.await()
                FestivalSchedule(emptyList())
            }
        }
        FestivalCoordinator(FakeCurrentUserProvider("user"), scheduleUseCase = schedule).test(this) {
            containerHost.onPrepRequested("festival", 0) { true }
            started.await()
            containerHost.onFestivalSelected("other")
            awaitState()
            containerHost.goBack()
            awaitState()
            release.complete(Unit)
            testScheduler.runCurrent()
            expectNoItems()
        }
    }

    @Test
    fun completedScheduleNotifiesOnceAndRejectsDuplicateCallback() = runTest {
        val useCase = PrepScheduleUseCase()
        val route = FestivalRoute.PrepSchedule("festival", emptyList())
        FestivalCoordinator(FakeCurrentUserProvider("user"), scheduleUseCase = useCase)
            .test(this, initialState = FestivalCoordinatorState(routes = listOf(FestivalRoute.List, route))) {
                containerHost.onPrepScheduleFinished(route, setOf("set"))
                expectState { copy(routes = routes + FestivalRoute.PrepFlags("festival")) }
                containerHost.onPrepScheduleFinished(route, setOf("set")).join()
                expectNoItems()
            }
        assertEquals(listOf(FestivalScheduleSaved("festival", setOf("set"))), useCase.notified)
        assertTrue(useCase.saved.isEmpty())
    }

    @Test
    fun asynchronousLookupCannotReopenPrepAfterExternalNavigation() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val useCase = PrepScheduleUseCase {
            withContext(NonCancellable) {
                started.complete(Unit)
                release.await()
                FestivalSchedule(emptyList())
            }
        }
        FestivalCoordinator(FakeCurrentUserProvider("user"), scheduleUseCase = useCase).test(this) {
            containerHost.onPrepRequested("festival", 0) { true }
            started.await()
            containerHost.onExternalNavigation().join()
            release.complete(Unit)
            testScheduler.runCurrent()
            expectNoItems()
        }
        assertTrue(useCase.saved.isEmpty())
        assertTrue(useCase.notified.isEmpty())
    }

    @Test
    fun prepStepsKeepBackStackAndLaterRemovesEveryStep() = runTest {
        val schedule = FestivalRoute.PrepSchedule("festival", emptyList())
        val flags = FestivalRoute.PrepFlags("festival")
        val checklist = FestivalRoute.PrepChecklist("festival", "")
        FestivalCoordinator(
            FakeCurrentUserProvider("user"),
        ).test(this, initialState = FestivalCoordinatorState(routes = listOf(FestivalRoute.List, schedule))) {
            containerHost.onPrepScheduleFinished(schedule)
            expectState { copy(routes = routes + flags) }
            containerHost.onPrepFlagsFinished(flags)
            expectState { copy(routes = routes + checklist) }
            containerHost.goBack()
            expectState { copy(routes = routes.dropLast(1)) }
            containerHost.onPrepDismissed()
            expectState { copy(routes = listOf(FestivalRoute.List)) }
        }
    }

    @Test
    fun pendingPrepBlocksUserNavigationButExternalInviteClearsIt() = runTest {
        val flags = FestivalRoute.PrepFlags("festival")
        FestivalCoordinator(
            FakeCurrentUserProvider("user"),
        ).test(this, initialState = FestivalCoordinatorState(routes = listOf(FestivalRoute.List, flags))) {
            containerHost.onPrepNavigationBlocked(flags, true)
            expectState { copy(prepNavigationBlocked = true) }
            containerHost.goBack()
            containerHost.onPrepFlagsFinished(flags)
            containerHost.onPrepDismissed()
            expectNoItems()
            containerHost.onCrewInviteOpened("invite")
            expectState { copy(routes = listOf(FestivalRoute.List), prepNavigationBlocked = false) }
            expectState { copy(routes = routes + FestivalRoute.CrewJoin("invite")) }
        }
    }

    @Test
    fun bufferedDownloadTokenFromPreviousListVisitIsIgnored() = runTest {
        val vm = FestivalCoordinator(FakeCurrentUserProvider("user"))
        val token = vm.listVisitToken
        vm.test(this) {
            containerHost.onFestivalSelected("other")
            awaitState()
            containerHost.goBack()
            awaitState()
            containerHost.onPrepRequested("festival", token) { true }
            expectNoItems()
        }
    }

    @Test
    fun lookupExceptionMissingCacheAndEmptyDaysSkipScheduleWithoutWriting() = runTest {
        for (scenario in listOf("exception", "missing-cache", "empty-days")) {
            val schedule = PrepScheduleUseCase {
                if (scenario == "exception") throw IllegalStateException("offline")
                FestivalSchedule(emptyList())
            }
            val document = FestivalPackDocument(id = "festival", name = "축제")
            val packs = FakePackUseCase().apply {
                if (scenario !=
                    "missing-cache"
                ) {
                    state.value =
                        FestivalPackState(packs = mapOf("festival" to CachedFestivalPack("festival", 1, "v1", null, document)))
                }
            }
            FestivalCoordinator(FakeCurrentUserProvider("user"), packUseCase = packs, scheduleUseCase = schedule).test(this) {
                containerHost.onPrepRequested("festival", 0) { scenario != "empty-days" }
                expectState { copy(routes = routes + FestivalRoute.PrepFlags("festival")) }
            }
            assertTrue("$scenario must not save a schedule", schedule.saved.isEmpty())
            assertTrue("$scenario must not publish a schedule", schedule.notified.isEmpty())
        }
    }

    @Test
    fun scheduleLookupFailureSkipsWithoutSavingAndTermsGoToParent() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider("user")).test(this) {
            containerHost.onPrepRequested("festival", 0) { true }
            expectState { copy(routes = routes + FestivalRoute.PrepFlags("festival")) }
            containerHost.onPrepTermsRequested()
            expectSideEffect(FestivalCoordinatorSideEffect.TermsAcceptanceRequested)
        }
    }

    @Test
    fun `목록에서 축제를 고르면 라이브 보드를 쌓고 같은 보드는 다시 쌓지 않는다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this) {
            containerHost.onFestivalSelected("fst_1")
            expectState { copy(selectedFestivalId = "fst_1", routes = listOf(FestivalRoute.List, board)) }
            containerHost.onFestivalSelected("fst_1")
        }
    }

    @Test
    fun `보드에서 뒤로 가면 목록으로 돌아가고 목록뿐이면 스택을 비우지 않는다`() = runTest {
        FestivalCoordinator(
            FakeCurrentUserProvider(),
        ).test(this, initialState = FestivalCoordinatorState(routes = listOf(FestivalRoute.List, board))) {
            containerHost.goBack()
            expectState { copy(routes = listOf(FestivalRoute.List)) }
            containerHost.goBack()
        }
    }

    /** iOS `syncTimetable`: 라이브 스택에 보드가 없으면 타임테이블은 축제 없음이다. */
    @Test
    fun `보드가 스택에서 빠지면 보드 공유 선택을 비운다`() = runTest {
        var cleared = 0
        FestivalCoordinator(FakeCurrentUserProvider(), clearBoardSelection = { cleared++ })
            .test(this, initialState = FestivalCoordinatorState(routes = listOf(FestivalRoute.List, board))) {
                runOnCreate()
                testScheduler.runCurrent()
                assertEquals(0, cleared)
                containerHost.goBack()
                expectState { copy(routes = listOf(FestivalRoute.List)) }
                testScheduler.runCurrent()
                assertEquals(1, cleared)
                containerHost.onFestivalSelected("fst_1")
                expectState { copy(selectedFestivalId = "fst_1", routes = listOf(FestivalRoute.List, board)) }
                testScheduler.runCurrent()
                assertEquals(1, cleared)
                cancelAndIgnoreRemainingItems()
            }
    }

    @Test
    fun `탭 바는 목록뿐이거나 보드에 시트 · 다이얼로그가 떠 있을 때 숨긴다`() = runTest {
        assertTrue(FestivalCoordinatorState().hidesTabBar)
        val onBoard = FestivalCoordinatorState(routes = listOf(FestivalRoute.List, board))
        assertFalse(onBoard.hidesTabBar)
        assertTrue(onBoard.copy(isLiveBoardPresentingModal = true).hidesTabBar)

        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard) {
            containerHost.onLiveBoardPresentingModalChanged(true)
            expectState { copy(isLiveBoardPresentingModal = true) }
            // 보드를 닫으면 시트 표시도 지운다.
            containerHost.goBack()
            expectState { copy(routes = listOf(FestivalRoute.List), isLiveBoardPresentingModal = false) }
        }
    }

    @Test
    fun `라이브 보드의 기수 경계는 기수 보드를 한 번 쌓는다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this) {
            val bearer = LiveBoardBoundary.Bearer("fst_1", "flag_1", null)
            containerHost.onLiveBoardBoundary(bearer)
            val route = FestivalRoute.BearerBoard("fst_1", "flag_1")
            expectState { copy(routes = listOf(FestivalRoute.List, route)) }
            containerHost.onLiveBoardBoundary(bearer)
        }
    }

    /** iOS `MTCSc:127-134` `packDirectionsRequested`: 보드 "가는 길"은 상위(메인 탭)에 마이 팩 상세를 요청한다. */
    @Test
    fun `보드 가는 길 경계는 상위에 마이 팩 상세를 요청하고 스택은 그대로 둔다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this) {
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.PackDirections("fst_1", "축제"))
            expectSideEffect(FestivalCoordinatorSideEffect.PackDirectionsRequested("fst_1", "축제"))
        }
    }

    @Test
    fun `보드 로그인 경계는 문맥을 담아 로그인을 요청한다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this) {
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.Login)
            expectSideEffect(FestivalCoordinatorSideEffect.LoginRequested(null))
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.CrewLogin)
            expectSideEffect(FestivalCoordinatorSideEffect.LoginRequested(FestivalLoginContext.Crew))
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.FollowLogin)
            expectSideEffect(FestivalCoordinatorSideEffect.LoginRequested(FestivalLoginContext.Follow))
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.CrewJoinLogin("주말 크루"))
            expectSideEffect(FestivalCoordinatorSideEffect.LoginRequested(FestivalLoginContext.CrewJoin("주말 크루")))
        }
    }

    @Test
    fun `보드 팔로우 안내의 깃발 찾기는 깃발 탭을 요청한다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this) {
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.FlagDirectory)
            expectSideEffect(FestivalCoordinatorSideEffect.FlagTabRequested)
        }
    }

    @Test
    fun `깃발 탭은 스택에 보드가 있을 때만 보드가 알린 값을 따른다`() = runTest {
        val flagBoard = FlagBoard("fst_1", FlagDetailContext(accountId = "user-a"))
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard) {
            containerHost.onLiveBoardChanged(board, flagBoard)
            val reported = awaitState()
            assertEquals(flagBoard, reported.flagBoard)
            // 같은 값이면 상태를 바꾸지 않는다.
            containerHost.onLiveBoardChanged(board, flagBoard)
            // 존 지도가 위에 있어도 그 아래 보드를 따른다.
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.ZoneMap("fst_1", "zon_a"))
            assertEquals(flagBoard, awaitState().flagBoard)
            containerHost.onDeeplinkFestivalListOpened()
            assertEquals(null, awaitState().flagBoard)
        }
    }

    @Test
    fun `새 보드 화면을 쌓으면 그 보드가 알리기 전까지 깃발 탭은 이전 보드 값을 따르지 않는다`() = runTest {
        val flagBoard = FlagBoard("fst_1", FlagDetailContext(accountId = "user-a"))
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard) {
            containerHost.onLiveBoardChanged(board, flagBoard)
            assertEquals(flagBoard, awaitState().flagBoard)
            // 목록으로 갔다가 같은 축제 보드를 다시 열어도 새 보드 화면이다.
            containerHost.onDeeplinkFestivalListOpened()
            awaitState()
            containerHost.onDeeplinkFestivalOpened("fst_1")
            assertEquals(null, awaitState().flagBoard)
            // 다른 축제를 고르면 위에 쌓인 새 보드가 알리기 전까지 비어 있다.
            containerHost.onLiveBoardChanged(board, flagBoard)
            assertEquals(flagBoard, awaitState().flagBoard)
            containerHost.onFestivalSelected("fst_3")
            assertEquals(null, awaitState().flagBoard)
        }
    }

    @Test
    fun `맨 위 보드가 아닌 보드 화면의 늦은 값은 받지 않는다`() = runTest {
        val stale = FlagBoard("fst_1", FlagDetailContext(accountId = "user-a"))
        val fresh = FlagBoard("fst_2", FlagDetailContext(accountId = "user-a"))
        val newBoard = FestivalRoute.LiveBoard("fst_2")
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard) {
            containerHost.onDeeplinkFestivalOpened("fst_2")
            awaitState()
            containerHost.onLiveBoardChanged(board, stale)
            containerHost.onLiveBoardChanged(newBoard, fresh)
            assertEquals(fresh, awaitState().flagBoard)
        }
    }

    private val zoneMap = FestivalRoute.ZoneMap("fst_1", "zon_a")
    private val onBoard = FestivalCoordinatorState(routes = listOf(FestivalRoute.List, board))

    @Test
    fun `크루 존 칩은 존 지도를 쌓고 이미 있으면 다시 쌓지 않는다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard) {
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.ZoneMap("fst_1", "zon_a"))
            expectState { copy(routes = listOf(FestivalRoute.List, board, zoneMap)) }
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.ZoneMap("fst_1", "zon_b"))
        }
    }

    @Test
    fun `존 지도에서 뒤로 가면 보드로 돌아가고 존 지도 위에서도 탭 바는 보인다`() = runTest {
        val onZoneMap = onBoard.copy(routes = onBoard.routes + zoneMap)
        assertFalse(onZoneMap.hidesTabBar)

        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onZoneMap) {
            containerHost.goBack()
            expectState { copy(routes = listOf(FestivalRoute.List, board)) }
        }
    }

    @Test
    fun `존 지도의 팔로우 안내는 보드로 돌아가 깃발 탭을 요청한다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard.copy(routes = onBoard.routes + zoneMap)) {
            containerHost.onZoneMapFlagDirectoryRequested()
            expectState { copy(routes = listOf(FestivalRoute.List, board)) }
            expectSideEffect(FestivalCoordinatorSideEffect.FlagTabRequested)
        }
    }

    @Test
    fun `계정이 바뀌면 존 지도를 스택에서 뺀다`() = runTest {
        val currentUser = FakeCurrentUserProvider("user-a")
        FestivalCoordinator(currentUser).test(this, initialState = onBoard.copy(routes = onBoard.routes + zoneMap)) {
            val subscription = runOnCreate()
            currentUser.currentUserId.value = "user-b"
            expectState { copy(routes = listOf(FestivalRoute.List, board)) }
            subscription.cancel()
        }
    }

    // MARK: - 알림함 (iOS FestivalNotificationInboxRoutingTests)

    private val inbox = FestivalRoute.NotificationInbox

    @Test
    fun opensInboxAndReturns() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider("user-a")).test(this, initialState = onBoard) {
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.NotificationInbox)
            expectState { copy(routes = listOf(FestivalRoute.List, board, inbox)) }
            // 이미 열려 있으면 다시 쌓지 않는다.
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.NotificationInbox)
            containerHost.goBack()
            expectState { copy(routes = listOf(FestivalRoute.List, board)) }
        }
    }

    @Test
    fun `알림함이 맨 위면 탭 바를 숨긴다`() {
        assertTrue(onBoard.copy(routes = onBoard.routes + inbox).hidesTabBar)
        assertFalse(onBoard.hidesTabBar)
    }

    @Test
    fun accountChangeClearsInboxAndBadge() = runTest {
        val currentUser = FakeCurrentUserProvider("user-a")
        val initial = onBoard.copy(routes = onBoard.routes + inbox, pendingBoardFestivalId = "fst_2")
        FestivalCoordinator(currentUser).test(this, initialState = initial) {
            val subscription = runOnCreate()
            currentUser.currentUserId.value = null
            expectState { copy(routes = listOf(FestivalRoute.List, board), pendingBoardFestivalId = null) }
            subscription.cancel()
        }
    }

    @Test
    fun switchedAccountClosesInbox() = runTest {
        val currentUser = FakeCurrentUserProvider("user-a")
        FestivalCoordinator(currentUser).test(this, initialState = onBoard.copy(routes = onBoard.routes + inbox)) {
            val subscription = runOnCreate()
            currentUser.currentUserId.value = "user-b"
            expectState { copy(routes = listOf(FestivalRoute.List, board)) }
            subscription.cancel()
        }
    }

    @Test
    fun `알림으로 보드 카탈로그에 있는 축제를 열면 알림함을 닫고 보드에 전환을 맡긴다`() = runTest {
        val onInbox = onBoard.copy(routes = onBoard.routes + inbox, liveBoardFestivalIds = setOf("fst_1", "fst_2"))
        FestivalCoordinator(FakeCurrentUserProvider("user-a")).test(this, initialState = onInbox) {
            assertTrue(containerHost.onNotificationFestivalOpened("fst_2"))
            expectState { copy(routes = listOf(FestivalRoute.List, board), pendingBoardFestivalId = "fst_2") }
            containerHost.onPendingBoardFestivalConsumed()
            expectState { copy(pendingBoardFestivalId = null) }
        }
    }

    @Test
    fun `보드 카탈로그에 없는 축제 알림은 알림함에 남는다`() = runTest {
        val onInbox = onBoard.copy(routes = onBoard.routes + inbox, liveBoardFestivalIds = setOf("fst_1"))
        FestivalCoordinator(FakeCurrentUserProvider("user-a")).test(this, initialState = onInbox) {
            assertFalse(containerHost.onNotificationFestivalOpened("fst_other"))
        }
    }

    @Test
    fun `보드가 알린 축제 목록을 받아 둔다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard) {
            containerHost.onLiveBoardFestivalsChanged(setOf("fst_1"))
            expectState { copy(liveBoardFestivalIds = setOf("fst_1")) }
        }
    }

    @Test
    fun `딥링크 축제 열기는 목록 위를 비우고 그 축제 보드를 쌓는다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this) {
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.NotificationInbox)
            expectState { copy(routes = listOf(FestivalRoute.List, FestivalRoute.NotificationInbox)) }

            containerHost.onDeeplinkFestivalOpened("fst_1")
            expectState { copy(selectedFestivalId = "fst_1", routes = listOf(FestivalRoute.List, board)) }
        }
    }

    @Test
    fun `같은 보드가 열려 있으면 딥링크 축제를 보드에 대기로 넘긴다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this) {
            containerHost.onFestivalSelected("fst_1")
            expectState { copy(selectedFestivalId = "fst_1", routes = listOf(FestivalRoute.List, board)) }

            containerHost.onDeeplinkFestivalOpened("fst_1")
            expectState { copy(pendingBoardFestivalId = "fst_1") }
        }
    }

    @Test
    fun `딥링크 축제 목록 열기는 목록만 남긴다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this) {
            containerHost.onFestivalSelected("fst_1")
            expectState { copy(selectedFestivalId = "fst_1", routes = listOf(FestivalRoute.List, board)) }

            containerHost.onDeeplinkFestivalListOpened()
            expectState { copy(routes = listOf(FestivalRoute.List)) }
        }
    }

    // MARK: - 크루 (iOS FestivalCrewCreateRoutingTests)

    private val crewCreate = FestivalRoute.CrewCreate("fst_1")
    private val crew = FestivalRoute.Crew("fst_1", "crew")

    @Test
    fun `크루 만들기는 만들기 화면을 쌓고 탭 바를 숨기며 뒤로 가면 라이브 보드로 돌아온다`() = runTest {
        assertFalse(onBoard.hidesTabBar)
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard) {
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.CrewCreate("fst_1"))
            val pushed = awaitState()
            assertEquals(listOf(FestivalRoute.List, board, crewCreate), pushed.routes)
            assertTrue(pushed.hidesTabBar)
            // 이미 열려 있으면 다시 쌓지 않는다.
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.CrewCreate("fst_1"))
            containerHost.onCrewBack()
            val back = awaitState()
            assertEquals(listOf(FestivalRoute.List, board), back.routes)
            assertFalse(back.hidesTabBar)
        }
    }

    @Test
    fun `만든 뒤 크루 보기는 만들기 화면을 크루 화면으로 바꾸고 크루에서 뒤로 가면 라이브 보드다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard.copy(routes = onBoard.routes + crewCreate)) {
            containerHost.onCrewCreateOpenCrew("fst_1", "crew")
            expectState { copy(routes = listOf(FestivalRoute.List, board)) }
            val opened = awaitState()
            assertEquals(listOf(FestivalRoute.List, board, crew), opened.routes)
            assertTrue(opened.hidesTabBar)
            containerHost.onCrewBack()
            val back = awaitState()
            assertEquals(listOf(FestivalRoute.List, board), back.routes)
            assertFalse(back.hidesTabBar)
        }
    }

    @Test
    fun `보드 크루 카드는 크루 화면을 쌓고 이미 있으면 무시하며 크루가 닫히면 뺀다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard) {
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.Crew("fst_1", "crew"))
            expectState { copy(routes = listOf(FestivalRoute.List, board, crew)) }
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.Crew("fst_1", "crew"))
            containerHost.onCrewClosed()
            expectState { copy(routes = listOf(FestivalRoute.List, board)) }
        }
    }

    @Test
    fun loginRequestClosesCreateScreen() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard.copy(routes = onBoard.routes + crewCreate)) {
            containerHost.onCrewCreateLoginRequested()
            expectState { copy(routes = listOf(FestivalRoute.List, board)) }
            expectSideEffect(FestivalCoordinatorSideEffect.LoginRequested(FestivalLoginContext.Crew))
        }
    }

    @Test
    fun `계정이 바뀌면 열린 만들기 · 크루 화면을 닫는다`() = runTest {
        val currentUser = FakeCurrentUserProvider("user-a")
        FestivalCoordinator(currentUser).test(this, initialState = onBoard.copy(routes = onBoard.routes + crewCreate)) {
            val subscription = runOnCreate()
            currentUser.currentUserId.value = "user-b"
            expectState { copy(routes = listOf(FestivalRoute.List, board)) }
            subscription.cancel()
        }
    }

    // MARK: - 참여 확인 (iOS FestivalCrewCreateRoutingTests 중 C2)

    private val crewJoin = FestivalRoute.CrewJoin("K7P2QX")

    @Test
    fun crewJoinLoginRequestUsesJoinContext() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard.copy(routes = onBoard.routes + crewJoin)) {
            containerHost.onCrewJoinLoginRequested(null)
            // 화면은 그대로 두고 참여 문맥으로 로그인을 요청한다(일반 크루 로그인과 구별된다).
            expectSideEffect(FestivalCoordinatorSideEffect.LoginRequested(FestivalLoginContext.CrewJoin(crewName = null)))
        }
    }

    @Test
    fun `초대 코드는 기존 참여 확인 · 크루 화면을 빼고 참여 확인을 쌓고 탭 바를 숨기며 닫으면 보드로 돌아온다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard.copy(routes = onBoard.routes + crew)) {
            containerHost.onCrewInviteOpened("K7P2QX")
            val opened = awaitState()
            assertEquals(listOf(FestivalRoute.List, board, crewJoin), opened.routes)
            assertTrue(opened.hidesTabBar)
            containerHost.onCrewJoinClosed()
            expectState { copy(routes = listOf(FestivalRoute.List, board)) }
        }
    }

    @Test
    fun `참여하면 참여 확인을 크루 화면으로 바꾼다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard.copy(routes = onBoard.routes + crewJoin)) {
            containerHost.onCrewJoined("fst_1", "crew")
            expectState { copy(routes = listOf(FestivalRoute.List, board, crew)) }
        }
    }
}
