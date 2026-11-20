package io.festicore.ui.designkit.component.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * iOS `FestiCoreDesignKitTests.swift` 16~246행의 시트 시나리오를 같은 순서 · 같은 기대 결과로 옮긴다.
 * iOS는 실제 시간(닫기 지연 3초, 렌더 100ms, 기한 전 2.5초, 결과 대기 3.5초)을 쓰고, 여기서는 같은 값을 테스트 시계로 진행한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FCSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val box = TestSheetBox()
    private var elapsedMillis = 0L

    @Before
    fun pauseClock() {
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun `지연된 닫기는 교체된 시트를 닫지 못하고 같은 시트만 닫는다`() {
        // iOS delayedDismissalCannotCloseReplacementSheet: completeDismissal(of: .login)은 .onboarding을 두고 .onboarding은 닫는다.
        setSheetHost(conditional = false)
        render()
        requestDismiss()
        replaceItem(TestSheet.Onboarding)
        render()
        waitResult()
        assertEquals(TestSheet.Onboarding, box.item)

        requestDismiss()
        waitResult()
        assertNull(box.item)
    }

    @Test
    fun `붙어 있는 시트를 닫는 중 다시 열면 열린 채로 남는다`() {
        setSheetHost(conditional = false)
        render()
        requestDismiss()
        render()
        replaceItem(null)
        render()
        replaceItem(TestSheet.Login)
        render()
        assertBeforeDeadline()

        waitResult()
        assertEquals(TestSheet.Login, box.item)
        assertSheetOnScreen()
    }

    @Test
    fun `조건부 시트를 닫는 중 빠르게 다시 열어도 예약된 닫기가 닫지 못한다`() {
        setSheetHost(conditional = true)
        render()
        requestDismiss()
        render()
        replaceItem(null)
        render()
        assertEquals(1, box.sheetDisappearCount)
        replaceItem(TestSheet.Login)
        render()
        assertBeforeDeadline()

        waitResult()
        assertEquals(TestSheet.Login, box.item)
        assertSheetOnScreen()

        requestDismiss()
        waitResult()
        assertNull(box.item)
    }

    @Test
    fun `시트가 빠지면 예약된 닫기가 취소된다`() {
        setSheetHost(conditional = true)
        render()
        requestDismiss()
        render()
        replaceItem(null)
        // 기한 전까지 실제로 빠졌는지 확인한다.
        while (box.modifierDisappearCount == 0 && elapsedMillis < PRE_DEADLINE_LIMIT_MILLIS) render()
        assertTrue(box.modifierDisappearCount >= 1)
        replaceItem(TestSheet.Login)
        render()
        assertBeforeDeadline()

        waitResult()
        assertEquals(TestSheet.Login, box.item)
    }

    @Test
    fun `아이템이 남은 채 시트를 빼면 닫기가 취소된다`() {
        setSheetHost(conditional = true)
        render()
        composeRule.runOnIdle {
            box.dismiss!!.invoke()
            box.isModifierMounted = false
        }
        render()
        assertEquals(1, box.modifierDisappearCount)
        assertEquals(TestSheet.Login, box.item)
        assertBeforeDeadline()

        waitResult()
        assertEquals(TestSheet.Login, box.item)
    }

    @Test
    fun `닫는 중 다른 아이템으로 바꾸면 교체된 시트가 남는다`() {
        setSheetHost(conditional = false)
        render()
        requestDismiss()
        render()
        replaceItem(TestSheet.Onboarding)
        render()
        assertBeforeDeadline()

        repeat(3) { render() }
        assertBeforeDeadline()
        assertSheetOnScreen()

        waitResult()
        assertEquals(TestSheet.Onboarding, box.item)
        assertSheetOnScreen()
    }

    @Test
    fun `닫기 예약 없이 교체하면 시트 위치가 그대로다`() {
        setSheetHost(conditional = false)
        repeat(5) { render() }
        val before = sheetTop()

        replaceItem(TestSheet.Onboarding)
        render()

        assertEquals(before, sheetTop())
    }

    @Test
    fun `시트를 띄워도 배경 위치는 그대로다`() {
        box.item = null
        setSheetHost(conditional = false)
        render()
        val before = composeRule.onNodeWithTag(BACKGROUND).getUnclippedBoundsInRoot()

        replaceItem(TestSheet.Login)
        repeat(5) { render() }

        assertEquals(before, composeRule.onNodeWithTag(BACKGROUND).getUnclippedBoundsInRoot())
        composeRule.onNodeWithTag(SHEET).assertExists()
    }

    @Test
    fun `지연된 닫기는 바뀌지 않은 시트를 닫는다`() {
        setSheetHost(conditional = false)
        render()
        requestDismiss()
        waitResult()
        assertNull(box.item)
    }

    @Test
    fun `바깥 시트와 화면 안쪽 시트 중 하나라도 뜨면 상위에 알린다`() {
        val presence = ModalPresenceBox()
        val presentation = FCSheetPresentation()
        composeRule.setContent {
            CompositionLocalProvider(LocalFCSheetPresentation provides presentation) {
                Box {
                    FCSheet(item = presence.inner, onDismissRequest = { presence.inner = null }) { BasicText("Inner") }
                }
                FCSheet(item = presence.outer, onDismissRequest = { presence.outer = null }) { BasicText("Outer") }
            }
        }
        render()
        assertFalse(presentation.isPresented)

        composeRule.runOnIdle { presence.inner = TestSheet.Login }
        render()
        assertTrue(presentation.isPresented)

        composeRule.runOnIdle {
            presence.inner = null
            presence.outer = TestSheet.Onboarding
        }
        render()
        assertTrue(presentation.isPresented)

        composeRule.runOnIdle { presence.outer = null }
        render()
        assertFalse(presentation.isPresented)
    }

    @Test
    fun `push된 화면 안 시트도 내비게이션 바깥에서 감지된다`() {
        // iOS는 NavigationStack의 push 목적지 안 시트를 본다. designkit은 내비게이션을 의존하지 않으므로 경로가 있을 때만 그리는 목적지로 같은 구조를 만든다.
        val presence = ModalPresenceBox()
        val presentation = FCSheetPresentation()
        composeRule.setContent {
            CompositionLocalProvider(LocalFCSheetPresentation provides presentation) {
                Box {
                    if (presence.path.isNotEmpty()) {
                        Box {
                            FCSheet(item = presence.inner, onDismissRequest = { presence.inner = null }) { BasicText("Pushed") }
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { presence.path = listOf(1) }
        render()
        assertFalse(presentation.isPresented)

        composeRule.runOnIdle { presence.inner = TestSheet.Login }
        render()
        assertTrue(presentation.isPresented)
    }

    @Test
    fun `닫을 수 있으면 닫기 접근성 동작이 있고 막으면 없다`() {
        var isDismissible by mutableStateOf(true)
        composeRule.setContent {
            FCSheet(item = box.item, onDismissRequest = { box.item = null }, isDismissible = isDismissible) { SheetTestContent(box) }
        }
        render()
        val closeAction = SemanticsMatcher("닫기 동작") { it.config.getOrNull(SemanticsActions.Dismiss)?.label == "닫기" }
        composeRule.onNode(closeAction).assertExists()

        composeRule.runOnIdle { isDismissible = false }
        render()
        composeRule.onNode(closeAction).assertDoesNotExist()
    }

    @Test
    fun `닫기를 막아도 시트 안 닫기 요청으로는 닫힌다`() {
        // iOS fcSheetDismiss는 닫기 막기와 관계없이 닫는다(막기는 딤 탭 · 끌기에만 걸린다).
        composeRule.setContent {
            CompositionLocalProvider(LocalFCSheetDismissalDelay provides DISMISSAL_DELAY) {
                FCSheet(item = box.item, onDismissRequest = { box.item = null }, isDismissible = false) { SheetTestContent(box) }
            }
        }
        render()
        requestDismiss()
        waitResult()
        assertNull(box.item)
    }

    @Test
    fun `고정 높이를 주면 내용 영역이 그 높이다`() {
        composeRule.setContent {
            FCSheet(item = box.item, onDismissRequest = {}, height = FCSheetHeight.Fixed(200.dp)) {
                Box(modifier = Modifier.fillMaxSize().testTag(FILLED))
            }
        }
        repeat(5) { render() }
        assertEquals(200.dp, composeRule.onNodeWithTag(FILLED).getUnclippedBoundsInRoot().height)
    }

    @Test
    fun `accessory는 시트 위에 함께 그려진다`() {
        composeRule.setContent {
            FCSheet(
                item = box.item,
                onDismissRequest = {},
                accessory = { BasicText("Toast", modifier = Modifier.testTag(ACCESSORY)) },
            ) { SheetTestContent(box) }
        }
        repeat(5) { render() }
        val accessory = composeRule.onNodeWithTag(ACCESSORY).getUnclippedBoundsInRoot()
        val sheet = composeRule.onNodeWithTag(SHEET).getUnclippedBoundsInRoot()
        assertTrue("accessory ${accessory.bottom}, 시트 내용 ${sheet.top}", accessory.bottom < sheet.top)
    }

    private fun setSheetHost(conditional: Boolean) {
        composeRule.setContent {
            CompositionLocalProvider(LocalFCSheetDismissalDelay provides DISMISSAL_DELAY) {
                if (conditional && (!box.isModifierMounted || box.item == null)) {
                    Box(modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        BasicText("Background", modifier = Modifier.testTag(BACKGROUND))
                        DisposableEffect(Unit) { onDispose { box.modifierDisappearCount++ } }
                        FCSheet(item = box.item, onDismissRequest = { box.item = null }) { SheetTestContent(box) }
                    }
                }
            }
        }
    }

    private fun render() {
        // 바로 앞의 상태 변경을 먼저 반영한 뒤 시계를 진행한다(iOS layoutIfNeeded 뒤 대기).
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(RENDER_WAIT_MILLIS)
        elapsedMillis += RENDER_WAIT_MILLIS
        composeRule.waitForIdle()
    }

    private fun waitResult() {
        // 바로 앞의 상태 변경을 먼저 반영한 뒤 시계를 진행한다(iOS layoutIfNeeded 뒤 대기).
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(RESULT_WAIT_MILLIS)
        elapsedMillis += RESULT_WAIT_MILLIS
        composeRule.waitForIdle()
    }

    // iOS처럼 닫기 요청 시점부터 시간을 잰다.
    private fun requestDismiss() {
        composeRule.runOnIdle { box.dismiss!!.invoke() }
        elapsedMillis = 0
    }

    private fun replaceItem(item: TestSheet?) {
        composeRule.runOnIdle { box.item = item }
    }

    private fun assertBeforeDeadline() {
        assertTrue("닫기 요청 뒤 ${elapsedMillis}ms", elapsedMillis < PRE_DEADLINE_LIMIT_MILLIS)
    }

    private fun assertSheetOnScreen() {
        composeRule.onNodeWithTag(SHEET).assertIsDisplayed()
    }

    private fun sheetTop() = composeRule.onNodeWithTag(SHEET).getUnclippedBoundsInRoot().top

    private companion object {
        val DISMISSAL_DELAY = 3.seconds
        const val PRE_DEADLINE_LIMIT_MILLIS = 2_500L
        const val RESULT_WAIT_MILLIS = 3_500L
        const val RENDER_WAIT_MILLIS = 100L
        const val BACKGROUND = "background"
        const val SHEET = "sheet"
        const val FILLED = "filled"
        const val ACCESSORY = "accessory"
    }
}

private enum class TestSheet { Login, Onboarding }

private class TestSheetBox {
    var item by mutableStateOf<TestSheet?>(TestSheet.Login)
    var isModifierMounted by mutableStateOf(true)
    var modifierDisappearCount = 0
    var dismiss: (() -> Unit)? = null
    var sheetDisappearCount = 0
}

private class ModalPresenceBox {
    var outer by mutableStateOf<TestSheet?>(null)
    var inner by mutableStateOf<TestSheet?>(null)
    var path by mutableStateOf(emptyList<Int>())
}

@Composable
private fun SheetTestContent(box: TestSheetBox) {
    val dismiss = LocalFCSheetDismiss.current
    SideEffect { box.dismiss = dismiss }
    DisposableEffect(Unit) { onDispose { box.sheetDisappearCount++ } }
    BasicText("Sheet", modifier = Modifier.testTag("sheet"))
}
