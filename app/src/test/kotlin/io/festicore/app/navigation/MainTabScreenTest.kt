package io.festicore.app.navigation

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MainTabScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tab(title: String) = composeRule.onNode(hasText(title) and isSelectable())

    @Test
    fun `라이브 스택이 목록뿐이면 탭 바를 숨긴다`() {
        composeRule.setContent {
            MainTabScreen(liveContent = { onHidesTabBarChanged, _ ->
                LaunchedEffect(Unit) { onHidesTabBarChanged(true) }
                Text("라이브 목록")
            })
        }

        composeRule.onNodeWithText("라이브 목록").assertExists()
        composeRule.onAllNodesWithText("타임테이블").assertCountEquals(0)
    }

    @Test
    fun `라이브 스택이 쌓이면 탭 바가 보이고 탭을 바꿔 다시 만들어도 선택이 유지된다`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            MainTabScreen(liveContent = { onHidesTabBarChanged, _ ->
                LaunchedEffect(Unit) { onHidesTabBarChanged(false) }
                Text("라이브 보드")
            })
        }

        tab("라이브").assertIsSelected()
        composeRule.onNodeWithText("라이브 보드").assertExists()

        tab("타임테이블").performClick()
        tab("타임테이블").assertIsSelected()
        tab("라이브").assertIsNotSelected()
        composeRule.onAllNodesWithText("타임테이블").assertCountEquals(2)

        restoration.emulateSavedInstanceStateRestore()
        tab("타임테이블").assertIsSelected()
    }

    @Test
    fun `라이브 흐름이 깃발 탭을 요청하면 깃발 탭으로 바꾼다`() {
        composeRule.setContent {
            MainTabScreen(liveContent = { onHidesTabBarChanged, onFlagTabRequested ->
                LaunchedEffect(Unit) { onHidesTabBarChanged(false) }
                Text("깃발 찾기", modifier = Modifier.clickable(onClick = onFlagTabRequested))
            })
        }

        composeRule.onNodeWithText("깃발 찾기").performClick()

        tab("깃발").assertIsSelected()
        tab("라이브").assertIsNotSelected()
    }
}
