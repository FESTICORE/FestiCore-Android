package io.festicore.ui.sharedui.sheet

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** iOS `FCReportSheet`: 제목은 heading, 선택지는 selected, 사유를 고르기 전에는 제출이 꺼진다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FCReportSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `사유를 고른 뒤에만 고른 위치로 제출한다`() {
        val submitted = mutableListOf<Int>()
        composeRule.setContent {
            var selection by remember { mutableStateOf<Int?>(null) }
            FCReportSheet(
                selection = selection,
                options = listOf("가짜 깃발이에요", "부적절한 사진이나 소개예요", "기타"),
                onSelect = { selection = it },
                onSubmit = { submitted += it },
            )
        }
        composeRule.onNodeWithText("어떤 문제가 있나요?").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("신고하기").assertIsNotEnabled()

        composeRule.onNodeWithText("부적절한 사진이나 소개예요").performClick()

        composeRule.onNodeWithText("부적절한 사진이나 소개예요").assertIsSelected()
        composeRule.onNodeWithText("가짜 깃발이에요").assertIsNotSelected()
        composeRule.onNodeWithText("신고하기").assertIsEnabled().performClick()
        assertEquals(listOf(1), submitted)
    }
}
