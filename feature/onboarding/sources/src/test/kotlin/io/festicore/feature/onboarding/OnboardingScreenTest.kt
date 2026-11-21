package io.festicore.feature.onboarding

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `건너뛰기를 누르면 완료 결과를 알린다`() {
        val results = mutableListOf<OnboardingResult>()
        composeRule.setContent { OnboardingScreen(onResult = results::add, viewModel = OnboardingViewModel()) }

        composeRule.onNodeWithText("건너뛰기").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<OnboardingResult>(OnboardingResult.Completed), results)
    }

    @Test
    fun `그림을 누르면 다음 페이지로 넘어가고 마지막 그림은 시작하기다`() {
        val results = mutableListOf<OnboardingResult>()
        composeRule.setContent { OnboardingScreen(onResult = results::add, viewModel = OnboardingViewModel()) }

        // Orbit intent는 Compose 시계 밖에서 상태를 바꾸므로, 페이지 점이 다음 페이지를 가리킬 때까지 기다린 뒤 다시 누른다.
        repeat(3) { index ->
            composeRule.onNodeWithContentDescription("다음 화면").performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription("${index + 2} / 4 페이지").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithContentDescription("시작하기").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<OnboardingResult>(OnboardingResult.Completed), results)
    }

    @Test
    fun `iOS와 같은 UI 테스트 식별자로 건너뛰기 · 그림 · 페이지 점을 찾는다`() {
        composeRule.setContent { OnboardingScreen(onResult = {}, viewModel = OnboardingViewModel()) }

        composeRule.onNodeWithTag(OnboardingTestTag.SKIP).assertExists()
        composeRule.onAllNodesWithTag(OnboardingTestTag.NEXT).onFirst().assertExists()
        composeRule.onNodeWithTag(OnboardingTestTag.PAGE_INDICATOR).assertExists()
    }
}
