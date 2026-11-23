package io.festicore.app.navigation

import androidx.lifecycle.ViewModel
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.core.logger.LogCategory
import io.festicore.core.utility.deeplink.FestiCoreDeeplink
import io.festicore.ui.designkit.component.navigation.FCTabBarTab
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** 라이브 흐름에 맡길 딥링크 열기. 라이브 흐름이 받아 처리하면 비운다. */
sealed interface LiveTabRequest {
    data object FestivalList : LiveTabRequest

    data class Festival(val festivalId: String) : LiveTabRequest
}

/** [selectedTab]은 메인 탭에서 고른 탭, [liveRequest]는 라이브 흐름이 아직 받지 않은 딥링크 열기다. */
data class MainTabCoordinatorState(
    val selectedTab: FCTabBarTab = FCTabBarTab.Live,
    val liveRequest: LiveTabRequest? = null,
)

/**
 * 메인 탭 선택과 딥링크 탭 분기(iOS `MainTabCoordinator` `select` · `handleDeeplink`, 설계 P7).
 * 축제 목록 · 축제 경로는 라이브 탭에서 연다. 공연(`Set`)은 타임테이블 탭이 연결되는 C1에서, 깃발 · 크루 초대는 해당 기능을 옮길 때 붙인다.
 * 그때까지는 꺼낸 뒤 디버그 로그만 남긴다.
 */
class MainTabCoordinator :
    ViewModel(),
    OrbitContainerHost<MainTabCoordinatorState, MainTabCoordinatorState, Nothing> {
    override val container = orbitContainer<MainTabCoordinatorState, Nothing>(MainTabCoordinatorState())

    fun onTabSelected(tab: FCTabBarTab) = intent { reduce { state.copy(selectedTab = tab) } }

    /** 라이브 흐름이 깃발 탭을 요청했다(iOS `flagTabRequested`). */
    fun onFlagTabRequested() = onTabSelected(FCTabBarTab.Flag)

    fun open(deeplink: FestiCoreDeeplink) = intent {
        when (deeplink) {
            FestiCoreDeeplink.FestivalList ->
                reduce { state.copy(selectedTab = FCTabBarTab.Live, liveRequest = LiveTabRequest.FestivalList) }

            is FestiCoreDeeplink.Festival ->
                reduce { state.copy(selectedTab = FCTabBarTab.Live, liveRequest = LiveTabRequest.Festival(deeplink.id)) }

            // 라우팅 보류: C1에서 축제 열기 → 타임테이블 탭 → 공연 상세로 잇는다(iOS `MainTabCoordinator.swift:371-377`).
            is FestiCoreDeeplink.Set -> FestiCoreLogger.debug("공연 딥링크는 타임테이블 연결(C1) 전이라 열지 않음", LogCategory.NAVIGATION)

            is FestiCoreDeeplink.Flag -> FestiCoreLogger.debug("깃발 딥링크는 깃발 탭 이식 전이라 열지 않음", LogCategory.NAVIGATION)

            is FestiCoreDeeplink.CrewInvite -> FestiCoreLogger.debug("크루 초대 딥링크는 크루 이식 전이라 열지 않음", LogCategory.NAVIGATION)
        }
    }

    /** 라이브 흐름이 [MainTabCoordinatorState.liveRequest]를 받았다. */
    fun onLiveRequestConsumed() = intent { reduce { state.copy(liveRequest = null) } }
}
