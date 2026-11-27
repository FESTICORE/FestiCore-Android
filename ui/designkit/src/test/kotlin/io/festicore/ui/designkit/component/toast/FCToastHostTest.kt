package io.festicore.ui.designkit.component.toast

import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.festicore.ui.designkit.R
import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FCToastHostTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `토스트는 화면 아래 안전 영역에서 16만큼 떨어진다`() = assertBottomGap(navigation = 48, ime = 0)

    @Test
    fun `키보드와 내비게이션 높이 중 큰 값만 적용한다`() = assertBottomGap(navigation = 48, ime = 180)

    private fun assertBottomGap(navigation: Int, ime: Int) {
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            FCToastHost(
                item = FCToastItem(R.drawable.ic_check, "저장했어요"),
                onItemChange = {},
                duration = 1.hours,
                modifier = Modifier.fillMaxSize().testTag("host"),
            ) {}
        }
        composeRule.runOnIdle {
            view.dispatchApplyWindowInsets(
                WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, navigation))
                    .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, ime))
                    .build(),
            )
        }
        val host = composeRule.onNodeWithTag("host").getUnclippedBoundsInRoot()
        val toast = composeRule.onNodeWithText("저장했어요").getUnclippedBoundsInRoot()
        assertEquals((maxOf(navigation, ime) + 16).toFloat(), (host.bottom - toast.bottom).value, 1f)
        assertEquals(((host.left + host.right) / 2).value, ((toast.left + toast.right) / 2).value, 1f)
    }
}
