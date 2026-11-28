package io.festicore.app.designparity

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.festicore.domain.festival.entity.FestivalCrewInvite
import io.festicore.feature.festival.crew.CrewJoinContent
import io.festicore.feature.festival.crew.CrewJoinLoad
import io.festicore.feature.festival.crew.CrewJoinState
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-mdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CrewInviteLargeFontTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `큰 글꼴에도 초대 만료 날짜와 시각이 잘리지 않는다`() {
        val now = Instant.parse("2026-11-07T12:00:00Z")
        val invite = FestivalCrewInvite(
            code = "TEST12",
            crewName = "크루",
            festivalId = "fixture",
            festivalName = "페스티벌",
            inviterNickname = "초대자",
            memberCount = 5,
            expiresAt = now.plusSeconds(172800),
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                FestiCoreTheme {
                    CrewJoinContent(CrewJoinState(code = invite.code, hasSession = true, invite = invite, load = CrewJoinLoad.Loaded), now)
                }
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText("11/9(월) 21:00", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        assertFalse(results.single().hasVisualOverflow)
    }
}
