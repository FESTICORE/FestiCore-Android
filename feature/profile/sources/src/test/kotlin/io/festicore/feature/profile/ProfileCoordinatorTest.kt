package io.festicore.feature.profile

import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.feature.web.WebPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * iOS `ProfileRoutingTests` 12건 중 마이 스택 · 탭 바 9건(같은 뜻의 이름을 주석에 적는다)과 Android 연결 사례.
 * 나머지 3건 중 `profileLoginDelegateReachesHome` · `browseFestivalsOpensFestivalList`는 app `MainTabCoordinatorTest`,
 * `liveBoardKeepsMainTabBar`는 `FestivalCoordinatorTest`(탭 바 숨김 사례)에 있다. 문서 주소 만들기는 `SettingsHomeViewModelTest`가 본다.
 */
class ProfileCoordinatorTest {
    private class FakeCurrentUserProvider(userId: String? = null) : CurrentUserProvider {
        override val currentUserId = MutableStateFlow(userId)
    }

    private val profile = ProfileRoute.Profile
    private val settings = ProfileRoute.Settings
    private val onSettings = ProfileCoordinatorState(routes = listOf(profile, settings))
    private val terms = WebPage(url = "https://festicore.app/api/v1/legal/terms", title = "이용약관")
    private val privacy = WebPage(url = "https://festicore.app/api/v1/legal/privacy", title = "개인정보 처리방침")
    private val sources = WebPage(url = "https://festicore.app/api/v1/legal/sources", title = "정보 출처")

