package io.festicore.feature.profile.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import io.festicore.feature.profile.AccountState
import io.festicore.feature.web.WebPage
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.list.FCListRow
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * 설정 홈(iOS `SettingsHomeView`, Figma S13-0). ViewModel을 잇고 [account]가 바뀌면 ViewModel에 알린다.
 * 이동은 콜백으로만 알리고 Coordinator가 정한다(T1).
 */
@Composable
fun SettingsHomeScreen(
    viewModel: SettingsHomeViewModel,
    account: AccountState,
    onBackRequested: () -> Unit,
    onAppearanceRequested: () -> Unit,
    onHomeWidgetRequested: () -> Unit,
    onNotificationsRequested: () -> Unit = {},
    onAccountRequested: () -> Unit,
    onNoticeListRequested: () -> Unit,
    onLegalDocumentRequested: (WebPage) -> Unit,
    onSessionLost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            SettingsHomeSideEffect.BackRequested -> onBackRequested()
            SettingsHomeSideEffect.AppearanceRequested -> onAppearanceRequested()
            SettingsHomeSideEffect.NotificationsRequested -> onNotificationsRequested()
            SettingsHomeSideEffect.HomeWidgetRequested -> onHomeWidgetRequested()
            SettingsHomeSideEffect.AccountRequested -> onAccountRequested()
            SettingsHomeSideEffect.NoticeListRequested -> onNoticeListRequested()
            is SettingsHomeSideEffect.LegalDocumentRequested -> onLegalDocumentRequested(sideEffect.page)
            SettingsHomeSideEffect.SessionLost -> onSessionLost()
        }
    }
    LaunchedEffect(viewModel, account) { viewModel.onAccountChanged(account) }
    val context = LocalContext.current
    // iOS `CFBundleShortVersionString`. Android는 설치된 앱의 versionName을 읽는다.
    val appVersion =
        remember(context) {
            runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull()
        }
    SettingsHomeContent(
        state = state,
        appVersion = appVersion,
        modifier = modifier,
        onBackTapped = viewModel::onBackTapped,
        onAppearanceTapped = viewModel::onAppearanceTapped,
        onHomeWidgetTapped = viewModel::onHomeWidgetTapped,
        onNotificationsTapped = viewModel::onNotificationsTapped,
        onAccountTapped = viewModel::onAccountTapped,
        onNoticeTapped = viewModel::onNoticeTapped,
        onLegalDocumentTapped = viewModel::onLegalDocumentTapped,
    )
    state.alert?.let { alert ->
        FCDialog(
            // FCDialog는 버튼 두 개를 그린다. 둘 다 팝업만 닫는다(iOS 확인 하나).
            item = FCDialogItem(title = alert.title, message = alert.message, cancelTitle = null, onConfirm = {}),
            onDismiss = viewModel::onAlertDismissed,
        )
    }
}

/**
 * 설정 홈(순수 UI). 앱(화면 모드 · 홈 화면 위젯) · 계정(로그인했을 때만) · 정보 그룹과 버전.
 * 알림은 서버 설정 편집을 제공한다. Android에 대응하지 않는 잠금화면 줄은 숨긴다.
 *
 * @param appVersion 앱 버전 이름. 없으면 버전 줄을 숨긴다.
 */
@Composable
fun SettingsHomeContent(
    state: SettingsHomeState,
    appVersion: String?,
    modifier: Modifier = Modifier,
    onBackTapped: () -> Unit = {},
    onAppearanceTapped: () -> Unit = {},
    onHomeWidgetTapped: () -> Unit = {},
    onNotificationsTapped: () -> Unit = {},
    onAccountTapped: () -> Unit = {},
    onNoticeTapped: () -> Unit = {},
    onLegalDocumentTapped: (SettingsLegalDocument) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        FCTopBar(style = FCTopBarStyle.Back, title = "설정", onBack = onBackTapped)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(top = FestiCoreSpacing.paddingP16)
                    .fcScrollBottomInset(),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
        ) {
            SettingsSection(title = "앱") {
                FCListRow(label = "알림", onClick = onNotificationsTapped)
                SettingsRowDivider()
                FCListRow(label = "화면 모드", value = state.appearanceMode.summary, onClick = onAppearanceTapped)
                SettingsRowDivider()
                FCListRow(label = "홈 화면 위젯", onClick = onHomeWidgetTapped)
            }
            if (state.showsAccount) {
                SettingsSection(title = "계정") {
                    FCListRow(label = "계정", value = state.account.profile?.user?.nickname, onClick = onAccountTapped)
                }
            }
            SettingsSection(title = "정보") {
                FCListRow(label = "공지사항", onClick = onNoticeTapped)
                SettingsLegalDocument.entries.forEach { document ->
                    SettingsRowDivider()
                    FCListRow(label = document.title, onClick = { onLegalDocumentTapped(document) })
                }
            }
            appVersion?.let { version ->
                Text(
                    text = "FESTICORE v$version",
                    style = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox,
                    color = FestiCoreSemanticColor.labelAssistive.color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
