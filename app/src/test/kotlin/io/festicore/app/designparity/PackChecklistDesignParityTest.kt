package io.festicore.app.designparity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.feature.profile.packdetail.PackDetailContent
import io.festicore.feature.profile.packdetail.PackDetailLoad
import io.festicore.feature.profile.packdetail.PackDetailState
import io.festicore.feature.profile.packdetail.PackDetailTab
import io.festicore.ui.designkit.theme.FestiCoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PackChecklistDesignParityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `진행도는 화면 여백과 카드 내부 여백을 한 번씩만 적용한다`() {
        val detail = FestivalPackDetail(
            pack = FestivalReceivedPack(id = "festival", name = "페스티벌", packVersion = 1),
            checklist = listOf(FestivalPackDetail.ChecklistItem(id = "water", label = "물")),
        )
        composeRule.setContent {
            FestiCoreTheme {
                Box(Modifier.requiredSize(393.dp, 852.dp).testTag("screen")) {
                    PackDetailContent(
                        PackDetailState(
                            festivalId = "festival",
                            title = "페스티벌",
                            tab = PackDetailTab.Checklist,
                            load = PackDetailLoad.Loaded,
                            detail = detail,
                        ),
                    )
                }
            }
        }
        val screen = composeRule.onNodeWithTag("screen").getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("1개 중 0개 챙겼어요", useUnmergedTree = true).getUnclippedBoundsInRoot()

        // Figma 313:16011: 화면 16 + 진행도 컴포넌트 내부 16.
        assertEquals(32f, (title.left - screen.left).value, 0.5f)
    }
}
