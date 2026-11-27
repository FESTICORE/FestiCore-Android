package io.festicore.app.designparity

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import io.festicore.domain.festival.entity.FestivalFlagDetail
import io.festicore.feature.festival.flagdetail.FlagDetailContent
import io.festicore.feature.festival.flagdetail.FlagDetailContext
import io.festicore.feature.festival.flagdetail.FlagDetailState
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-mdpi")
class FlagDetailWidgetPinDesignParityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `팔로우 중인 깃발의 홈 위젯 대표 선택을 요청한다`() {
        val requests = mutableListOf<Boolean>()
        composeRule.setContent {
            FestiCoreTheme {
                FlagDetailContent(state = state(), onWidgetPinChanged = { requests += it })
            }
        }
        composeRule.onNodeWithContentDescription("홈 위젯 대표 깃발").assertIsOff().performClick()
        composeRule.runOnIdle { assertEquals(listOf(true), requests) }
        composeRule.onNodeWithContentDescription("홈 위젯 대표 깃발")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.runOnIdle { assertEquals(listOf(true, true), requests) }
    }

    @Test
    fun `성공으로 선택된 홈 위젯 대표 깃발은 켜진 상태다`() {
        composeRule.setContent { FestiCoreTheme { FlagDetailContent(state = state().copy(pinOverride = true)) } }
        composeRule.onNodeWithContentDescription("홈 위젯 대표 깃발").assertIsOn()
    }

    @Test
    fun `진행 중 오프라인에서는 대표 선택이 비활성화되고 로그아웃하면 사라진다`() {
        val state = mutableStateOf(state().copy(isPinPending = true))
        composeRule.setContent { FestiCoreTheme { FlagDetailContent(state = state.value) } }
        composeRule.onNodeWithContentDescription("홈 위젯 대표 깃발").assertIsNotEnabled()
        composeRule.runOnIdle { state.value = state.value.copy(isPinPending = false, isOffline = true) }
        composeRule.onNodeWithContentDescription("홈 위젯 대표 깃발").assertIsNotEnabled()
        composeRule.runOnIdle { state.value = state.value.copy(context = FlagDetailContext(hasSession = false)) }
        composeRule.onNodeWithContentDescription("홈 위젯 대표 깃발").assertDoesNotExist()
    }

    private fun state(): FlagDetailState {
        val now = Instant.parse("2026-11-26T03:00:00Z")
        val detail = FestivalFlagDetail(
            id = "flag", name = "호락호락", thumbnailUrl = null, photoUrl = null,
            tags = emptyList(), bio = null, followerCount = 100, isPlanVisible = false,
            planSetIds = emptyList(), homeZoneId = null, status = FestivalFlagDetail.Status.Active,
            zoneId = null, activity = null, statusUpdatedAt = now, isFollowing = true,
            isBearer = false, isHidden = false,
        )
        return FlagDetailState(
            flagId = detail.id,
            context = FlagDetailContext(hasSession = true),
            now = now,
            content = FlagDetailContent.Loaded(detail),
        )
    }
}
