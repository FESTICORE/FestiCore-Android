package io.festicore.ui.designkit.theme

import android.content.res.Configuration
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.window.Dialog
import io.festicore.ui.designkit.component.modal.FCSheet
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 앱 루트가 덮어쓴 화면 모드(야간 비트)가 새 창(Dialog · 시트) 안에서도 유지되는지 본다. Robolectric 기기 설정은 라이트다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WindowNightModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val token = FestiCoreSemanticColor.backgroundElevated

    @Test
    fun `새 창은 바깥에서 덮어쓴 야간 비트를 잃는다`() {
        var color: Color? = null
        composeRule.setContent {
            AppNightMode(night = true) {
                Dialog(onDismissRequest = {}) { color = token.color }
            }
        }
        composeRule.waitForIdle()

        assertEquals(token.light, color)
    }

    @Test
    fun `시트 내용은 띄운 화면의 다크 모드를 따른다`() {
        var color: Color? = null
        composeRule.setContent {
            AppNightMode(night = true) {
                FCSheet(isPresented = true, onDismissRequest = {}) {
                    color = token.color
                    BasicText("시트")
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(token.dark, color)
    }

    @Test
    fun `라이트로 덮어쓴 화면에서 띄운 시트는 라이트를 따른다`() {
        var color: Color? = null
        composeRule.setContent {
            AppNightMode(night = true) {
                AppNightMode(night = false) {
                    FCSheet(isPresented = true, onDismissRequest = {}) {
                        color = token.color
                        BasicText("시트")
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(token.light, color)
    }
}

@Composable
private fun AppNightMode(night: Boolean, content: @Composable () -> Unit) {
    val base = LocalConfiguration.current
    val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    val configuration = Configuration(base).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode }
    CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}