    @Test
    fun `홈 위젯 안내는 설정 위에 쌓이고 뒤로 가면 설정으로 돌아간다`() = runTest {
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onSettings) {
            containerHost.onHomeWidgetRequested()
            expectState { copy(routes = listOf(profile, settings, ProfileRoute.HomeWidget)) }
            assertTrue(containerHost.container.stateFlow.value.hidesTabBar)
            containerHost.goBack()
            expectState { copy(routes = listOf(profile, settings)) }
        }
    }

    @Test
    fun notificationSettingsReturnsToSettingsAndHidesTabs() = runTest {
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onSettings) {
            containerHost.onNotificationsRequested().join()
            testScheduler.advanceUntilIdle()
            org.junit.Assert.assertEquals(
                listOf(profile, settings, ProfileRoute.Notifications),
                containerHost.container.stateFlow.value.routes,
            )
            assertTrue(containerHost.container.stateFlow.value.hidesTabBar)
            containerHost.goBack().join()
            org.junit.Assert.assertEquals(listOf(profile, settings), containerHost.container.stateFlow.value.routes)
            cancelAndIgnoreRemainingItems()
        }
    }

    /** iOS `coordinatorReducesSettingsRoutes`. */
    @Test
    fun `설정 버튼은 설정 홈을 쌓고 문서는 그 위에 쌓으며 뒤로 가면 한 단계씩 돌아온다`() = runTest {
        ProfileCoordinator(FakeCurrentUserProvider()).test(this) {
            containerHost.onSettingsRequested()
            expectState { copy(routes = listOf(profile, settings)) }
            containerHost.onLegalDocumentRequested(privacy)
            expectState { copy(routes = listOf(profile, settings, ProfileRoute.LegalDocument(privacy))) }
            containerHost.goBack()
            expectState { copy(routes = listOf(profile, settings)) }
            containerHost.onLegalDocumentRequested(sources)
            expectState { copy(routes = listOf(profile, settings, ProfileRoute.LegalDocument(sources))) }
            containerHost.goBack()
            expectState { copy(routes = listOf(profile, settings)) }
            containerHost.goBack()
            expectState { copy(routes = listOf(profile)) }
        }
    }

    /** iOS `settingsBackButtonReturnsToProfile`. 마이뿐이면 스택을 비우지 않는다. */
    @Test
    fun `설정 홈의 뒤로는 마이로 돌아오고 마이뿐이면 그대로다`() = runTest {
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onSettings) {
            containerHost.goBack()
            expectState { copy(routes = listOf(profile)) }
            containerHost.goBack()
        }
    }

    /** iOS `legalRowsOpenInAppAndBackReturnsToSettings`. 문서 닫기(`onBackToRoot`)는 설정 홈으로 돌아온다. */
    @Test
    fun `약관 문서를 차례로 열고 닫으면 매번 설정 홈으로 돌아온다`() = runTest {
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onSettings) {
            listOf(terms, privacy).forEach { page ->
                containerHost.onLegalDocumentRequested(page)
                expectState { copy(routes = listOf(profile, settings, ProfileRoute.LegalDocument(page))) }
                containerHost.goBack()
                expectState { copy(routes = listOf(profile, settings)) }
            }
        }
    }

    /** iOS `coordinatorReducesChildBackAction`. */
    @Test
    fun `설정 홈 아래 화면의 뒤로는 설정 홈으로 돌아온다`() = runTest {
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onSettings) {
            containerHost.onNoticeListRequested()
            expectState { copy(routes = listOf(profile, settings, ProfileRoute.NoticeList)) }
            containerHost.goBack()
            expectState { copy(routes = listOf(profile, settings)) }
            containerHost.onAppearanceRequested()
            expectState { copy(routes = listOf(profile, settings, ProfileRoute.Appearance)) }
            containerHost.goBack()
            expectState { copy(routes = listOf(profile, settings)) }
        }
    }

    /** iOS `legalDocumentIsPushRoute`. 문서 주소를 그대로 둔다. */
    @Test
    fun `문서는 설정 홈 위 세 번째 경로이고 주소를 그대로 둔다`() = runTest {
        val apiPage = WebPage(url = "https://festicore-dev.up.railway.app/api/v1/legal/privacy", title = "개인정보 처리방침")
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onSettings) {
            containerHost.onLegalDocumentRequested(apiPage)
            expectState { copy(routes = listOf(profile, settings, ProfileRoute.LegalDocument(apiPage))) }
            containerHost.goBackToRoot()
            expectState { copy(routes = listOf(profile)) }
        }
    }

    /** iOS `noticeAndPackDetailArePushRoutes`. 마이 위 목적지는 하나라 팩 상세를 열면 설정 스택을 바꾼다. */
    @Test
    fun `공지는 설정 홈 위에 팩 상세는 마이 위에 쌓는다`() = runTest {
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onSettings) {
            containerHost.onNoticeListRequested()
            expectState { copy(routes = listOf(profile, settings, ProfileRoute.NoticeList)) }
            containerHost.onPackDetailRequested("festival", "축제 팩")
            expectState { copy(routes = listOf(profile, ProfileRoute.PackDetail("festival", "축제 팩"))) }
        }
    }

    /** iOS `noticeDetailSwipePopReturnsToList`. */
    @Test
    fun `공지 상세는 목록 위 네 번째 경로이고 뒤로 가면 목록으로 돌아온다`() = runTest {
        val onList = ProfileCoordinatorState(routes = listOf(profile, settings, ProfileRoute.NoticeList))
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onList) {
            containerHost.onNoticeDetailRequested("notice-1", "공지")
            expectState { copy(routes = onList.routes + ProfileRoute.NoticeDetail("notice-1", "공지")) }
            // 목록이 맨 위가 아니면 쌓지 않는다.
            containerHost.onNoticeDetailRequested("notice-2", "공지 2")
            containerHost.goBack()
            expectState { copy(routes = onList.routes) }
        }
    }

    /** iOS `profileInformationRoutesHideTabBarUntilDismissed`. */
    @Test
    fun `설정 홈과 그 아래는 탭 바를 숨기고 자리도 비우며 팩 상세는 보이고 삭제 팝업이 뜨면 자리만 남긴다`() {
        val root = ProfileCoordinatorState()
        assertFalse(root.hidesTabBar)
        assertTrue(root.reservesTabBarSpace)

        assertTrue(onSettings.hidesTabBar)
        assertFalse(onSettings.reservesTabBarSpace)
        val children =
            listOf(
                ProfileRoute.Account,
                ProfileRoute.Appearance,
                ProfileRoute.NoticeList,
                ProfileRoute.LegalDocument(terms),
                ProfileRoute.LegalDocument(privacy),
            )
        children.forEach { child ->
            val state = onSettings.copy(routes = onSettings.routes + child)
            assertTrue(state.hidesTabBar)
            assertFalse(state.reservesTabBarSpace)
        }
        val onNoticeDetail = onSettings.copy(
            routes = listOf(profile, settings, ProfileRoute.NoticeList, ProfileRoute.NoticeDetail("n", "공지")),
        )
        assertTrue(onNoticeDetail.hidesTabBar)

        val onPackDetail = ProfileCoordinatorState(routes = listOf(profile, ProfileRoute.PackDetail("festival", "축제 팩")))
        assertFalse(onPackDetail.hidesTabBar)
        assertTrue(onPackDetail.reservesTabBarSpace)
        val deleting = onPackDetail.copy(isPresentingModal = true)
        assertTrue(deleting.hidesTabBar)
        assertTrue(deleting.reservesTabBarSpace)
    }

    /** iOS `MTCSc:127-134`: 보드 "가는 길"은 받은 팩 목록과 상관없이 마이 위를 그 축제 팩 상세로 바꾼다. */
    @Test
    fun `가는 길은 열린 설정 스택을 그 축제 팩 상세로 바꾼다`() = runTest {
        val onAccount = ProfileCoordinatorState(routes = listOf(profile, settings, ProfileRoute.Account))
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onAccount) {
            containerHost.onOpenPackDirections("fst_1", "축제")
            expectState { copy(routes = listOf(profile, ProfileRoute.PackDetail("fst_1", "축제"))) }
        }
    }

    /** iOS `PF:439-442`: 팩을 지우면 상세를 닫는다. 팝업 표시도 걷는다. */
    @Test
    fun `팩 삭제 팝업이 뜨면 알리고 지우면 상세를 닫는다`() = runTest {
        val onPackDetail = ProfileCoordinatorState(routes = listOf(profile, ProfileRoute.PackDetail("fst_1", "축제")))
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onPackDetail) {
            containerHost.onPresentingModalChanged(true)
            expectState { copy(isPresentingModal = true) }
            containerHost.onPackDeleted()
            expectState { copy(routes = listOf(profile), isPresentingModal = false) }
        }
    }

    /** iOS `PF:211-216`: 계정 화면에서 세션을 잃으면 설정 홈째 닫는다. 계정 화면이 없으면 스택을 그대로 둔다. */
    @Test
    fun `계정 화면이 열린 채 세션을 잃으면 마이로 돌아온다`() = runTest {
        val onAccount = ProfileCoordinatorState(routes = listOf(profile, settings, ProfileRoute.Account))
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onAccount) {
            // 처음 받은 값은 기준으로만 둔다.
            containerHost.onAccountChanged(AccountState(hasSession = false))
            containerHost.onAccountChanged(AccountState(hasSession = true))
            containerHost.onAccountChanged(AccountState(hasSession = true, isLoading = true))
            containerHost.onAccountChanged(AccountState(message = null))
            expectState { copy(routes = listOf(profile)) }
            // 화면이 늦게 보낸 같은 알림은 아무것도 바꾸지 않는다.
            containerHost.onSessionLost()
        }
    }

    @Test
    fun `계정 화면이 없으면 세션을 잃어도 스택을 그대로 둔다`() = runTest {
        ProfileCoordinator(FakeCurrentUserProvider()).test(this, initialState = onSettings) {
            containerHost.onSessionLost()
        }
    }

    /** iOS `reconcileAccountDisplayData`(`MTC:527-546`): 확인한 계정이 다른 계정으로 바뀌면 마이 위 화면을 모두 닫는다. */
    @Test
    fun `다른 계정으로 바뀌면 마이 위 화면을 모두 닫는다`() = runTest {
        val currentUser = FakeCurrentUserProvider("user-a")
        ProfileCoordinator(currentUser).test(this, initialState = onSettings.copy(routes = onSettings.routes + ProfileRoute.NoticeList)) {
            val subscription = runOnCreate()
            currentUser.currentUserId.value = "user-b"
            expectState { copy(routes = listOf(profile)) }
            subscription.cancel()
        }
    }

    @Test
    fun `비로그인에서 로그인하면 스택을 그대로 둔다`() = runTest {
        val currentUser = FakeCurrentUserProvider(null)
        ProfileCoordinator(currentUser).test(this, initialState = onSettings) {
            val subscription = runOnCreate()
            currentUser.currentUserId.value = "user-a"
            subscription.cancel()
        }
    }
}
