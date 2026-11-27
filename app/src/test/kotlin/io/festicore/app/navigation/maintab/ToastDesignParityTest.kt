package io.festicore.app.navigation.maintab

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.festicore.domain.festival.entity.FestivalCrewState
import io.festicore.feature.festival.crew.CrewContent
import io.festicore.feature.festival.crew.CrewLoad
import io.festicore.feature.festival.crew.CrewState
import io.festicore.feature.festival.crew.CrewToast
import io.festicore.feature.festival.flag.FlagContent
import io.festicore.feature.festival.flag.FlagDirectoryState
import io.festicore.feature.festival.flag.FlagState
import io.festicore.feature.festival.flagdetail.FlagDetailContent
import io.festicore.feature.festival.flagdetail.FlagDetailContext
import io.festicore.feature.festival.flagdetail.FlagDetailState
import io.festicore.feature.festival.flagdetail.FlagDetailToast
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.navigation.FCTabBarTab
import io.festicore.ui.designkit.component.toast.FCToastHost
import io.festicore.ui.designkit.component.toast.FCToastItem
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.hours
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
class ToastDesignParityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `표시된 탭 바 위 토스트는 내비게이션 인셋을 다시 더하지 않는다`() = assertTabToastGap(hides = false, reserves = true)

    @Test
    fun `예약된 탭 바 위 토스트도 내비게이션 인셋을 다시 더하지 않는다`() = assertTabToastGap(hides = true, reserves = true)

    @Test
    fun `탭 바 자리가 없으면 토스트가 내비게이션 인셋을 적용한다`() = assertTabToastGap(hides = true, reserves = false)

    @Test
    fun `키보드가 열리면 표시된 탭 바 높이를 중복 적용하지 않는다`() = assertTabToastGap(hides = false, reserves = true, ime = 300)

    @Test
    fun `키보드가 열리면 예약된 탭 바 높이를 중복 적용하지 않는다`() = assertTabToastGap(hides = true, reserves = true, ime = 300)

    @Test
    fun `크루 토스트는 상태 버튼 바로 위 하단에 있고 버튼을 가리지 않는다`() {
        var selectedState: FestivalCrewState? = null
        composeRule.setContent {
            CrewContent(
                state = CrewState(festivalId = "festival", crewId = "crew", load = CrewLoad.Loaded, toast = CrewToast.Renamed),
                modifier = Modifier.testTag("crew"),
                onStateTapped = { selectedState = it },
            )
        }
        val capture = composeRule.onNodeWithTag("crew").captureToImage().asAndroidBitmap()
        val output = File("build/reports/design-parity/crew-bottom-toast.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { capture.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val root = composeRule.onNodeWithTag("crew").getUnclippedBoundsInRoot()
        val toast = composeRule.onNodeWithText("크루 이름을 바꿨어요").getUnclippedBoundsInRoot()
        val status = composeRule.onNodeWithText("내 상태 · 탭 한 번이면 크루원에게 공유돼요").getUnclippedBoundsInRoot()
        assertTrue("토스트는 화면 아래 절반에 있어야 한다", toast.top > (root.top + root.bottom) / 2)
        assertEquals("상태 패널 위 여백 16 + 패널 내부 위 여백 12", 28f, (status.top - toast.bottom).value, 1f)
        composeRule.onNodeWithText("내 상태 · 탭 한 번이면 크루원에게 공유돼요").assertIsDisplayed()
        composeRule.onNodeWithText("나 여기").assertIsDisplayed().performClick()
        assertEquals(FestivalCrewState.HERE, selectedState)
    }

    @Test
    fun `크루는 키보드 위 토스트에 상태 패널 높이를 중복 적용하지 않는다`() {
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            CrewContent(
                state = CrewState(festivalId = "festival", crewId = "crew", load = CrewLoad.Loaded, toast = CrewToast.Renamed),
                modifier = Modifier.testTag("crew"),
            )
        }
        composeRule.runOnIdle {
            view.dispatchApplyWindowInsets(
                WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                    .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 300))
                    .build(),
            )
        }
        val root = composeRule.onNodeWithTag("crew").getUnclippedBoundsInRoot()
        val toast = composeRule.onNodeWithText("크루 이름을 바꿨어요").getUnclippedBoundsInRoot()
        assertEquals("창 기준 키보드 위 P16", 316f, (root.bottom - toast.bottom).value, 1f)
    }

    @Test
    fun `깃발 목록 토스트는 시스템 내비게이션 위에 표시된다`() {
        assertScreenToastGap("안내") {
            FlagContent(state = FlagState(directory = FlagDirectoryState(toast = "안내")))
        }
    }

    @Test
    fun `깃발 상세 토스트는 시스템 내비게이션 위에 표시된다`() {
        assertScreenToastGap("신고를 접수했어요") {
            FlagDetailContent(state = FlagDetailState("flag", FlagDetailContext(), Instant.EPOCH, toast = FlagDetailToast.Reported))
        }
    }

    private fun assertScreenToastGap(message: String, content: @Composable () -> Unit) {
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            Box(Modifier.fillMaxSize().testTag("screen")) { content() }
        }
        composeRule.runOnIdle {
            view.dispatchApplyWindowInsets(
                WindowInsets.Builder().setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48)).build(),
            )
        }
        val root = composeRule.onNodeWithTag("screen").getUnclippedBoundsInRoot()
        val toast = composeRule.onNodeWithText(message).getUnclippedBoundsInRoot()
        assertEquals(64f, (root.bottom - toast.bottom).value, 1f)
    }

    private fun assertTabToastGap(
        hides: Boolean,
        reserves: Boolean,
        ime: Int = 0,
    ) {
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            MainTabScreen(
                modifier = Modifier.testTag("mainTab"),
                selection = FCTabBarTab.My,
                onSelectionChange = {},
                onFlagTabRequested = {},
                liveContent = { _, _ -> },
                flagContent = {},
                timetableContent = {},
                myContent = { onHidesTabBarChanged ->
                    LaunchedEffect(Unit) { onHidesTabBarChanged(hides, reserves) }
                    FCToastHost(
                        item = FCToastItem(R.drawable.ic_check, "저장했어요"),
                        onItemChange = {},
                        duration = 1.hours,
                        modifier = Modifier.fillMaxSize().testTag("content"),
                    ) { Box(Modifier.fillMaxSize()) }
                },
            )
        }
        composeRule.runOnIdle {
            view.dispatchApplyWindowInsets(
                WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                    .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, ime))
                    .build(),
            )
        }
        val content = composeRule.onNodeWithTag("content").getUnclippedBoundsInRoot()
        val toast = composeRule.onNodeWithText("저장했어요").getUnclippedBoundsInRoot()
        if (ime > 0) {
            val root = composeRule.onNodeWithTag("mainTab").getUnclippedBoundsInRoot()
            assertEquals("창 기준 키보드 위 P16", (ime + 16).toFloat(), (root.bottom - toast.bottom).value, 1f)
        } else {
            assertEquals(if (!hides || reserves) 16f else 64f, (content.bottom - toast.bottom).value, 1f)
        }
    }
}
