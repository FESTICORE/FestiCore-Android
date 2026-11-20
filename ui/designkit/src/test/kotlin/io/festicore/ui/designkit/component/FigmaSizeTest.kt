package io.festicore.ui.designkit.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.festicore.ui.designkit.component.action.FCMenuItem
import io.festicore.ui.designkit.component.action.FCSortButton
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** iOS `FestiCoreDesignKitFigmaSizeTests`의 `FigmaHeightCase.all` 중 C1 컴포넌트 케이스. 기대값 · 허용 오차는 iOS와 같다. */
// 글자 높이를 실제로 재려면 NATIVE 그래픽이 필요하다. SDK 36 이상에서는 Espresso가 없어진 InputManager.getInstance()를 불러 실패하므로 35로 고정한다.
// 기본 mdpi(1x)에서는 행간 18.005sp가 픽셀 올림으로 19dp가 되므로 실제 기기에 가까운 xxhdpi에서 잰다.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FigmaSizeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Menu Item 선택은 Figma 심볼 높이 48이다`() {
        assertFigmaHeight(figmaHeight = 48.dp, width = 200.dp) { FCMenuItem("활동 중 순", isSelected = true) }
    }

    @Test
    fun `Menu Item은 Figma 심볼 높이 48이다`() {
        assertFigmaHeight(figmaHeight = 48.dp, width = 200.dp) { FCMenuItem("활동 중 순") }
    }

    @Test
    fun `Sort Button은 Figma 심볼 높이 26이다`() {
        assertFigmaHeight(figmaHeight = 26.dp, width = null) { FCSortButton("활동 중 순") {} }
    }

    private fun assertFigmaHeight(
        figmaHeight: Dp,
        width: Dp?,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                Box(modifier = Modifier.testTag(SUBJECT).then(if (width == null) Modifier else Modifier.width(width))) {
                    content()
                }
            }
        }
        val height = composeRule.onNodeWithTag(SUBJECT).getUnclippedBoundsInRoot().height
        assertTrue("렌더 높이 $height, Figma $figmaHeight", abs((height - figmaHeight).value) <= 0.5f)
    }

    private companion object {
        const val SUBJECT = "subject"
    }
}
