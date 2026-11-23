package io.festicore.feature.profile.appearance

import androidx.lifecycle.ViewModel
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** 화면 모드(Figma S13-J). iOS `AppearanceSettingsFeature`. 고르면 바로 저장하고, 앱 루트가 저장 값을 읽어 즉시 적용한다(설계 쟁점 M6). */
class AppearanceSettingsViewModel(private val appearanceModeSettings: AppearanceModeSettings) :
    ViewModel(),
    OrbitContainerHost<AppearanceSettingsState, AppearanceSettingsState, AppearanceSettingsSideEffect> {
    override val container =
        orbitContainer<AppearanceSettingsState, AppearanceSettingsSideEffect>(AppearanceSettingsState()) {
            appearanceModeSettings.appearanceMode.collect { onModeLoaded(it) }
        }

    // view

    fun onModeTapped(mode: AppearanceMode) = intent {
        reduce { state.copy(mode = mode) }
        appearanceModeSettings.saveAppearanceMode(mode)
    }

    fun onBackTapped() = intent { postSideEffect(AppearanceSettingsSideEffect.BackRequested) }

    // inner

    private suspend fun onModeLoaded(mode: AppearanceMode) = subIntent {
        if (state.mode != mode) reduce { state.copy(mode = mode) }
    }
}
