package io.festicore.feature.profile.settings

/** app이 Android 런처에서 읽어 전달하는 사실. 추가 요청의 수락은 설치 완료가 아니다. */
data class HomeWidgetStatus(
    val isPinSupported: Boolean,
    val isInstalled: Boolean,
)

data class HomeWidgetSettingsState(
    val status: HomeWidgetStatus? = null,
    val isRequesting: Boolean = false,
    val pinRequestAccepted: Boolean? = null,
)

sealed interface HomeWidgetSettingsSideEffect {
    data object BackRequested : HomeWidgetSettingsSideEffect
    data object PinRequested : HomeWidgetSettingsSideEffect
}
