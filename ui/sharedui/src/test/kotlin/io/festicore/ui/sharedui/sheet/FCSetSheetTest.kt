package io.festicore.ui.sharedui.sheet

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.festicore.ui.designkit.component.toast.FCToast
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.sharedui.toast.FCToastItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp")
class FCSetSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `하단 상태는 고정된 자리에서 아래쪽으로부터 나타난다`() {
        val visible = mutableStateOf(false)
        composeRule.setContent {
            FCSetSheet(
                artist = "라임", stage = "메인", time = "17:00", relativeTime = "30분 후",
                isScheduled = false, onIsScheduledChange = {}, modifier = Modifier.testTag("sheet"),
                bottomStatus = { Box(Modifier.testTag("toastBounds")) { FCToast(FCToastItems.scheduleAdded()) } },
                isBottomStatusVisible = visible.value,
            )
        }
        val before = composeRule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { visible.value = true }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.waitForIdle()
        val entering = composeRule.onNodeWithTag("toastBounds", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        val settled = composeRule.onNodeWithTag("toastBounds", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("진입 중에는 최종 위치보다 아래에 있어야 한다", entering.top > settled.top)
        assertEquals(before, composeRule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun `하단 상태는 숨겨도 측정 높이를 유지하고 접근성에서 제외한다`() {
        val visible = mutableStateOf(false)
        composeRule.setContent {
            FCSetSheet(
                artist = "라임", stage = "메인 스테이지", time = "17:00–17:50", relativeTime = "30분 후",
                isScheduled = false, onIsScheduledChange = {}, source = "공식 타임테이블",
                modifier = Modifier.testTag("sheet"),
                bottomStatus = {
                    Box(Modifier.testTag("toastBounds")) { FCToast(FCToastItems.scheduleAdded()) }
                },
                isBottomStatusVisible = visible.value,
            )
        }
        val switch = composeRule.onNodeWithContentDescription("내 일정에 넣기")
        val before = switch.fetchSemanticsNode().boundsInRoot
        val sheetBefore = composeRule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val toast = composeRule.onNodeWithText("내 일정에 추가됐어요")
        toast.assertDoesNotExist()
        composeRule.runOnIdle { visible.value = true }
        toast.assertIsDisplayed()
        val toastBounds = composeRule.onNodeWithTag("toastBounds").fetchSemanticsNode().boundsInRoot
        assertTrue(toastBounds.top >= before.bottom)
        assertEquals(with(composeRule.density) { FestiCoreSpacing.paddingP16.toPx() }, sheetBefore.bottom - toastBounds.bottom, 1f)
        assertEquals(sheetBefore.center.x, toastBounds.center.x, 1f)
        assertEquals(before, switch.fetchSemanticsNode().boundsInRoot)
        assertEquals(sheetBefore, composeRule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot)
        composeRule.runOnIdle { visible.value = false }
        toast.assertDoesNotExist()
        assertEquals(before, switch.fetchSemanticsNode().boundsInRoot)
        assertEquals(sheetBefore, composeRule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot)
    }
}
