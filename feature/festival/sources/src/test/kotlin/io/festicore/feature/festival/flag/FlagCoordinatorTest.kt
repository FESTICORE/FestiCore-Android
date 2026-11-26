package io.festicore.feature.festival.flag

import io.festicore.domain.festival.entity.FestivalFlagFollowResult
import io.festicore.feature.festival.FlagRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `FlagRoutingTests` 중 Coordinator 안 흐름. 탭 바 · MainTab 문맥 · 딥링크 해석은 T1에서 옮긴다. */
class FlagCoordinatorTest {
    private val detail = FlagRoute.Detail("flg_1")
    private val onDetail = FlagCoordinatorState(routes = listOf(FlagRoute.Directory, detail))

    @Test
    fun `디렉토리 카드를 누르면 상세를 연다`() = runTest {
        FlagCoordinator().test(this) {
            containerHost.openDetail("flg_1")
            expectState { copy(routes = listOf(FlagRoute.Directory, detail)) }
        }
    }

    @Test
    fun `상세 뒤로 가기와 스와이프는 디렉토리로 돌아간다`() = runTest {
        FlagCoordinator().test(this, initialState = onDetail) {
            containerHost.goBack()
            expectState { copy(routes = listOf(FlagRoute.Directory)) }
            // 목록뿐이면 스택을 비우지 않는다.
            containerHost.goBack()
        }
    }

    /** 상세 기수 메뉴 → 이동 화면 → 옮기면 상세로 돌아와 다시 불러오게 알린다(Android 먼저, 백엔드 #409). */
    @Test
    fun `깃발을 옮기면 이동 화면을 닫고 상세에 알린다`() = runTest {
        val transfer = FlagRoute.FestivalTransfer("flg_1")
        FlagCoordinator().test(this, initialState = onDetail) {
            containerHost.openFestivalTransfer("flg_1")
            expectState { copy(routes = listOf(FlagRoute.Directory, detail, transfer)) }
            containerHost.onFestivalTransferred("flg_1")
            expectState { copy(routes = listOf(FlagRoute.Directory, detail), transferredFlagId = "flg_1") }
            containerHost.onFestivalTransferConsumed()
            expectState { copy(transferredFlagId = null) }
        }
    }

    @Test
    fun `상세에서 바꾼 팔로우를 디렉토리 카드에도 반영한다`() = runTest {
        val result = FestivalFlagFollowResult(flagId = "flg_1", isFollowing = true, followerCount = 11)
        val snapshot = FlagFixtures.board(listOf(FlagFixtures.flag("flg_1", count = 10)))
        FlagCoordinator().test(this, initialState = onDetail) {
            containerHost.onDetailFollowChanged(result, snapshot)
            expectSideEffect(FlagCoordinatorSideEffect.FollowChangedElsewhere(result, baseCount = 10))
            // 목록이 아직 받지 못한 깃발은 기준 수가 없다.
            containerHost.onDetailFollowChanged(result, null)
            expectSideEffect(FlagCoordinatorSideEffect.FollowChangedElsewhere(result, baseCount = null))
        }
    }

    @Test
    fun `깃발 링크는 상세를 연다`() = runTest {
        FlagCoordinator().test(this, initialState = onDetail) {
            // 다른 깃발 링크는 쌓지 않고 바꾼다(상세 하나만).
            containerHost.openDetail("flg_9")
            expectState { copy(routes = listOf(FlagRoute.Directory, FlagRoute.Detail("flg_9"))) }
        }
    }

    @Test
    fun `계정이 바뀌면 목록에 알리고 스택은 그대로 둔다`() {
        val coordinator = FlagCoordinator()
        assertFalse(coordinator.onAccountChanged("usr_1"))
        assertFalse(coordinator.onAccountChanged("usr_1"))
        assertTrue(coordinator.onAccountChanged(null))
        assertTrue(coordinator.onAccountChanged("usr_2"))
        assertTrue(coordinator.container.stateFlow.value == FlagCoordinatorState())
    }
}
