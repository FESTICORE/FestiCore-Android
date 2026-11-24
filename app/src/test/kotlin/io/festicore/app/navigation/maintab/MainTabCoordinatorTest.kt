package io.festicore.app.navigation.maintab

import io.festicore.core.utility.deeplink.FestiCoreDeeplink
import io.festicore.feature.auth.LoginContext
import io.festicore.feature.festival.FestivalLoginContext
import io.festicore.feature.festival.FlagLoginContext
import io.festicore.ui.designkit.component.navigation.FCTabBarTab
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `DeeplinkRoutingTests` · `FlagRoutingTests` 중 Android에 있는 경로(축제 목록 · 축제 · 깃발 · 크루 초대)와 아직 열지 않는 경로. */
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
    fun `공연 딥링크는 타임테이블 연결 전까지 탭을 바꾸지 않는다`() = runTest {
        MainTabCoordinator().test(this, flagTab) {
            containerHost.open(FestiCoreDeeplink.Set(festivalId = "fst_a", setId = "set_1"))
            expectNoItems()
        }
    }

    /** iOS `flagDeeplinkOpensDetail`: 깃발 링크는 깃발 탭을 고르고 깃발 흐름에 상세 열기를 넘긴다. */
    @Test
    fun flagDeeplinkOpensDetail() = runTest {
        MainTabCoordinator().test(this) {
            containerHost.open(FestiCoreDeeplink.Flag("flg_9"))
            expectState { copy(selectedTab = FCTabBarTab.Flag, flagRequest = "flg_9") }

            containerHost.onFlagRequestConsumed()
            expectState { copy(flagRequest = null) }
        }
    }

    /** iOS `MTC:379-384`: 크루 초대 링크는 라이브 탭을 고르고 라이브 흐름에 참여 확인 열기를 넘긴다. */
    @Test
    fun `크루 초대 딥링크는 라이브 탭으로 바꾸고 라이브 흐름에 초대 코드를 넘긴다`() = runTest {
        MainTabCoordinator().test(this, flagTab) {
            containerHost.open(FestiCoreDeeplink.CrewInvite("K7P2QX"))
            expectState { copy(selectedTab = FCTabBarTab.Live, liveRequest = LiveTabRequest.CrewInvite("K7P2QX")) }

            containerHost.onLiveRequestConsumed()
            expectState { copy(liveRequest = null) }
        }
    }

    @Test
    fun `라이브 흐름이 깃발 탭을 요청하면 깃발 탭으로 바꾼다`() = runTest {
        MainTabCoordinator().test(this) {
            containerHost.onFlagTabRequested()
            expectState { copy(selectedTab = FCTabBarTab.Flag) }
        }
    }

    /** iOS `browseFestivalsOpensFestivalList`: 마이 빈 팩 "페스티벌 둘러보기"는 라이브 탭으로 바꿔 축제 목록만 남긴다. */
    @Test
    fun `마이의 축제 목록 요청은 라이브 탭에서 목록을 연다`() = runTest {
        MainTabCoordinator().test(this, MainTabCoordinatorState(selectedTab = FCTabBarTab.My)) {
            containerHost.onFestivalListRequested()
            expectState { copy(selectedTab = FCTabBarTab.Live, liveRequest = LiveTabRequest.FestivalList) }
        }
    }

    /** iOS `MTCSc:127-134` `packDirectionsRequested`: 보드 "가는 길"은 마이 탭을 고르고 마이 흐름에 팩 상세를 넘긴다. */
    @Test
    fun `라이브 보드 가는 길은 마이 탭으로 바꾸고 마이 흐름에 팩 상세 열기를 넘긴다`() = runTest {
        MainTabCoordinator().test(this) {
            containerHost.onPackDirectionsRequested("fst_a", "축제")
            expectState { copy(selectedTab = FCTabBarTab.My, packDirectionsRequest = PackDirectionsRequest("fst_a", "축제")) }

            containerHost.onPackDirectionsRequestConsumed()
            expectState { copy(packDirectionsRequest = null) }
        }
    }

    /** iOS `profileLoginDelegateReachesHome`: 마이 로그인 요청은 문맥 없이 올라가 마이 탭 기본(프로필) 시트를 연다. */
    @Test
    fun `마이 로그인 요청은 프로필 문맥 시트다`() {
        assertEquals(LoginContext.Profile, MainTabCoordinatorState(selectedTab = FCTabBarTab.My).loginContext(null))
    }

    @Test
    fun `로그인 시트 문맥이 없으면 고른 탭으로 정하고 있으면 그대로 쓴다`() {
        val expected =
            mapOf(
                FCTabBarTab.My to LoginContext.Profile,
                FCTabBarTab.Flag to LoginContext.Flag,
                FCTabBarTab.Live to LoginContext.Bookmark,
                FCTabBarTab.Timetable to LoginContext.Bookmark,
            )
        for ((tab, context) in expected) {
            val state = MainTabCoordinatorState(selectedTab = tab)
            assertEquals(context, state.loginContext(null))
            assertEquals(LoginContext.Report, state.loginContext(LoginContext.Report))
        }
    }

    /** iOS `detailRequestsReachAppSheets`: 깃발 목록은 깃발 탭 기본, 상세 팔로우 · 신고는 그 문맥으로 시트를 연다. */
    @Test
    fun detailRequestsReachAppSheets() {
        assertEquals(LoginContext.Flag, flagTab.flagLoginContext(null))
        assertEquals(LoginContext.Follow, flagTab.flagLoginContext(FlagLoginContext.Follow))
        assertEquals(LoginContext.Report, flagTab.flagLoginContext(FlagLoginContext.Report))
    }

    /** iOS `MTCSc:83-106` · `crewJoinLoginRequestUsesJoinContext`: 라이브 흐름 요청은 문맥을 잃지 않고 바꾸며, 문맥이 없으면 라이브 탭 기본(축제 저장)이다. */
    @Test
    fun `라이브 흐름 로그인 요청은 크루 · 참여 · 팔로우 문맥을 그대로 옮긴다`() {
        val liveTab = MainTabCoordinatorState(selectedTab = FCTabBarTab.Live)
        assertEquals(LoginContext.Bookmark, liveTab.liveLoginContext(null))
        assertEquals(LoginContext.Crew, liveTab.liveLoginContext(FestivalLoginContext.Crew))
        assertEquals(LoginContext.CrewJoin("주말 크루"), liveTab.liveLoginContext(FestivalLoginContext.CrewJoin("주말 크루")))
        assertEquals(LoginContext.CrewJoin(null), liveTab.liveLoginContext(FestivalLoginContext.CrewJoin(null)))
        assertEquals(LoginContext.Follow, liveTab.liveLoginContext(FestivalLoginContext.Follow))
    }
}
