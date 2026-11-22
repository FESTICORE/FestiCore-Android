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
        FestivalCoordinator().test(this) {
            containerHost.onFestivalSelected("fst_1")
            expectState { copy(selectedFestivalId = "fst_1", routes = listOf(FestivalRoute.List, board)) }
            containerHost.onFestivalSelected("fst_1")
        }
    }

    @Test
    fun `보드에서 뒤로 가면 목록으로 돌아가고 목록뿐이면 스택을 비우지 않는다`() = runTest {
        FestivalCoordinator().test(this, initialState = FestivalCoordinatorState(routes = listOf(FestivalRoute.List, board))) {
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

        FestivalCoordinator().test(this, initialState = onBoard) {
            containerHost.onLiveBoardPresentingModalChanged(true)
            expectState { copy(isLiveBoardPresentingModal = true) }
            // 보드를 닫으면 시트 표시도 지운다.
            containerHost.goBack()
            expectState { copy(routes = listOf(FestivalRoute.List), isLiveBoardPresentingModal = false) }
        }
    }

    @Test
    fun `보드의 경계 이동은 받아 두기만 한다`() = runTest {
        FestivalCoordinator().test(this) {
            containerHost.onLiveBoardBoundary(LiveBoardBoundary.FlagDirectory)
            expectState { copy(lastBoundary = LiveBoardBoundary.FlagDirectory) }
        }
    }
}
