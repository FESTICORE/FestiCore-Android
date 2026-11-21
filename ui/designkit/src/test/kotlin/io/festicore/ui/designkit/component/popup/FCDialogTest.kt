package io.festicore.ui.designkit.component.popup

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * iOS `FCDialog.swift` 64~78행: 문구 frame은 `.center`일 때만 가운데이고 나머지(`.leading` · `.trailing`)는 `.leading`이다.
 * 그래서 짧은 한 줄 제목은 Trailing이어도 카드 왼쪽 안쪽 여백에서 시작하고, Center일 때만 가운데에 놓인다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FCDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `짧은 한 줄 제목은 Trailing이어도 iOS처럼 왼쪽에서 시작한다`() {
        val (cardLeft, titleLeft, _) = titleBounds(FCDialogTextAlign.Trailing)

        assertEquals((cardLeft + FestiCoreSpacing.paddingP20).value, titleLeft.value, 0.5f)
    }

    @Test
    fun `짧은 한 줄 제목은 Center면 카드 가운데에 놓인다`() {
        val (cardLeft, titleLeft, titleRight) = titleBounds(FCDialogTextAlign.Center)
        val cardCenter = cardLeft + 311.dp / 2

        assertTrue(titleLeft > cardLeft + FestiCoreSpacing.paddingP20)
        assertEquals(cardCenter.value, ((titleLeft + titleRight) / 2).value, 0.5f)
    }

    private fun titleBounds(textAlign: FCDialogTextAlign): Triple<Dp, Dp, Dp> {
        composeRule.setContent {
            FCDialogCard(
                item = FCDialogItem(title = "삭제", message = "설명", onConfirm = {}),
                onDismiss = {},
                modifier = Modifier.testTag("card"),
                textAlign = textAlign,
            )
        }
        val card = composeRule.onNodeWithTag("card").getUnclippedBoundsInRoot()
        val title = composeRule.onNode(isHeading()).getUnclippedBoundsInRoot()
        return Triple(card.left, title.left, title.right)
    }
}
