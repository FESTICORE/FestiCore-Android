package io.festicore.feature.profile.settings

import androidx.lifecycle.ViewModel
import io.festicore.feature.profile.AccountState
import io.festicore.feature.profile.appearance.AppearanceMode
import io.festicore.feature.profile.appearance.AppearanceModeSettings
import io.festicore.feature.web.LegalDocumentUrls
import io.festicore.feature.web.WebPage
import io.festicore.service.analytics.usecase.AnalyticsUseCase
import io.festicore.service.analytics.usecase.PreviewAnalyticsUseCase
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * 설정 홈(Figma S13-0). iOS `SettingsHomeFeature`(알림 · 잠금화면 줄은 설계 쟁점 M4 · M5로 숨김, 알림 저장 보내기는 S6).
 * 하위 화면은 SideEffect로 요청하고 Coordinator가 연다. 계정 화면의 요청(로그아웃 · 탈퇴 등)은 Coordinator가 앱으로 넘긴다(S3 · T1).
 *
 * @param initialAccount 화면을 만들 때의 계정 상태.
 */
class SettingsHomeViewModel(
    initialAccount: AccountState,
    private val legalDocumentUrls: LegalDocumentUrls,
    private val appearanceModeSettings: AppearanceModeSettings,
    private val analytics: AnalyticsUseCase = PreviewAnalyticsUseCase,
) : ViewModel(),
    OrbitContainerHost<SettingsHomeState, SettingsHomeState, SettingsHomeSideEffect> {
    override val container =
        orbitContainer<SettingsHomeState, SettingsHomeSideEffect>(
            SettingsHomeState(account = initialAccount, analyticsEnabled = analytics.isCollectionEnabled),
        ) {
            appearanceModeSettings.appearanceMode.collect { onAppearanceModeChanged(it) }
        }

    // view

    /** 앱 코디네이터가 계정 상태가 바뀔 때 보내는 입력. */
    fun onAccountChanged(account: AccountState) = intent {
        val lostSession = state.account.hasSession && !account.hasSession
        reduce { state.copy(account = account) }
        if (lostSession) postSideEffect(SettingsHomeSideEffect.SessionLost)
    }

    fun onAnalyticsTapped() = intent {
        val saved = analytics.setCollectionEnabled(!state.analyticsEnabled)
        reduce {
            state.copy(analyticsEnabled = analytics.isCollectionEnabled, alert = if (saved) null else SettingsHomeAlert.AnalyticsSaveFailed)
        }
    }

    fun onBackTapped() = intent { postSideEffect(SettingsHomeSideEffect.BackRequested) }

    fun onNotificationsTapped() = intent { postSideEffect(SettingsHomeSideEffect.NotificationsRequested) }

    fun onHomeWidgetTapped() = intent { postSideEffect(SettingsHomeSideEffect.HomeWidgetRequested) }

    fun onAppearanceTapped() = intent { postSideEffect(SettingsHomeSideEffect.AppearanceRequested) }

    fun onAccountTapped() = intent {
        if (!state.showsAccount) return@intent
        postSideEffect(SettingsHomeSideEffect.AccountRequested)
    }

    fun onNoticeTapped() = intent { postSideEffect(SettingsHomeSideEffect.NoticeListRequested) }

    /** '정보 출처' · '이용약관' · '개인정보 처리방침' · '위치기반서비스 이용약관'. iOS `sourcesTapped` · `termsTapped` · `privacyTapped` · `locationTermsTapped`. */
    fun onLegalDocumentTapped(document: SettingsLegalDocument) = intent {
        val url = legalDocumentUrls.url(document.path)
        if (url == null) {
            // 이미 떠 있으면 다시 띄우지 않는다.
            if (state.alert == null) reduce { state.copy(alert = SettingsHomeAlert.LegalLinkUnavailable) }
            return@intent
        }
        postSideEffect(SettingsHomeSideEffect.LegalDocumentRequested(WebPage(url = url, title = document.title)))
    }

    /** 팝업의 확인 · 딤 · 뒤로. */
    fun onAlertDismissed() = intent {
        if (state.alert != null) reduce { state.copy(alert = null) }
    }

    // inner

    private suspend fun onAppearanceModeChanged(mode: AppearanceMode) = subIntent {
        if (state.appearanceMode != mode) reduce { state.copy(appearanceMode = mode) }
    }
}
