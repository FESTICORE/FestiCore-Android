package io.festicore.feature.onboarding

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

        repeat(3) {
            composeRule.onNodeWithContentDescription("다음 화면").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithContentDescription("시작하기").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<OnboardingResult>(OnboardingResult.Completed), results)
    }
}
