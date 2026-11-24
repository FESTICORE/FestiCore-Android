package io.festicore.app.navigation.maintab

import androidx.lifecycle.ViewModel
import io.festicore.core.logger.FestiCoreLogCategory
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.core.utility.deeplink.FestiCoreDeeplink
import io.festicore.feature.auth.LoginContext
import io.festicore.feature.festival.FestivalLoginContext
import io.festicore.feature.festival.FlagLoginContext
import io.festicore.ui.designkit.component.navigation.FCTabBarTab
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** 라이브 흐름에 맡길 딥링크 열기. 라이브 흐름이 받아 처리하면 비운다. */
sealed interface LiveTabRequest {
    data object FestivalList : LiveTabRequest

    data class Festival(val festivalId: String) : LiveTabRequest

    /** 크루 초대 링크(iOS `openCrewInvite(code)`). */
    data class CrewInvite(val code: String) : LiveTabRequest
}

/** 마이 흐름에 맡길 팩 상세 열기(라이브 보드 "가는 길", iOS `openPackDirections(festivalID:title:)`). */
data class PackDirectionsRequest(
    val festivalId: String,
    val title: String,
)

/**
 * [selectedTab]은 메인 탭에서 고른 탭, [liveRequest]는 라이브 흐름이 아직 받지 않은 딥링크 열기다.
 * [flagRequest]는 깃발 흐름이 아직 받지 않은 깃발 링크의 깃발 ID다(iOS `showDetail(flagID:)`).
 * [packDirectionsRequest]는 마이 흐름이 아직 받지 않은 팩 상세 열기다.
 */
data class MainTabCoordinatorState(
    val selectedTab: FCTabBarTab = FCTabBarTab.Live,
    val liveRequest: LiveTabRequest? = null,
    val flagRequest: String? = null,
    val packDirectionsRequest: PackDirectionsRequest? = null,
) {
    /**
     * 로그인 시트 문맥. 요청에 문맥이 없으면 고른 탭으로 정한다(iOS `MainTabAuthInputs.swift:48-58`).
     * 마이 = 프로필, 깃발 = 깃발, 라이브 · 타임테이블 = 축제 저장.
     */
    fun loginContext(requested: LoginContext?): LoginContext = requested ?: when (selectedTab) {
        FCTabBarTab.My -> LoginContext.Profile
        FCTabBarTab.Flag -> LoginContext.Flag
        FCTabBarTab.Live, FCTabBarTab.Timetable -> LoginContext.Bookmark
    }

    /** 깃발 탭 요청(iOS `MTCSc:185-192`). 목록은 문맥 없이(깃발 탭 기본), 상세는 팔로우 · 신고 문맥이다. */
    fun flagLoginContext(requested: FlagLoginContext?): LoginContext = loginContext(
        when (requested) {
            null -> null
            FlagLoginContext.Follow -> LoginContext.Follow
            FlagLoginContext.Report -> LoginContext.Report
        },
    )

    /** 라이브 탭 요청(iOS `MTCSc:83-106`). 보드 알림 아이콘은 문맥 없이, 크루 · 참여 · 팔로우는 그 문맥이다. 참여 문맥은 크루 이름을 그대로 넘긴다. */
    fun liveLoginContext(requested: FestivalLoginContext?): LoginContext = loginContext(
        when (requested) {
            null -> null
            FestivalLoginContext.Crew -> LoginContext.Crew
            is FestivalLoginContext.CrewJoin -> LoginContext.CrewJoin(requested.crewName)
            FestivalLoginContext.Follow -> LoginContext.Follow
        },
    )
}

/**
 * 메인 탭 선택과 딥링크 탭 분기(iOS `MainTabCoordinator` `select` · `handleDeeplink`, 설계 P7).
 * 축제 목록 · 축제 · 크루 초대 경로는 라이브 탭에서, 깃발 경로는 깃발 탭에서 연다(iOS `MTC:379-394`).
 * 공연(`Set`, 공연 10분 전 푸시)은 푸시 · 기기 등록(P0)에서 붙인다. 그때까지는 꺼낸 뒤 디버그 로그만 남긴다.
 */
class MainTabCoordinator :
    ViewModel(),
    OrbitContainerHost<MainTabCoordinatorState, MainTabCoordinatorState, Nothing> {
    override val container = orbitContainer<MainTabCoordinatorState, Nothing>(MainTabCoordinatorState())

    fun onTabSelected(tab: FCTabBarTab) = intent { reduce { state.copy(selectedTab = tab) } }

    /** 라이브 흐름이 깃발 탭을 요청했다(iOS `flagTabRequested`). */
    fun onFlagTabRequested() = onTabSelected(FCTabBarTab.Flag)

    /** 라이브 보드 "가는 길"(iOS `packDirectionsRequested`): 마이 탭을 고르고 마이 흐름에 그 축제의 팩 상세를 넘긴다(iOS `MTCSc:127-134`). */
    fun onPackDirectionsRequested(festivalId: String, title: String) = intent {
        reduce {
            state.copy(selectedTab = FCTabBarTab.My, packDirectionsRequest = PackDirectionsRequest(festivalId, title))
        }
    }

    /** 마이 빈 팩 "페스티벌 둘러보기"(iOS `festivalListRequested`): 축제 목록 딥링크와 같다(iOS `MTCSc:245-249`). */
    fun onFestivalListRequested() = open(FestiCoreDeeplink.FestivalList)

    fun open(deeplink: FestiCoreDeeplink) = intent {
        when (deeplink) {
            FestiCoreDeeplink.FestivalList ->
                reduce { state.copy(selectedTab = FCTabBarTab.Live, liveRequest = LiveTabRequest.FestivalList) }

            is FestiCoreDeeplink.Festival ->
                reduce { state.copy(selectedTab = FCTabBarTab.Live, liveRequest = LiveTabRequest.Festival(deeplink.id)) }

            // 라우팅 보류: P0에서 축제 열기 → 타임테이블 탭 → 공연 상세로 잇는다(iOS `MainTabCoordinator.swift:371-377`).
            // 지금은 타임테이블 탭만 골라도 라이브 탭이 그려지지 않아 보드가 축제를 열지 못한다.
            is FestiCoreDeeplink.Set -> FestiCoreLogger.debug("공연 딥링크는 푸시(P0) 전이라 열지 않음", FestiCoreLogCategory.NAVIGATION)

            is FestiCoreDeeplink.Flag ->
                reduce { state.copy(selectedTab = FCTabBarTab.Flag, flagRequest = deeplink.id) }

            is FestiCoreDeeplink.CrewInvite ->
                reduce { state.copy(selectedTab = FCTabBarTab.Live, liveRequest = LiveTabRequest.CrewInvite(deeplink.code)) }
        }
    }

    /** 깃발 흐름이 [MainTabCoordinatorState.flagRequest]를 받았다. */
    fun onFlagRequestConsumed() = intent { reduce { state.copy(flagRequest = null) } }

    /** 마이 흐름이 [MainTabCoordinatorState.packDirectionsRequest]를 받았다. */
    fun onPackDirectionsRequestConsumed() = intent { reduce { state.copy(packDirectionsRequest = null) } }

    /** 라이브 흐름이 [MainTabCoordinatorState.liveRequest]를 받았다. */
    fun onLiveRequestConsumed() = intent { reduce { state.copy(liveRequest = null) } }
}
