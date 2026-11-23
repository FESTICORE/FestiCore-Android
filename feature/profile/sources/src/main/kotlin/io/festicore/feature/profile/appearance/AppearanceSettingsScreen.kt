package io.festicore.feature.profile.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.festicore.feature.profile.settings.SettingsRowDivider
import io.festicore.feature.profile.settings.SettingsSection
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.list.FCListRow
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** 화면 모드(iOS `AppearanceSettingsView`, Figma S13-J). 뒤로는 [onBackRequested]로 알리고 Coordinator가 닫는다(T1). */
@Composable
fun AppearanceSettingsScreen(
    viewModel: AppearanceSettingsViewModel,
    onBackRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            AppearanceSettingsSideEffect.BackRequested -> onBackRequested()
        }
    }
    AppearanceSettingsContent(
        state = state,
        modifier = modifier,
        onBackTapped = viewModel::onBackTapped,
        onModeTapped = viewModel::onModeTapped,
    )
}

/** 화면 모드(순수 UI). */
@Composable
fun AppearanceSettingsContent(
    state: AppearanceSettingsState,
    modifier: Modifier = Modifier,
    onBackTapped: () -> Unit = {},
    onModeTapped: (AppearanceMode) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        FCTopBar(style = FCTopBarStyle.Back, title = "화면 모드", onBack = onBackTapped)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(top = FestiCoreSpacing.paddingP16)
                    .fcScrollBottomInset(),
        ) {
            SettingsSection(
                title = "화면 모드",
                footnote = "시스템 설정과 같게를 고르면 기기의 라이트·다크 설정을 따라 자동으로 바뀌어요.",
            ) {
                AppearanceMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) SettingsRowDivider()
                    FCListRow(label = mode.title, isSelected = state.mode == mode, onClick = { onModeTapped(mode) })
                }
            }
        }
    }
}
