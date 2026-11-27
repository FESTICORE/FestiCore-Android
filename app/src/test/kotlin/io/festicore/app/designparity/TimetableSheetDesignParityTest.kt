package io.festicore.app.designparity

import android.app.Application
import android.graphics.Insets
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import io.festicore.domain.timetable.entity.TimetableInterval
import io.festicore.domain.timetable.entity.TimetableSetDetail
import io.festicore.domain.timetable.entity.TimetableSetOverlap
import io.festicore.feature.timetable.main.TimetableContent
import io.festicore.feature.timetable.main.TimetableContentStatus
import io.festicore.feature.timetable.main.TimetableState
import io.festicore.feature.timetable.setdetail.TimetableSetDetailContent
import io.festicore.feature.timetable.setdetail.TimetableSetDetailState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp")
class TimetableSheetDesignParityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `토스트는 일정 스위치 아래에 있고 표시와 시간 만료에도 스위치 위치가 같다`() {
        val state = mutableStateOf(sheet())
        composeRule.setContent {
            Screen(state.value, onToggle = {
                val scheduled = !state.value.content.detail.isScheduled
                state.value = state.value.copy(
                    content = state.value.content.copy(detail = state.value.content.detail.copy(isScheduled = scheduled)),
                    isScheduleToastVisible = scheduled,
                )
            })
        }
        val switch = composeRule.onNodeWithContentDescription("내 일정에 넣기", useUnmergedTree = true)
        val before = switch.fetchSemanticsNode().boundsInRoot
        switch.performClick()
        val toast = composeRule.onNodeWithText("내 일정에 추가됐어요")
        toast.assertIsDisplayed()
        assertTrue("토스트는 일정 동작 아래에 있어야 한다", toast.fetchSemanticsNode().boundsInRoot.top >= before.bottom)
        assertEquals(before, switch.fetchSemanticsNode().boundsInRoot)
        switch.performClick()
        toast.assertDoesNotExist()
        assertEquals(before, switch.fetchSemanticsNode().boundsInRoot)
        switch.performClick()
        toast.assertIsDisplayed()
        composeRule.runOnIdle { state.value = state.value.copy(isScheduleToastVisible = false) }
        toast.assertDoesNotExist()
        assertEquals(before, switch.fetchSemanticsNode().boundsInRoot)
    }

    @Test
    @Config(qualifiers = "w360dp-h480dp")
    fun `작은 화면에서 사진 겹침 메모 출처가 있어도 스크롤해서 일정 변경 후 닫는다`() {
        val state = mutableStateOf<TimetableSetDetailState?>(sheet(rich = true).copy(isScheduleToastVisible = true))
        var toggles = 0
        composeRule.setContent { Screen(state.value, onToggle = { toggles++ }, onDismiss = { state.value = null }) }
        composeRule.onNodeWithContentDescription("내 일정에 넣기", useUnmergedTree = true).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, toggles)
        composeRule.onNode(
            SemanticsMatcher("닫기 동작") {
                it.config.getOrNull(SemanticsActions.Dismiss)?.label == "닫기"
            },
        ).performSemanticsAction(SemanticsActions.Dismiss) { it() }
        // FCSheet는 닫기 애니메이션과 300ms 지연 뒤에 부모의 상태를 비운다.
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertNull(state.value) }
        composeRule.onNodeWithContentDescription("내 일정에 넣기", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("라임", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("내 일정에 추가됐어요").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h480dp")
    fun `작은 화면에서 내용 스크롤을 맨 위로 되돌린 뒤 아래로 끌면 시트가 닫힌다`() {
        val state = mutableStateOf<TimetableSetDetailState?>(sheet(rich = true).copy(isScheduleToastVisible = true))
        var dismissals = 0
        composeRule.setContent {
            Screen(state.value, onToggle = {}, onDismiss = {
                dismissals++
                state.value = null
            })
        }
        composeRule.onNodeWithContentDescription("내 일정에 넣기", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val content = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        assertTrue(content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        content.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -10_000f) }
        composeRule.waitForIdle()
        assertEquals(0f, content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0f)
        // 스크롤 내용 위의 실제 제스처가 바텀시트의 중첩 스크롤로 전달되어야 한다.
        content.performTouchInput { swipeDown() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, dismissals)
            assertNull(state.value)
        }
        composeRule.onNodeWithContentDescription("내 일정에 넣기", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("라임", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("내 일정에 추가됐어요").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h480dp-mdpi")
    fun `작은 화면에서 일정 추가 토스트 전체가 내비게이션 안전 영역 안에 보인다`() {
        val state = mutableStateOf(sheet(rich = true))
        composeRule.setContent {
            Screen(state.value, onToggle = { state.value = state.value.copy(isScheduleToastVisible = true) })
        }
        composeRule.runOnIdle {
            val windows = WindowInspector.getGlobalWindowViews()
            assertTrue("시트 창이 있어야 한다", windows.size >= 2)
            windows.forEach { view ->
                view.dispatchApplyWindowInsets(
                    WindowInsets.Builder().setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48)).build(),
                )
            }
        }
        composeRule.onNodeWithContentDescription("내 일정에 넣기", useUnmergedTree = true).performScrollTo().performClick()
        val toast = composeRule.onNodeWithText("내 일정에 추가됐어요")
        toast.assertIsDisplayed()
        val toastBounds = toast.getUnclippedBoundsInRoot()
        val root = generateSequence(toast.fetchSemanticsNode()) { it.parent }.last().boundsInRoot
        assertTrue("토스트 전체가 창 안에 있어야 한다", toastBounds.top.value >= root.top && toastBounds.bottom.value <= root.bottom)
        // 기존 FCSheet는 배경 바깥과 안쪽에 각각 navigationBars 여백을 둔다.
        assertEquals(16f, root.bottom - 48f * 2 - toastBounds.bottom.value, 1f)
    }

    @Composable
    private fun Screen(
        sheet: TimetableSetDetailState?,
        onToggle: () -> Unit,
        onDismiss: () -> Unit = {},
    ) {
        TimetableContent(
            state = TimetableState(contentStatus = TimetableContentStatus.NoFestival, setDetail = sheet),
            onSearchClicked = {}, onDaySelected = {}, onRetryClicked = {}, onNotifyClicked = {},
            onMineOnlyToggled = {}, onSchedulePickClicked = {}, onChangeBannerClicked = {},
            onSetClicked = {}, onZoomChanged = {}, onScheduleToggled = onToggle, onSetDetailDismissed = onDismiss,
        )
    }

    private fun sheet(rich: Boolean = false) = TimetableSetDetailState(
        setId = "lime",
        content = TimetableSetDetailContent(
            detail = TimetableSetDetail(
                artist = "라임",
                stage = "메인 스테이지",
                time = "17:00–17:50",
                status = TimetableSetDetail.Status.Upcoming(30),
                isScheduled = false,
                imageUrl = if (rich) "https://example.invalid/artist.png" else null,
                memo = if (rich) "앞에서 함께 공연을 즐겨요" else null,
                overlap = if (rich) TimetableSetOverlap("버드", "원더 스테이지", "17:00–17:30", 30) else null,
            ),
            interval = TimetableInterval(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600)),
            source = "공식 타임테이블",
            isScheduleLoading = false,
            isScheduleLimitReached = false,
        ),
    )
}
