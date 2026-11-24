package io.festicore.ui.designkit.theme

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.festicore.ui.designkit.component.modal.FCSheet
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 다크 고정(설계 B6)이 라이트 기기에서도 화면과 그 안에서 띄운 시트를 다크로 그리는지 본다. Robolectric 기기 설정은 라이트다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FestiCoreDarkModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val token = FestiCoreSemanticColor.backgroundElevated

    @Test
    fun `다크 고정 안의 화면과 시트는 라이트 기기에서도 다크 색을 쓴다`() {
        var screenColor: Color? = null
        var sheetColor: Color? = null
        composeRule.setContent {
            FestiCoreDarkMode {
                screenColor = token.color
                FCSheet(isPresented = true, onDismissRequest = {}) {
                    sheetColor = token.color
                    BasicText("시트")
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(token.dark, screenColor)
        assertEquals(token.dark, sheetColor)
    }
}
