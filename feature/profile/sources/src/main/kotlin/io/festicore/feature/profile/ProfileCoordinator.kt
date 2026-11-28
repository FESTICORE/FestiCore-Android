package io.festicore.feature.profile

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.feature.web.WebPage
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * [routes]는 마이 탭 스택, [isPresentingModal]은 맨 위 팩 상세에 삭제 확인 팝업이 떠 있는지다.
 */
data class ProfileCoordinatorState(
    val routes: List<NavKey> = listOf(ProfileRoute.Profile),
    val isPresentingModal: Boolean = false,
) {
    /**
     * 경로 때문에 탭 바를 숨기는지(iOS `hidesTabBarForProfileRoute`). 설정 홈과 그 아래는 숨기고 자리도 비운다.
     * 마이 루트 · 팩 상세는 보인다(Figma Pack A~H).
     */
    val hidesTabBarForRoute: Boolean
        get() = when (routes.lastOrNull()) {
            ProfileRoute.Settings,
            ProfileRoute.BearerEntry,
            ProfileRoute.Account,
            ProfileRoute.Appearance,
            ProfileRoute.HomeWidget,
            ProfileRoute.Notifications,
            ProfileRoute.NoticeList,
            is ProfileRoute.NoticeDetail,
            is ProfileRoute.LegalDocument,
            -> true

            else -> false
        }

    /** 탭 바를 숨기는지(iOS `MainTabCoordinator.shouldHideTabBar`의 마이 부분). 팝업이 떠 있어도 숨긴다. */
    val hidesTabBar: Boolean get() = hidesTabBarForRoute || isPresentingModal

    /** 탭 바 자리를 남기는지(iOS `shouldReserveTabBarSpace`). 팝업으로만 숨기면 자리는 남겨 뒤 화면이 움직이지 않게 한다. */
    val reservesTabBarSpace: Boolean get() = !hidesTabBarForRoute
}

/**
 * 마이 탭 흐름(iOS `ProfileCoordinator` + `ProfileFeature.destination`). 첫 화면은 마이(`routes = [Profile]`)다.
 * - 마이 위에는 목적지 하나만 둔다(iOS `destination`): 팩 상세나 설정 홈. 새 목적지를 열면 마이 위를 그것으로 바꾼다.
 * - 설정 홈 위에도 목적지 하나만 둔다(iOS 설정 홈 `destination`): 계정 · 화면 모드 · 문서 · 공지 목록. 공지 상세는 공지 목록 위에만 쌓는다.
 * - 뒤로(상단 뒤로 · 시스템 뒤로)는 한 단계 pop이다(iOS `handleUpdatedRoutes`).
 * - 계정 화면에서 세션을 잃으면 설정 홈째 닫는다(iOS `PF:211-216`). 확인한 계정이 다른 계정으로 바뀌면 마이 위를 모두 닫는다(iOS `clearAccountDisplayData`).
 * 같은 탭을 다시 눌러도 루트로 pop하지 않는다(설계 "탭 바 규칙").
 */
