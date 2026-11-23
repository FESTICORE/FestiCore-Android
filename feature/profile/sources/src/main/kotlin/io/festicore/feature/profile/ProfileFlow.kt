package io.festicore.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.festicore.domain.auth.entity.AuthUser
import io.festicore.feature.profile.account.AccountSettingsScreen
import io.festicore.feature.profile.appearance.AppearanceSettingsScreen
import io.festicore.feature.profile.main.ProfileScreen
import io.festicore.feature.profile.main.ProfileViewModel
import io.festicore.feature.profile.notice.NoticeDetailScreen
import io.festicore.feature.profile.notice.NoticeListScreen
import io.festicore.feature.profile.packdetail.PackDetailScreen
import io.festicore.feature.profile.settings.SettingsHomeScreen
import io.festicore.feature.web.WebPage
import kotlinx.coroutines.flow.StateFlow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState

/**
 * 마이 탭 루트(iOS `ProfileCoordinatorView`). 마이 위에 팩 상세 · 설정 홈을, 설정 홈 위에 계정 · 화면 모드 · 문서 · 공지를 push로 쌓는다.
 * [account]는 앱이 소유한 계정 상태다(설계 쟁점 M13). 각 화면에 입력으로 넘기고 Coordinator는 세션 잃음을 본다.
 * [onHidesTabBarChanged]로 탭 바를 숨길지와 자리를 남길지 알린다(iOS `shouldHideTabBar` · `shouldReserveTabBarSpace`의 마이 부분).
 * 로그인 · 축제 목록 · 계정 작업은 상위(앱)에 콜백으로 올린다(iOS `ProfileDelegate`).
 * 약관 문서는 `feature/web` 화면이라 상위가 [legalDocument]로 그린다(가입 흐름과 같은 방식).
 * Coordinator와 마이 ViewModel은 이 흐름을 그린 범위(메인 탭 항목)에 둔다. 탭을 바꿔도 스택과 받은 팩 목록이 남고,
 * 마이 화면이 다시 보일 때마다 팩을 다시 읽는다(iOS `onAppear`).
 *
 * @param onLoginRequested 로그인 시트 요청(문맥은 상위가 탭 기본으로 정한다, iOS `loginRequested`)
 * @param onGoogleLoginRequested 로그인 카드 Google 버튼(iOS `socialLoginRequested(.google)`)
 * @param onFestivalListRequested 빈 팩 "페스티벌 둘러보기"(iOS `festivalListRequested`)
 * @param onLogoutRequested 계정 화면 로그아웃 확인(iOS `logoutRequested`)
 * @param onWithdrawRequested 계정 화면 탈퇴 확인(iOS `withdrawRequested`)
 * @param onRefreshRequested 계정 다시 확인(iOS `profileRefreshRequested`)
 * @param onNicknameUpdated 닉네임을 바꿨다(iOS `profileUpdated`)
 */
@Composable
fun ProfileFlow(
    account: StateFlow<AccountState>,
    onHidesTabBarChanged: (hides: Boolean, reservesSpace: Boolean) -> Unit,
    onLoginRequested: () -> Unit,
    onGoogleLoginRequested: () -> Unit,
    onFestivalListRequested: () -> Unit,
    onLogoutRequested: () -> Unit,
    onWithdrawRequested: () -> Unit,
    onRefreshRequested: () -> Unit,
    onNicknameUpdated: (AuthUser) -> Unit,
    legalDocument: @Composable (page: WebPage, onBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    coordinator: ProfileCoordinator = koinViewModel(),
    profileViewModel: ProfileViewModel = koinViewModel { parametersOf(account.value) },
) {
    val state by coordinator.collectAsState()
    val currentAccount by account.collectAsState()
    LaunchedEffect(currentAccount) { coordinator.onAccountChanged(currentAccount) }
    LaunchedEffect(state.hidesTabBar, state.reservesTabBarSpace) {
        onHidesTabBarChanged(state.hidesTabBar, state.reservesTabBarSpace)
    }

    NavDisplay(
        backStack = state.routes,
        modifier = modifier,
        onBack = coordinator::goBack,
        transitionSpec = { ProfileTransitions.push() },
        popTransitionSpec = { ProfileTransitions.pop() },
        predictivePopTransitionSpec = { ProfileTransitions.pop() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<ProfileRoute.Profile> {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        account = currentAccount,
                        onLoginRequested = onLoginRequested,
                        onGoogleLoginRequested = onGoogleLoginRequested,
                        onFestivalListRequested = onFestivalListRequested,
                        onSettingsRequested = coordinator::onSettingsRequested,
                        onPackDetailRequested = coordinator::onPackDetailRequested,
                        onSessionLost = coordinator::onSessionLost,
                    )
                }
                entry<ProfileRoute.PackDetail> { key ->
                    PackDetailScreen(
                        viewModel = koinViewModel { parametersOf(key.festivalId, key.title) },
                        onBackRequested = coordinator::goBack,
                        // iOS `PF:443-445`: 받거나 확인하면 목록만 다시 읽고 상세는 둔다.
                        onPackChanged = profileViewModel::onPackChanged,
                        // iOS `PF:439-442`: 지우면 상세를 닫고 목록을 다시 읽는다.
                        onPackDeleted = {
                            coordinator.onPackDeleted()
                            profileViewModel.onPackDeleted()
                        },
                        onPresentingModalChanged = coordinator::onPresentingModalChanged,
                    )
                }
                entry<ProfileRoute.Settings> {
                    SettingsHomeScreen(
                        viewModel = koinViewModel { parametersOf(currentAccount) },
                        account = currentAccount,
                        onBackRequested = coordinator::goBack,
                        onAppearanceRequested = coordinator::onAppearanceRequested,
                        onAccountRequested = coordinator::onAccountRequested,
                        onNoticeListRequested = coordinator::onNoticeListRequested,
                        onLegalDocumentRequested = coordinator::onLegalDocumentRequested,
                        onSessionLost = coordinator::onSessionLost,
                    )
                }
                entry<ProfileRoute.Account> {
                    AccountSettingsScreen(
                        viewModel = koinViewModel { parametersOf(currentAccount) },
                        account = currentAccount,
                        onBackRequested = coordinator::goBack,
                        onLogoutRequested = onLogoutRequested,
                        onWithdrawRequested = onWithdrawRequested,
                        onRefreshRequested = onRefreshRequested,
                        onNicknameUpdated = onNicknameUpdated,
                    )
                }
                entry<ProfileRoute.Appearance> {
                    AppearanceSettingsScreen(viewModel = koinViewModel(), onBackRequested = coordinator::goBack)
                }
                entry<ProfileRoute.NoticeList> {
                    NoticeListScreen(
                        viewModel = koinViewModel(),
                        onBackRequested = coordinator::goBack,
                        onNoticeDetailRequested = coordinator::onNoticeDetailRequested,
                    )
                }
                entry<ProfileRoute.NoticeDetail> { key ->
                    NoticeDetailScreen(
                        viewModel = koinViewModel { parametersOf(key.id, key.title) },
                        onBackRequested = coordinator::goBack,
                    )
                }
                entry<ProfileRoute.LegalDocument> { key -> legalDocument(key.page, coordinator::goBack) }
            },
    )
}
