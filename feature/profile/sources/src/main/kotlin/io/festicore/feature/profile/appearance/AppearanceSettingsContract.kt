package io.festicore.feature.profile.appearance

/**
 * 화면 모드 화면 상태. iOS `AppearanceSettingsFeature.State`.
 *
 * @param mode 저장된 화면 모드. 저장소를 읽기 전에는 iOS 기본값인 다크다.
 */
data class AppearanceSettingsState(val mode: AppearanceMode = AppearanceMode.Dark)

/** iOS `AppearanceSettingsFeature.DelegateAction`. */
sealed interface AppearanceSettingsSideEffect {
    /** 설정 홈으로 돌아간다. */
    data object BackRequested : AppearanceSettingsSideEffect
}
