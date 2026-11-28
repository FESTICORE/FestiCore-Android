package io.festicore.app.designparity

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import io.festicore.domain.festival.entity.FestivalFlagDetail
import io.festicore.feature.festival.flagdetail.FlagDetailContent
import io.festicore.feature.festival.flagdetail.FlagDetailContext
import io.festicore.feature.festival.flagdetail.FlagDetailFailure
import io.festicore.feature.festival.flagdetail.FlagDetailState
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class FlagDetailHeaderDesignParityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `스크롤한 히어로는 고정 헤더 아래까지 실제 표시된다`() = assertHeroOverlap()

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-mdpi")
    fun `다크 모드에서도 스크롤한 히어로는 고정 헤더 아래까지 표시된다`() = assertHeroOverlap()

    private fun assertHeroOverlap() {
        mount()
        val root = bounds("screen")
        val header = bounds("flagDetailHeader")
        assertEquals("주입된 상태바 24 + 상단바 52", 76f, header.height, 1f)
        assertEquals(root.top, header.top, 1f)
        assertEquals("정지 상태 히어로는 헤더 뒤에서 시작", header.bottom, bounds("flagDetailHero").top, 1f)
        composeRule.onNodeWithTag("flagDetailScroll").performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
            scroll(0f, header.height + 8f)
        }
        composeRule.waitForIdle()
        val hero = bounds("flagDetailHero")
        val scroll = bounds("flagDetailScroll")
        assertEquals("헤더 위치 고정", header, bounds("flagDetailHeader"))
        assertTrue("스크롤 viewport가 헤더까지 포함해야 함: $scroll / $header", scroll.top <= header.top)
        assertEquals("클립된 히어로의 상단은 화면 상단까지 올라온다", root.top, hero.top, 1f)
        assertTrue("클립된 히어로와 헤더가 양의 면적으로 겹쳐야 함", hero.overlaps(header))
        saveScreenshot(
            composeRule.onNodeWithTag("screen").captureToImage().asAndroidBitmap(),
            "s05-overlay-${javaClass.simpleName}-${android.content.res.Resources.getSystem().configuration.uiMode}",
        )
    }

    @Test
    fun `헤더 위 포인터는 뒤로 공유만 누르고 본문 드래그와 분리된다`() {
        var back = 0
        var share = 0
        var follow = 0
        mount(onBack = { back++ }, onShare = { share++ }, onFollow = { follow++ })
        scrollBy(84f)
        composeRule.onNodeWithContentDescription("뒤로").performTouchInput { click() }
        composeRule.onNodeWithContentDescription("공유").performTouchInput { click() }
        assertEquals(1, back)
        assertEquals(1, share)
        val before = scrollOffset()
        composeRule.onNodeWithTag("flagDetailHeader").performTouchInput { swipeUp() }
        assertEquals(before, scrollOffset(), 1f)
        composeRule.onNodeWithTag("flagDetailScroll").performTouchInput { swipeUp() }
        assertTrue(scrollOffset() > before)
        assertEquals(0, follow)
    }

    @Test
    fun `헤더 빈 영역을 누르면 그 뒤 팔로우가 실행되지 않는다`() {
        var followed = 0
        var back = 0
        var share = 0
        mount(onFollow = { followed++ }, onBack = { back++ }, onShare = { share++ })
        val follow = composeRule.onNodeWithText("팔로우")
        val initial = follow.fetchSemanticsNode().boundsInRoot
        scrollBy(initial.center.y - 40f)
        val covered = follow.fetchSemanticsNode().boundsInRoot
        assertTrue(covered.center.y < bounds("flagDetailHeader").bottom)
        composeRule.onNodeWithTag("screen").performTouchInput { click(Offset(covered.left + 2f, covered.center.y)) }
        assertEquals(0, followed)
        assertEquals(0, back)
        assertEquals(0, share)
        val header = composeRule.onNodeWithTag("flagDetailHeader").fetchSemanticsNode().config
        assertTrue(header[androidx.compose.ui.semantics.SemanticsProperties.IsTraversalGroup])
        assertEquals(-1f, header[androidx.compose.ui.semantics.SemanticsProperties.TraversalIndex], 0f)
    }

    @Test
    fun `헤더 아래에 가려진 팔로우 버튼은 키보드 포커스로 드러난다`() {
        var followed = 0
        mount(onFollow = { followed++ })
        val follow = composeRule.onNodeWithText("팔로우")
        val initial = follow.fetchSemanticsNode().boundsInRoot
        scrollBy(initial.top - 30f)
        assertTrue(follow.fetchSemanticsNode().boundsInRoot.top < bounds("flagDetailHeader").bottom)
        composeRule.runOnIdle { assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard)) }
        follow.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        composeRule.waitForIdle()
        follow.assertIsFocused()
        assertTrue(follow.fetchSemanticsNode().boundsInRoot.top >= bounds("flagDetailHeader").bottom - 1f)
        follow.performTouchInput { click() }
        assertEquals(1, followed)
    }

    @Test
    fun `이미 보이는 팔로우에 포커스를 주어도 스크롤은 움직이지 않는다`() {
        mount()
        val before = scrollOffset()
        composeRule.runOnIdle { assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard)) }
        val follow = composeRule.onNodeWithText("팔로우")
        follow.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        composeRule.waitForIdle()
        follow.assertIsFocused()
        assertEquals(before, scrollOffset(), 1f)
    }

    @Test
    fun `오프라인 배지의 높이 변화와 끝단 클램프는 스크롤 범위에 따른다`() {
        val state = mutableStateOf(loadedState())
        mount(state = { state.value })
        scrollBy(120f)
        val normalHeader = bounds("flagDetailHeader").height
        val normalOffset = scrollOffset()
        val normalTop = rowTop(0)
        composeRule.runOnIdle { state.value = state.value.copy(isOffline = true, lastLoadedAt = state.value.now) }
        val offlineHeader = bounds("flagDetailHeader").height
        val badge = offlineHeader - normalHeader
        assertTrue(badge > 0f)
        assertEquals(badge, rowTop(0) - normalTop, 1f)
        assertEquals(normalOffset, scrollOffset(), 1f)
        composeRule.runOnIdle { state.value = state.value.copy(isOffline = false) }
        assertEquals(normalHeader, bounds("flagDetailHeader").height, 1f)
        assertEquals(normalTop, rowTop(0), 1f)
        assertEquals(normalOffset, scrollOffset(), 1f)
        composeRule.runOnIdle { state.value = state.value.copy(isOffline = true) }
        scrollBy(100000f)
        val oldOffset = scrollOffset()
        val offlineMax = scrollMax()
        val oldTop = rowTop(19)
        composeRule.runOnIdle { state.value = state.value.copy(isOffline = false) }
        assertEquals(offlineMax - badge, scrollMax(), 1f)
        assertEquals(minOf(oldOffset, scrollMax()), scrollOffset(), 1f)
        assertEquals(-badge - (scrollOffset() - oldOffset), rowTop(19) - oldTop, 1f)
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-mdpi")
    fun `오프라인 다크 헤더 배경은 아래 본문이 이동해도 변하지 않는다`() {
        mount(state = { loadedState().let { it.copy(isOffline = true, lastLoadedAt = it.now) } })
        val before = composeRule.onNodeWithTag("screen").captureToImage().asAndroidBitmap()
        val headerHeight = bounds("flagDetailHeader").height
        assertNotEquals("샘플 아래 히어로는 헤더 배경과 달라야 한다", before.getPixel(2, 25), before.getPixel(2, headerHeight.toInt() + 35))
        assertNotEquals("배지 아래 히어로도 대조 색이어야 한다", before.getPixel(2, 80), before.getPixel(2, headerHeight.toInt() + 90))
        scrollBy(headerHeight + 10f)
        val after = composeRule.onNodeWithTag("screen").captureToImage().asAndroidBitmap()
        assertEquals(before.getPixel(2, 25), after.getPixel(2, 25))
        assertEquals(before.getPixel(2, 80), after.getPixel(2, 80))
        saveScreenshot(after, "s05-dark-offline-overlay")
    }

    @Test
    fun `스크롤 복원은 헤더 여백이 포함된 끝단 위치를 유지한다`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            FestiCoreTheme { FlagDetailContent(state = loadedState()) }
        }
        scrollBy(100000f)
        val before = scrollOffset()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertEquals(before, scrollOffset(), 1f)
    }

    @Test
    fun `로딩과 오류는 헤더 아래에 있고 공유는 없다`() {
        val state = mutableStateOf(loadedState().copy(content = FlagDetailContent.Loading))
        var retry = 0
        var back = 0
        mount(state = { state.value }, onBack = { back++ }, onRetry = { retry++ })
        assertTrue(bounds("festivalFlagDetailSkeleton").top >= bounds("flagDetailHeader").bottom)
        composeRule.onNodeWithContentDescription("공유").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("뒤로").performTouchInput { click() }
        assertEquals(1, back)
        composeRule.runOnIdle { state.value = state.value.copy(content = FlagDetailContent.Failed(FlagDetailFailure.NotFound)) }
        assertTrue(composeRule.onNodeWithText("깃발을 찾을 수 없어요").fetchSemanticsNode().boundsInRoot.top >= bounds("flagDetailHeader").bottom)
        composeRule.runOnIdle { state.value = state.value.copy(content = FlagDetailContent.Failed(FlagDetailFailure.Unavailable)) }
        assertTrue(composeRule.onNodeWithText("다시 시도").fetchSemanticsNode().boundsInRoot.top >= bounds("flagDetailHeader").bottom)
        composeRule.onNodeWithText("다시 시도").performTouchInput { click() }
        assertEquals(1, retry)
    }

    private fun saveScreenshot(bitmap: Bitmap, name: String) {
        val output = File("build/reports/design-parity/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private lateinit var inputModeManager: InputModeManager

    private fun mount(
        state: () -> FlagDetailState = { loadedState() },
        onBack: () -> Unit = {},
        onShare: () -> Unit = {},
        onFollow: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            inputModeManager = LocalInputModeManager.current
            FestiCoreTheme {
                Box(Modifier.fillMaxSize().testTag("screen")) {
                    FlagDetailContent(
                        state = state(),
                        onBack = onBack,
                        onShareTapped = onShare,
                        onFollowTapped = onFollow,
                        onRetryTapped = onRetry,
                    )
                }
            }
        }
        composeRule.runOnIdle {
            view.dispatchApplyWindowInsets(
                WindowInsets.Builder().setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 24, 0, 0)).build(),
            )
        }
        composeRule.waitForIdle()
    }

    private fun scrollBy(delta: Float) {
        composeRule.onNodeWithTag("flagDetailScroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta) }
        composeRule.waitForIdle()
    }

    private fun scrollOffset(): Float = composeRule.onNodeWithTag("flagDetailScroll").fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()

    private fun rowTop(index: Int): Float = composeRule.onNodeWithText("아티스트 $index").fetchSemanticsNode().boundsInRoot.top

    private fun scrollMax(): Float = composeRule.onNodeWithTag("flagDetailScroll").fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].maxValue()

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun loadedState(): FlagDetailState {
        val now = Instant.parse("2026-11-26T03:00:00Z")
        val performances = (0 until 20).associate { index ->
            "set-$index" to FlagDetailContext.Performance(
                artist = "아티스트 $index",
                stageName = "메인 스테이지",
                startsAt = now.plusSeconds(index * 3600L),
                endsAt = now.plusSeconds((index + 1) * 3600L),
            )
        }
        val detail = FestivalFlagDetail(
            id = "flag",
            name = "호락호락",
            thumbnailUrl = null,
            photoUrl = null,
            tags = listOf("slam"),
            bio = "같이 즐겨요",
            followerCount = 100,
            isPlanVisible = true,
            planSetIds = performances.keys.toList(),
            homeZoneId = null,
            status = FestivalFlagDetail.Status.Active,
            zoneId = null,
            activity = null,
            statusUpdatedAt = now,
            isFollowing = false,
            isBearer = false,
            isHidden = false,
        )
        return FlagDetailState(
            flagId = detail.id,
            context = FlagDetailContext(performances = performances, hasSession = true),
            now = now,
            content = FlagDetailContent.Loaded(detail),
        )
    }
}
