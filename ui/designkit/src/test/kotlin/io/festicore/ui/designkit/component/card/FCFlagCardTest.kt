package io.festicore.ui.designkit.component.card

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.festicore.ui.designkit.component.action.FCButtonStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** iOS `FestiCoreDesignKitTests.flagCardFollowButtonMatchesFigmaVariants`를 같은 기대값으로 옮겼다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FCFlagCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context =
        FCFlagCardContext.Directory(followers = "팔로워 1,284", tags = listOf("슬램"), isFollowing = false, onFollow = {})

    @Test
    fun `팔로우 전은 inverse, 팔로우 중은 secondary 버튼이다`() {
        assertEquals(FCButtonStyle.Inverse, fcFlagCardFollowStyle(isFollowing = false))
        assertEquals(FCButtonStyle.Secondary, fcFlagCardFollowStyle(isFollowing = true))
    }

    @Test
    fun `팔로우 버튼은 기본으로 켜져 있다`() {
        composeRule.setContent {
            FCFlagCard(name = "호락호락", status = null, zone = "리벳 스테이지 앞 왼쪽", context = context)
        }
        composeRule.onNodeWithText("팔로우").assertIsEnabled()
    }

    @Test
    fun `isFollowEnabled가 false면 팔로우 버튼을 끈다`() {
        composeRule.setContent {
            FCFlagCard(name = "호락호락", status = null, zone = "리벳 스테이지 앞 왼쪽", context = context, isFollowEnabled = false)
        }
        composeRule.onNodeWithText("팔로우").assertIsNotEnabled()
    }
}
