package io.festicore.app.navigation

import io.festicore.core.utility.deeplink.FestiCoreDeeplink
import io.festicore.ui.designkit.component.navigation.FCTabBarTab
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `DeeplinkRoutingTests` 중 Android에 있는 경로(축제 목록 · 축제)와 아직 열지 않는 경로. */
class MainTabCoordinatorTest {
    private val flagTab = MainTabCoordinatorState(selectedTab = FCTabBarTab.Flag)

    @Test
    fun `축제 딥링크는 라이브 탭으로 바꾸고 라이브 흐름에 축제 열기를 넘긴다`() = runTest {
        MainTabCoordinator().test(this, flagTab) {
            containerHost.open(FestiCoreDeeplink.Festival("fst_a"))
            expectState { copy(selectedTab = FCTabBarTab.Live, liveRequest = LiveTabRequest.Festival("fst_a")) }

            containerHost.onLiveRequestConsumed()
            expectState { copy(liveRequest = null) }
        }
    }

    @Test
    fun `축제 목록 딥링크는 라이브 탭에서 목록을 연다`() = runTest {
        MainTabCoordinator().test(this, flagTab) {
            containerHost.open(FestiCoreDeeplink.FestivalList)
            expectState { copy(selectedTab = FCTabBarTab.Live, liveRequest = LiveTabRequest.FestivalList) }
        }
    }

    @Test
    fun `공연 · 깃발 · 크루 초대 딥링크는 대상 화면이 생길 때까지 탭을 바꾸지 않는다`() = runTest {
        MainTabCoordinator().test(this, flagTab) {
            containerHost.open(FestiCoreDeeplink.Set(festivalId = "fst_a", setId = "set_1"))
            containerHost.open(FestiCoreDeeplink.Flag("flg_1"))
            containerHost.open(FestiCoreDeeplink.CrewInvite("K7P2QX"))
            expectNoItems()
        }
    }

    @Test
    fun `라이브 흐름이 깃발 탭을 요청하면 깃발 탭으로 바꾼다`() = runTest {
        MainTabCoordinator().test(this) {
            containerHost.onFlagTabRequested()
            expectState { copy(selectedTab = FCTabBarTab.Flag) }
        }
    }
}
