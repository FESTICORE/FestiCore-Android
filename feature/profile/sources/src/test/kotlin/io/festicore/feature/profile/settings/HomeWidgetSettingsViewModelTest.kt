package io.festicore.feature.profile.settings

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.orbitmvi.orbit.test.test

class HomeWidgetSettingsViewModelTest {
    @Test
    fun `지원되지 않는 런처와 설치된 위젯에는 추가를 요청하지 않는다`() = runTest {
        HomeWidgetSettingsViewModel().test(this) {
            containerHost.onAddTapped()
            containerHost.onStatusChanged(HomeWidgetStatus(false, false))
            expectState { copy(status = HomeWidgetStatus(false, false)) }
            containerHost.onAddTapped()
            containerHost.onStatusChanged(HomeWidgetStatus(true, true))
            expectState { copy(status = HomeWidgetStatus(true, true)) }
            containerHost.onAddTapped()
        }
    }

    @Test
    fun `요청 수락은 설치가 아니며 복귀 조회가 취소와 설치를 구분한다`() = runTest {
        HomeWidgetSettingsViewModel().test(this) {
            containerHost.onStatusChanged(HomeWidgetStatus(true, false))
            expectState { copy(status = HomeWidgetStatus(true, false)) }
            containerHost.onAddTapped()
            expectState { copy(isRequesting = true) }
            expectSideEffect(HomeWidgetSettingsSideEffect.PinRequested)
            containerHost.onAddTapped()
            containerHost.onPinResult(true)
            expectState { copy(isRequesting = false, pinRequestAccepted = true) }
            containerHost.onStatusChanged(HomeWidgetStatus(true, false))
            expectState { copy(pinRequestAccepted = null) }
            containerHost.onStatusChanged(HomeWidgetStatus(true, true))
            expectState { copy(status = HomeWidgetStatus(true, true)) }
            containerHost.onStatusChanged(HomeWidgetStatus(true, false))
            expectState { copy(status = HomeWidgetStatus(true, false)) }
        }
    }

    @Test
    fun `요청 거절은 설치 상태를 유지하고 재시도할 수 있다`() = runTest {
        HomeWidgetSettingsViewModel().test(this, initialState = HomeWidgetSettingsState(status = HomeWidgetStatus(true, false))) {
            containerHost.onAddTapped()
            expectState { copy(isRequesting = true) }
            expectSideEffect(HomeWidgetSettingsSideEffect.PinRequested)
            containerHost.onPinResult(false)
            expectState { copy(isRequesting = false, pinRequestAccepted = false) }
            containerHost.onAddTapped()
            expectState { copy(isRequesting = true, pinRequestAccepted = null) }
            expectSideEffect(HomeWidgetSettingsSideEffect.PinRequested)
            containerHost.onBackTapped()
            expectSideEffect(HomeWidgetSettingsSideEffect.BackRequested)
        }
    }
}
