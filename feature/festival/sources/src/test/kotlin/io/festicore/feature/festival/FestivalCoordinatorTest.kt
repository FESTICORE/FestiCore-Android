package io.festicore.feature.festival

import io.festicore.feature.festival.liveboard.LiveBoardBoundary
import kotlinx.coroutines.test.runTest
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
    fun `존 지도 밖의 보드 경계 이동은 받아 두기만 한다`() = runTest {
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
}
