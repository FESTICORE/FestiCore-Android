package io.festicore.app.application

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.festicore.core.storage.shared.AppearanceMode
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppearanceModeProviderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `라이트 · 다크는 기기 설정과 상관없이 생성된 색에 적용되고 시스템 설정과 같게는 바깥 값을 따른다`() {
        val colors = mutableMapOf<String, Color>()
        composeRule.setContent {
            // Robolectric 기기 설정은 라이트다.
            AppearanceModeProvider(AppearanceMode.SYSTEM) { colors["system"] = FestiCoreSemanticColor.backgroundNormal.color }
            AppearanceModeProvider(AppearanceMode.DARK) {
                colors["dark"] = FestiCoreSemanticColor.backgroundNormal.color
                AppearanceModeProvider(AppearanceMode.SYSTEM) { colors["systemInDark"] = FestiCoreSemanticColor.backgroundNormal.color }
                AppearanceModeProvider(AppearanceMode.LIGHT) { colors["light"] = FestiCoreSemanticColor.backgroundNormal.color }
            }
        }
        composeRule.waitForIdle()

        val token = FestiCoreSemanticColor.backgroundNormal
        assertEquals(token.light, colors["system"])
        assertEquals(token.dark, colors["dark"])
        assertEquals(token.dark, colors["systemInDark"])
        assertEquals(token.light, colors["light"])
    }
}
