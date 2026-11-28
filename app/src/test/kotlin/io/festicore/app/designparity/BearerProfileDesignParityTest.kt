package io.festicore.app.designparity

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.feature.bearer.profile.BearerPhotoUpload
import io.festicore.feature.bearer.profile.BearerProfileCallbacks
import io.festicore.feature.bearer.profile.BearerProfileContent
import io.festicore.feature.bearer.profile.BearerProfileState
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BearerProfileDesignParityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `마이에서 수정하는 프로필은 편집 제목을 표시한다`() = assertEditForm("s10-edit-light")

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-mdpi")
    fun `어두운 테마에서도 편집 제목과 잠긴 축제를 표시한다`() = assertEditForm("s10-edit-dark")

    private fun assertEditForm(captureName: String) {
        var saves = 0
        composeRule.setContent {
            FestiCoreTheme {
                BearerProfileContent(
                    state = state(),
                    modifier = Modifier.testTag("screen"),
                    callbacks = BearerProfileCallbacks(onSaveTapped = { saves++ }),
                )
            }
        }
        composeRule.onNodeWithText("프로필 수정").assertIsDisplayed()
        composeRule.onNodeWithText("깃발 프로필 수정").assertIsDisplayed()
        composeRule.onNodeWithText("팔로워에게 보이는 내 깃발이에요.\n고친 내용은 저장하면 바로 반영돼요.").assertIsDisplayed()
        val tile = composeRule.onNode(hasText("원더리벳 2026") and isSelected())
        tile.assertIsNotEnabled().assertIsSelected()
        tile.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "참여 페스티벌은 바꿀 수 없어요"))
        tile.performTouchInput { click() }
        composeRule.onNodeWithText("참여 페스티벌은 바꿀 수 없어요. 다른 페스티벌 깃발은 마이 > '다른 페스티벌 깃발 만들기'에서 새로 만들어요.")
            .assertIsDisplayed()
        val header = composeRule.onNodeWithText("깃발 프로필 수정").getUnclippedBoundsInRoot()
        val festival = tile.getUnclippedBoundsInRoot()
        val photo = composeRule.onNodeWithText("깃발 사진").getUnclippedBoundsInRoot()
        assertTrue(header.bottom <= festival.top)
        assertTrue(festival.bottom <= photo.top)
        composeRule.onNodeWithText("다른 페스티벌 찾기").assertDoesNotExist()
        capture(captureName)
        composeRule.onNodeWithText("저장").performClick()
        assertEquals(1, saves)
    }

    @Test
    fun `사진 업로드 중에는 편집 저장을 누를 수 없다`() {
        composeRule.setContent {
            FestiCoreTheme {
                BearerProfileContent(state().copy(photoUpload = BearerPhotoUpload.Uploading(byteArrayOf(), 0.5)))
            }
        }
        composeRule.onNodeWithText("사진 올리는 중이에요").assertIsNotEnabled()
    }

    @Test
    fun `일반 프로필은 기존 제목과 저장 완료 화면을 유지한다`() {
        composeRule.setContent {
            FestiCoreTheme { BearerProfileContent(state().copy(isEditMode = false)) }
        }
        composeRule.onNodeWithText("깃발 프로필").assertIsDisplayed()
        composeRule.onNodeWithText("저장하기").assertIsDisplayed()
        composeRule.onNodeWithText("참여 페스티벌").assertDoesNotExist()
    }

    @Test
    fun `신규 저장 완료 화면은 완료 액션을 유지한다`() {
        composeRule.setContent {
            FestiCoreTheme {
                BearerProfileContent(state().copy(isEditMode = false, savedProfile = BearerFlagProfile("flag", "호락호락")))
            }
        }
        composeRule.onNodeWithText("깃발이 준비됐어요").assertIsDisplayed()
        composeRule.onNodeWithText("완료").assertIsDisplayed()
    }

    private fun state() = BearerProfileState(
        festivalId = "festival",
        flagId = "flag",
        isLoading = false,
        isEditMode = true,
        name = "호락호락",
        festivalName = "원더리벳 2026",
        tags = listOf(BearerFlagTag.Slam),
    )
    private fun capture(name: String) {
        val bitmap = composeRule.onNodeWithTag("screen").captureToImage().asAndroidBitmap()
        val output = File("build/reports/design-parity/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
