package io.festicore.ui.designkit.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCBigButton
import io.festicore.ui.designkit.component.action.FCBigButtonSize
import io.festicore.ui.designkit.theme.FestiCoreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FCLargeFontLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `큰 글꼴의 작은 상태 버튼은 문구 전체와 클릭을 유지한다`() {
        var clicks = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                FestiCoreTheme {
                    Box(Modifier.width(76.dp)) {
                        FCBigButton("화장실", "", R.drawable.ic_circle_info_20, size = FCBigButtonSize.Small, onClick = { clicks++ })
                    }
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("화장실", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        val layout = results.single()
        assertFalse(layout.hasVisualOverflow)
        assertEquals(3, layout.getLineEnd(layout.lineCount - 1))
        for (line in 0 until layout.lineCount) assertFalse(layout.isLineEllipsized(line))
        composeRule.onNodeWithText("화장실").performClick()
        assertEquals(1, clicks)
    }

    @Test fun `큰 글꼴 일정 행의 시작과 종료 시각을 전부 읽을 수 있다`() = verifySelectionRow(2f, 328)

    @Test fun `기본 글꼴 일정 행의 시간과 터치를 유지한다`() = verifySelectionRow(1f, 328)

    @Test fun `좁은 화면 큰 글꼴 일정 행의 시간과 터치를 유지한다`() = verifySelectionRow(2f, 280)

    private fun verifySelectionRow(scale: Float, width: Int) {
        var clicks = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                FestiCoreTheme {
                    Box(Modifier.width(width.dp)) {
                        io.festicore.ui.designkit.component.selection.FCSetSelectRow(
                            artist = "아티스트",
                            stage = "무대",
                            start = "19:30",
                            end = "20:40",
                            isSelected = true,
                            onClick = { clicks++ },
                        )
                    }
                }
            }
        }
        for (text in listOf("19:30", "20:40")) {
            val results = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
            val layout = results.single()
            assertFalse(layout.hasVisualOverflow)
            assertFalse(layout.didOverflowWidth)
            assertFalse(layout.isLineEllipsized(0))
            assertEquals(text.length, layout.getLineEnd(0, visibleEnd = true))
        }
        composeRule.onNodeWithText("아티스트", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("무대", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("19:30").performClick()
        assertEquals(1, clicks)
    }

    @Test fun `큰 글꼴 공개 계획의 시각을 전부 읽을 수 있다`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                FestiCoreTheme {
                    Box(Modifier.width(328.dp)) {
                        io.festicore.ui.designkit.component.list.FCPlanRow(
                            time = "20:30",
                            artist = "아티스트",
                            stage = "무대",
                            state = io.festicore.ui.designkit.component.list.FCPlanRowState.Now,
                            isMine = true,
                        )
                    }
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("20:30", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        assertFalse(results.single().hasVisualOverflow)
    }
}
