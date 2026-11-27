package io.festicore.feature.profile.settings

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** S13-C의 Android 홈 위젯 대응. 설치 상태를 저장하거나 요청 성공으로 추정하지 않는다. */
class HomeWidgetSettingsViewModel :
    ViewModel(),
    OrbitContainerHost<HomeWidgetSettingsState, HomeWidgetSettingsState, HomeWidgetSettingsSideEffect> {
    override val container = orbitContainer<HomeWidgetSettingsState, HomeWidgetSettingsSideEffect>(HomeWidgetSettingsState())

    /** 최초 표시와 foreground 복귀 때 런처를 다시 읽는다. 취소된 요청 안내도 초기화한다. */
    fun onStatusChanged(status: HomeWidgetStatus) = intent {
        reduce { state.copy(status = status, pinRequestAccepted = null) }
    }

    fun onBackTapped() = intent { postSideEffect(HomeWidgetSettingsSideEffect.BackRequested) }

    fun onAddTapped() = intent {
        val status = state.status ?: return@intent
        if (!status.isPinSupported || status.isInstalled || state.isRequesting) return@intent
        reduce { state.copy(isRequesting = true, pinRequestAccepted = null) }
        postSideEffect(HomeWidgetSettingsSideEffect.PinRequested)
    }

    fun onPinResult(accepted: Boolean) = intent {
        reduce { state.copy(isRequesting = false, pinRequestAccepted = accepted) }
    }
}
