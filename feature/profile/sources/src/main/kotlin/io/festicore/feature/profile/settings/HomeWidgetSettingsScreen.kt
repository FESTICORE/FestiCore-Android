package io.festicore.feature.profile.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** 런처 작업은 app 콜백으로 넘긴다. iOS scenePhase active처럼 실제 ON_RESUME에서 설치 상태를 재조회한다. */
@Composable
fun HomeWidgetSettingsScreen(
    viewModel: HomeWidgetSettingsViewModel,
    readStatus: () -> HomeWidgetStatus,
    requestPin: () -> Boolean,
    onBackRequested: () -> Unit,
    modifier: Modifier = Modifier,
    installationChanges: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
) {
    val state by viewModel.collectAsState()
    val currentReadStatus by rememberUpdatedState(readStatus)
    androidx.compose.runtime.LaunchedEffect(viewModel, installationChanges) {
        installationChanges.collect { viewModel.onStatusChanged(currentReadStatus()) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onStatusChanged(currentReadStatus())
        }
        lifecycle.addObserver(observer)
        // STARTED 상태로 들어온 화면도 설치 상태를 즉시 표시한다.
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.onStatusChanged(currentReadStatus())
        onDispose { lifecycle.removeObserver(observer) }
    }
    viewModel.collectSideEffect {
        when (it) {
            HomeWidgetSettingsSideEffect.BackRequested -> onBackRequested()
            HomeWidgetSettingsSideEffect.PinRequested -> viewModel.onPinResult(requestPin())
        }
    }
    HomeWidgetSettingsContent(state, modifier, viewModel::onBackTapped, viewModel::onAddTapped)
}

/** S13-C의 상단바·그룹·각주 구조를 재사용하되 Android에서 지원하는 홈 위젯만 설명한다. */
@Composable
fun HomeWidgetSettingsContent(
    state: HomeWidgetSettingsState,
    modifier: Modifier = Modifier,
    onBackTapped: () -> Unit = {},
    onAddTapped: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        FCTopBar(style = FCTopBarStyle.Back, title = "홈 화면 위젯", onBack = onBackTapped)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = FestiCoreSpacing.paddingP20, vertical = FestiCoreSpacing.paddingP20)
                .fcScrollBottomInset(),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
        ) {
            SettingsSection(
                title = "홈 위젯",
                footnote = "앱에서 선택한 페스티벌의 지금 무대와 다음 내 일정을 홈 화면에서 볼 수 있어요.",
            ) {
                Text(
                    text = when (state.status?.isInstalled) {
                        true -> "홈 화면에 위젯을 추가했어요"
                        false -> "아직 위젯을 추가하지 않았어요"
                        null -> "위젯 상태를 확인하고 있어요"
                    },
                    style = FestiCoreTextStyle.baseBodyRegular.textStyle,
                    color = FestiCoreSemanticColor.labelNormal.color,
                    modifier = Modifier.padding(FestiCoreSpacing.paddingP16),
                )
            }
            val status = state.status
            if (status != null) {
                Text(
                    text = if (status.isInstalled) {
                        "위젯 크기와 위치를 바꾸거나 제거하려면 홈 화면의 위젯을 길게 눌러 주세요."
                    } else if (!status.isPinSupported) {
                        "홈 화면의 빈 곳을 길게 누른 뒤 위젯 목록에서 FESTICORE를 찾아 추가해 주세요."
                    } else {
                        "아래 버튼을 누른 뒤 홈 화면에서 위젯 추가를 완료해 주세요."
                    },
                    style = FestiCoreTextStyle.baseBodyRegular.textStyle,
                    color = FestiCoreSemanticColor.labelNeutral.color,
                )
                if (status.isPinSupported && !status.isInstalled) {
                    FCButton(
                        title = "홈 화면에 추가",
                        style = FCButtonStyle.Secondary,
                        size = FCButtonSize.Medium,
                        isEnabled = !state.isRequesting,
                        isFullWidth = true,
                        onClick = onAddTapped,
                    )
                }
            }
            state.pinRequestAccepted?.let { accepted ->
                Text(
                    text = if (accepted) "추가 요청을 보냈어요. 홈 화면에서 완료해 주세요." else "추가 요청을 열지 못했어요. 홈 화면의 위젯 목록에서 FESTICORE를 추가해 주세요.",
                    style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                    color = FestiCoreSemanticColor.labelAssistive.color,
                )
            }
        }
    }
}
