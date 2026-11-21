package io.festicore.ui.designkit.component.navigation

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * iOS `FestivalListView.swift` 261행 `.onSubmit`: 키보드 검색 키를 누르면 제출 동작이 불린다.
 * 제출 뒤 초점을 놓는 동작(키보드 닫힘)은 Robolectric이 초점을 다시 주어 단위 테스트로 확인하지 못해 에뮬레이터에서 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FCTopBarSearchTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `검색 키를 누르면 제출 동작을 한 번 부른다`() {
        var submits = 0
        composeRule.setContent {
            FCTopBar(
                FCTopBarStyle.Search(text = "실리카겔", onTextChange = {}),
                onSearchSubmit = { submits += 1 },
            )
        }
        val field = composeRule.onNode(hasSetTextAction())
        field.performClick()

        field.performImeAction()

        assertEquals(1, submits)
    }
}
