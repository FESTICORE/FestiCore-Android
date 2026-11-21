package io.festicore.app.navigation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainTabScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tab(title: String) = composeRule.onNode(hasText(title) and isSelectable())

    @Test
    fun `라이브가 기본이고 탭을 바꾸면 그 탭 제목이 보이며 다시 만들어도 선택이 유지된다`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { MainTabScreen() }

        tab("라이브").assertIsSelected()
        composeRule.onAllNodesWithText("라이브").assertCountEquals(2)

        tab("타임테이블").performClick()
        tab("타임테이블").assertIsSelected()
        tab("라이브").assertIsNotSelected()
        composeRule.onAllNodesWithText("타임테이블").assertCountEquals(2)

        restoration.emulateSavedInstanceStateRestore()
        tab("타임테이블").assertIsSelected()
    }
}
