package io.festicore.feature.festival
import io.festicore.feature.festival.festivallist.FakeCurrentUserProvider
import io.festicore.feature.festival.liveboard.LiveBoardBoundary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

class FestivalCoordinatorTest {
    private val board = FestivalRoute.LiveBoard("fst_1")

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
    fun `존 지도 · 알림함 밖의 보드 경계 이동은 받아 두기만 한다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this) {
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.FlagDirectory)
            expectState { copy(lastBoundary = LiveBoardBoundary.FlagDirectory) }
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
    fun `만들기 화면에서 세션이 끊기면 화면을 닫고 크루 로그인 경계를 남긴다`() = runTest {
        FestivalCoordinator(FakeCurrentUserProvider()).test(this, initialState = onBoard.copy(routes = onBoard.routes + crewCreate)) {
            containerHost.onCrewCreateLoginRequested()
            expectState { copy(routes = listOf(FestivalRoute.List, board), lastBoundary = LiveBoardBoundary.CrewLogin) }
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
}