class ProfileCoordinator(currentUserProvider: CurrentUserProvider) :
    ViewModel(),
    OrbitContainerHost<ProfileCoordinatorState, ProfileCoordinatorState, Nothing> {
    /** 이 흐름을 만든 계정. 여기서 바뀔 때만 정리한다(구독 시작이 늦어도 놓치지 않게 만들 때 읽는다). */
    private var lastUserId = currentUserProvider.currentUserId.value

    /** 마지막으로 받은 계정 상태의 세션 여부. 처음 받은 값은 기준으로만 둔다. */
    private var lastHasSession: Boolean? = null

    override val container =
        orbitContainer<ProfileCoordinatorState, Nothing>(ProfileCoordinatorState()) {
            currentUserProvider.currentUserId.collect { userId ->
                if (userId == lastUserId) return@collect
                val previous = lastUserId
                lastUserId = userId
                // iOS `reconcileAccountDisplayData`: 이전 계정이 있었고 다른 계정(또는 비로그인)으로 바뀌면 비운다.
                if (previous != null) goBackToRoot()
            }
        }

    fun goBack() = intent {
        reduce { state.copy(routes = state.routes.dropLast(1).ifEmpty { state.routes }, isPresentingModal = false) }
    }

    fun goBackToRoot() = intent {
        reduce { state.copy(routes = state.routes.take(1), isPresentingModal = false) }
    }

    // 마이

    /** 받은 팩 행(iOS `packTapped`). */
    fun onPackDetailRequested(festivalId: String, title: String) = openOnProfile(ProfileRoute.PackDetail(festivalId, title))

    /** 마이 화면에서 app 소유 기수 흐름을 스택에 push한다. */
    fun onBearerEntryRequested() = openOnProfile(ProfileRoute.BearerEntry)

    /** 라이브 보드 "가는 길"(iOS `openPackDirections`). 받은 팩 목록과 상관없이 그 축제의 팩 상세를 연다. */
    fun onOpenPackDirections(festivalId: String, title: String) = onPackDetailRequested(festivalId, title)

    /** 설정 버튼(iOS `settingsTapped`). */
    fun onSettingsRequested() = openOnProfile(ProfileRoute.Settings)

    /**
     * 세션을 잃었다(iOS `accountChanged`의 `lostSession`). 계정 화면이 열려 있으면 설정 홈째 닫고 마이로 돌아온다.
     * 마이 · 설정 홈 화면도 같은 알림을 보내므로 여러 번 불러도 결과가 같다.
     */
    fun onSessionLost() = intent {
        if (ProfileRoute.Account !in state.routes) return@intent
        reduce { state.copy(routes = state.routes.take(1), isPresentingModal = false) }
    }

    /** 앱이 알린 계정 상태. 세션이 있다가 없어지면 [onSessionLost]와 같다. 처음 받은 값은 기준으로만 둔다. */
    fun onAccountChanged(account: AccountState) {
        val lostSession = lastHasSession == true && !account.hasSession
        lastHasSession = account.hasSession
        if (lostSession) onSessionLost()
    }

    // 팩 상세

    /** 팩을 지웠다(iOS `packDeleted`). 상세를 닫는다. 목록 다시 읽기는 마이 화면이 한다. */
    fun onPackDeleted() = intent {
        reduce { state.copy(routes = state.routes.filterNot { it is ProfileRoute.PackDetail }, isPresentingModal = false) }
    }

    /** 팩 상세 삭제 확인 팝업 표시가 바뀌었다(탭 바 숨김, iOS `isProfileOverlayPresented`). */
    fun onPresentingModalChanged(isPresenting: Boolean) = intent {
        reduce { state.copy(isPresentingModal = isPresenting) }
    }

    // 설정 홈

    fun onAccountRequested() = openOnSettings(ProfileRoute.Account)

    fun onNotificationsRequested() = openOnSettings(ProfileRoute.Notifications)

    fun onHomeWidgetRequested() = openOnSettings(ProfileRoute.HomeWidget)

    fun onAppearanceRequested() = openOnSettings(ProfileRoute.Appearance)

    fun onNoticeListRequested() = openOnSettings(ProfileRoute.NoticeList)

    fun onLegalDocumentRequested(page: WebPage) = openOnSettings(ProfileRoute.LegalDocument(page))

    // 공지

    /** 공지 행(iOS 공지 목록 `destination = .detail`). 공지 목록이 맨 위일 때만 쌓는다. */
    fun onNoticeDetailRequested(id: String, title: String) = intent {
        if (state.routes.lastOrNull() != ProfileRoute.NoticeList) return@intent
        reduce { state.copy(routes = state.routes + ProfileRoute.NoticeDetail(id, title)) }
    }

    /** 마이 위 목적지를 [route] 하나로 둔다. */
    private fun openOnProfile(route: ProfileRoute) = intent {
        reduce { state.copy(routes = state.routes.take(1) + route, isPresentingModal = false) }
    }

    /** 설정 홈 위 목적지를 [route] 하나로 둔다. 설정 홈이 없으면 무시한다. */
    private fun openOnSettings(route: ProfileRoute) = intent {
        val settingsIndex = state.routes.indexOf(ProfileRoute.Settings)
        if (settingsIndex < 0) return@intent
        reduce { state.copy(routes = state.routes.take(settingsIndex + 1) + route) }
    }
}
