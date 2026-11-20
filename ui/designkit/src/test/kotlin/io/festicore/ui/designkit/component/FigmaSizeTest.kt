package io.festicore.ui.designkit.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCMenuItem
import io.festicore.ui.designkit.component.action.FCSortButton
import io.festicore.ui.designkit.component.label.FCBadge
import io.festicore.ui.designkit.component.label.FCBearerFooter
import io.festicore.ui.designkit.component.label.FCChecklistProgress
import io.festicore.ui.designkit.component.label.FCLiveBadge
import io.festicore.ui.designkit.component.label.FCLiveBadgeKind
import io.festicore.ui.designkit.component.label.FCOfflineBadge
import io.festicore.ui.designkit.component.label.FCSourceLine
import io.festicore.ui.designkit.component.label.FCStatusChip
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCZoneChip
import io.festicore.ui.designkit.font.FestiCoreFontFamily
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** iOS `FestiCoreDesignKitFigmaSizeTests`의 `FigmaHeightCase.all` 중 C1 · C2 컴포넌트 케이스와 배지 · 존 칩 크기 검사. 기대값 · 허용 오차는 iOS와 같다. */
// 글자 높이를 실제로 재려면 NATIVE 그래픽이 필요하다. SDK 36 이상에서는 Espresso가 없어진 InputManager.getInstance()를 불러 실패하므로 35로 고정한다.
// Robolectric 기본 화면 폭(320dp)보다 넓은 Figma 폭(375 · 393)도 그대로 재도록 requiredWidth로 둔다.
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

    @Test
    fun `Tag는 Figma 심볼 높이 18이다`() {
        assertFigmaHeight(figmaHeight = 18.dp, width = null) { FCTag("슬램") }
    }

    @Test
    fun `Status Chip은 Figma 심볼 높이 24이다`() {
        assertFigmaHeight(figmaHeight = 24.dp, width = null) { FCStatusChip(FCStatusChipStatus.Active) }
    }

    @Test
    fun `Live Badge는 Figma 심볼 높이 24이다`() {
        assertFigmaHeight(figmaHeight = 24.dp, width = null) { FCLiveBadge(FCLiveBadgeKind.Now) }
    }

    @Test
    fun `Offline Badge는 Figma 심볼 높이 36이다`() {
        assertFigmaHeight(figmaHeight = 36.dp, width = 375.dp) { FCOfflineBadge("오프라인 · 3분 전 정보") }
    }

    @Test
    fun `Source Line은 Figma 심볼 높이 40이다`() {
        assertFigmaHeight(figmaHeight = 40.dp, width = 375.dp) { FCSourceLine("출처: 주최측 공지 · 확인 10/16 14:00") }
    }

    @Test
    fun `Bearer Footer는 Figma 심볼 높이 42이다`() {
        assertFigmaHeight(figmaHeight = 42.dp, width = 393.dp) { FCBearerFooter() }
    }

    @Test
    fun `Checklist Progress는 Figma 심볼 높이 72이다`() {
        assertFigmaHeight(figmaHeight = 72.dp, width = 361.dp) { FCChecklistProgress(checked = 3, total = 7) }
    }

    @Test
    fun `배지는 Figma 심볼처럼 16×16이다`() {
        val bounds = renderedBounds(width = null) { FCBadge(count = 3) }
        assertEquals(16.dp, bounds.width)
        assertEquals(16.dp, bounds.height)
    }

    @Test
    fun `존 칩은 좌우 테두리를 포함해 100×26이다`() {
        // 폭은 글자 폭에 기대므로 Pretendard가 등록됐는지 먼저 확인한다.
        assertNotNull(RuntimeEnvironment.getApplication().resources.getFont(R.font.pretendard_variable))
        assertEquals(FestiCoreFontFamily.pretendard, FestiCoreTextStyle.baseCaption1Medium.textStyle.fontFamily)
        val bounds = renderedBounds(width = null) { FCZoneChip("리벳 앞 왼쪽") {} }

        // Figma Zone Chip 100×26 = 테두리 1 + 6 + 아이콘 16 + 8 + 글자 60 + 8 + 테두리 1
        assertTrue("렌더 폭 ${bounds.width}, Figma 100", abs((bounds.width - 100.dp).value) <= 1.5f)
        assertTrue("렌더 높이 ${bounds.height}, Figma 26", abs((bounds.height - 26.dp).value) <= 0.5f)
    }

    private fun assertFigmaHeight(
        figmaHeight: Dp,
        width: Dp?,
        content: @Composable () -> Unit,
    ) {
        val height = renderedBounds(width, content).height
        assertTrue("렌더 높이 $height, Figma $figmaHeight", abs((height - figmaHeight).value) <= 0.5f)
    }

    private fun renderedBounds(width: Dp?, content: @Composable () -> Unit): DpRect {
        composeRule.setContent {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                Box(modifier = Modifier.testTag(SUBJECT).then(if (width == null) Modifier else Modifier.requiredWidth(width))) {
                    content()
                }
            }
        }
        return composeRule.onNodeWithTag(SUBJECT).getUnclippedBoundsInRoot()
    }

    private companion object {
        const val SUBJECT = "subject"
    }
}
