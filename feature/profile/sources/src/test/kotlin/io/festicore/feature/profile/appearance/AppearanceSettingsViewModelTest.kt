package io.festicore.feature.profile.appearance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `AppearanceSettingsFeatureTests` 3건(같은 이름). 화면 모드를 앱에 적용하는 것은 app `AppearanceModeProviderTest`가 본다. */
class AppearanceSettingsViewModelTest {
    private class FakeAppearanceModeSettings : AppearanceModeSettings {
        override val appearanceMode = MutableStateFlow(AppearanceMode.Dark)
        val saved = mutableListOf<AppearanceMode>()

        override suspend fun saveAppearanceMode(mode: AppearanceMode) {
            saved += mode
            appearanceMode.value = mode
        }
    }

    @Test
    fun selectsAndPersistsMode() = runTest {
        val settings = FakeAppearanceModeSettings()
        val viewModel = AppearanceSettingsViewModel(settings)
        // 기본은 지금까지처럼 다크다.
        assertEquals(AppearanceMode.Dark, viewModel.container.stateFlow.value.mode)
        viewModel.test(this) {
            containerHost.onModeTapped(AppearanceMode.System)
            expectState { copy(mode = AppearanceMode.System) }
            containerHost.onModeTapped(AppearanceMode.Light)
            expectState { copy(mode = AppearanceMode.Light) }
        }
        assertEquals(listOf(AppearanceMode.System, AppearanceMode.Light), settings.saved)
    }

    @Test
    fun backReturns() = runTest {
        AppearanceSettingsViewModel(FakeAppearanceModeSettings()).test(this) {
            containerHost.onBackTapped()
            expectSideEffect(AppearanceSettingsSideEffect.BackRequested)
        }
    }

    @Test
    fun modePresentation() {
        assertEquals(listOf("시스템 설정과 같게", "라이트", "다크"), AppearanceMode.entries.map { it.title })
        assertEquals("시스템 설정", AppearanceMode.System.summary)
        assertEquals(AppearanceMode.System, AppearanceMode.fromStoredValue("system"))
        assertEquals(AppearanceMode.Dark, AppearanceMode.fromStoredValue("unknown"))
        assertEquals(listOf("system", "light", "dark"), AppearanceMode.entries.map { it.storedValue })
    }
}
