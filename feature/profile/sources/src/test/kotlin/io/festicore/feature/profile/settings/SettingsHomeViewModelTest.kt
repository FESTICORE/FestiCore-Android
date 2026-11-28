package io.festicore.feature.profile.settings

import io.festicore.feature.profile.AccountState
import io.festicore.feature.profile.appearance.AppearanceMode
import io.festicore.feature.profile.appearance.AppearanceModeSettings
import io.festicore.feature.web.LegalDocumentUrls
import io.festicore.feature.web.WebPage
import io.festicore.service.analytics.usecase.AnalyticsUseCase
import io.festicore.service.analytics.usecase.PreviewAnalyticsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * iOS `SettingsHomeFeatureTests` 11건 중 7건(같은 이름). 알림 2건 · 잠금화면 1건은 설계 쟁점 M4 · M5로 줄을 숨겨 S6 · 라이브 액티비티 단계에서,
 * `accountScreenRequestsAreForwardedToProfile`(계정 화면 요청 전달)은 요청 쪽을 `AccountSettingsViewModelTest`(S3)로 옮겼고 전달은 Coordinator(T1)에서 옮긴다.
 * 하위 화면의 열기 · 닫기는 Coordinator가 하므로 여기서는 요청(SideEffect)까지 본다. 주소 · 계정 값은 테스트 가짜 값이다.
 */
class SettingsHomeViewModelTest {
    private class FakeAppearanceModeSettings(initial: AppearanceMode = AppearanceMode.Dark) : AppearanceModeSettings {
        override val appearanceMode = MutableStateFlow(initial)

        override suspend fun saveAppearanceMode(mode: AppearanceMode) {
            appearanceMode.value = mode
        }
    }

    private val validUrls = LegalDocumentUrls("https://festicore.app/api/v1/legal/")
    private val signedIn = AccountState(hasSession = true)

    private fun viewModel(
        account: AccountState = AccountState(),
        urls: LegalDocumentUrls = validUrls,
        appearance: AppearanceModeSettings = FakeAppearanceModeSettings(),
    ) = SettingsHomeViewModel(initialAccount = account, legalDocumentUrls = urls, appearanceModeSettings = appearance)

    @Test
    fun analyticsTogglePersistsSelectionWithoutAccountRequest() = runTest {
        var enabled = false
        val analytics = object : AnalyticsUseCase by PreviewAnalyticsUseCase {
            override val isCollectionEnabled: Boolean get() = enabled
            override fun setCollectionEnabled(value: Boolean): Boolean {
                enabled = value
                return true
            }
        }
        SettingsHomeViewModel(AccountState(), validUrls, FakeAppearanceModeSettings(), analytics).test(this) {
            containerHost.onAnalyticsTapped()
            expectState { copy(analyticsEnabled = true) }
            containerHost.onAnalyticsTapped()
            expectState { copy(analyticsEnabled = false) }
        }
        assertFalse(enabled)
    }

    @Test
    fun guestHidesAccountGroup() = runTest {
        assertFalse(SettingsHomeState().showsAccount)
        assertTrue(SettingsHomeState(account = AccountState(hasSession = true)).showsAccount)

        viewModel().test(this) {
            // 로그아웃 상태에서는 계정 줄 입력을 무시한다.
            containerHost.onAccountTapped().join()
        }
    }

    @Test
    fun backRequestsDismiss() = runTest {
        viewModel().test(this) {
            containerHost.onBackTapped()
            expectSideEffect(SettingsHomeSideEffect.BackRequested)
        }
    }

    @Test
    fun accountTapOpensAccountScreenAndForwardsAccountChanges() = runTest {
        viewModel(account = signedIn).test(this) {
            containerHost.onAccountTapped()
            expectSideEffect(SettingsHomeSideEffect.AccountRequested)
            // 계정 화면은 같은 AccountState 입력을 받는다(T1). 설정 홈은 받은 값을 그대로 둔다.
            containerHost.onAccountChanged(signedIn.copy(isLoading = true))
            expectState { copy(account = signedIn.copy(isLoading = true)) }
        }
    }

    @Test
    fun losingSessionClosesAccountScreen() = runTest {
        viewModel(account = signedIn).test(this) {
            containerHost.onAccountChanged(AccountState())
            expectState { copy(account = AccountState()) }
            // 계정 화면이 열려 있으면 Coordinator가 닫는다.
            expectSideEffect(SettingsHomeSideEffect.SessionLost)
        }
    }

    @Test
    fun missingLegalConfigurationShowsDismissibleError() = runTest {
        viewModel(urls = LegalDocumentUrls("")).test(this) {
            containerHost.onLegalDocumentTapped(SettingsLegalDocument.Terms)
            expectState { copy(alert = SettingsHomeAlert.LegalLinkUnavailable) }
            // 이미 떠 있는 팝업에 같은 동작이 다시 와도 새로 띄우지 않는다.
            containerHost.onLegalDocumentTapped(SettingsLegalDocument.Privacy).join()
            containerHost.onAlertDismissed()
            expectState { copy(alert = null) }
        }
    }

    @Test
    fun appearanceRowOpensScreenAndBackReturnsToSettings() = runTest {
        viewModel().test(this) {
            containerHost.onAppearanceTapped()
            expectSideEffect(SettingsHomeSideEffect.AppearanceRequested)
        }
    }

    @Test
    fun noticeRowOpensListAndBackReturnsToSettings() = runTest {
        viewModel().test(this) {
            containerHost.onNoticeTapped()
            expectSideEffect(SettingsHomeSideEffect.NoticeListRequested)
        }
    }

    @Test
    fun legalRowsOpenDocumentsInAppWithRowTitles() = runTest {
        viewModel().test(this) {
            SettingsLegalDocument.entries.forEach { containerHost.onLegalDocumentTapped(it) }
            expectSideEffect(SettingsHomeSideEffect.LegalDocumentRequested(WebPage("https://festicore.app/api/v1/legal/sources", "정보 출처")))
            expectSideEffect(SettingsHomeSideEffect.LegalDocumentRequested(WebPage("https://festicore.app/api/v1/legal/terms", "이용약관")))
            expectSideEffect(
                SettingsHomeSideEffect.LegalDocumentRequested(WebPage("https://festicore.app/api/v1/legal/privacy", "개인정보 처리방침")),
            )
            expectSideEffect(
                SettingsHomeSideEffect.LegalDocumentRequested(WebPage("https://festicore.app/api/v1/legal/location", "위치기반서비스 이용약관")),
            )
        }
    }

    @Test
    fun appearanceRowFollowsStoredMode() = runTest {
        val appearance = FakeAppearanceModeSettings(AppearanceMode.Light)
        viewModel(appearance = appearance).test(this) {
            val subscription = runOnCreate()
            expectState { copy(appearanceMode = AppearanceMode.Light) }
            appearance.appearanceMode.value = AppearanceMode.System
            expectState { copy(appearanceMode = AppearanceMode.System) }
            subscription.cancel()
        }
    }
}
