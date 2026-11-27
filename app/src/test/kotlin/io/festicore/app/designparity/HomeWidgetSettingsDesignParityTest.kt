package io.festicore.app.designparity

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.festicore.feature.profile.settings.HomeWidgetSettingsContent
import io.festicore.feature.profile.settings.HomeWidgetSettingsScreen
import io.festicore.feature.profile.settings.HomeWidgetSettingsState
import io.festicore.feature.profile.settings.HomeWidgetSettingsViewModel
import io.festicore.feature.profile.settings.HomeWidgetStatus
import io.festicore.feature.profile.settings.SettingsHomeContent
import io.festicore.feature.profile.settings.SettingsHomeState
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
class HomeWidgetSettingsDesignParityTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `설정에서 홈 화면 위젯 안내를 열 수 있다`() {
        var opens = 0
        composeRule.setContent {
            FestiCoreTheme { SettingsHomeContent(SettingsHomeState(), appVersion = null, onHomeWidgetTapped = { opens++ }) }
        }
        composeRule.onNodeWithText("홈 화면 위젯").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, opens) }
    }

    @Test
    fun `미지원 런처는 수동 추가 안내를 보이고 동작하지 않는 버튼은 숨긴다`() {
        composeRule.setContent {
            FestiCoreTheme {
                HomeWidgetSettingsContent(HomeWidgetSettingsState(status = HomeWidgetStatus(false, false)), Modifier.testTag("screen"))
            }
        }
        composeRule.onNodeWithText("홈 화면의 빈 곳을 길게 누른 뒤 위젯 목록에서 FESTICORE를 찾아 추가해 주세요.").assertIsDisplayed()
        composeRule.onNodeWithText("홈 화면에 추가").assertDoesNotExist()
        capture("s13-widget-unsupported")
    }

    @Test
    fun `요청 후 실제 resume 조회만 설치와 제거를 반영한다`() {
        val owner = TestLifecycleOwner()
        val viewModel = HomeWidgetSettingsViewModel()
        var installed = false
        var reads = 0
        var requests = 0
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                FestiCoreTheme {
                    HomeWidgetSettingsScreen(
                        viewModel = viewModel,
                        modifier = Modifier.testTag("screen"),
                        readStatus = {
                            reads++
                            HomeWidgetStatus(true, installed)
                        },
                        requestPin = {
                            requests++
                            true
                        },
                        onBackRequested = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("아직 위젯을 추가하지 않았어요").assertIsDisplayed()
        capture("s13-widget-supported")
        composeRule.onNodeWithText("홈 화면에 추가").performClick()
        // Orbit side effect → app callback → result intent는 Compose idle과 별도로 완료된다.
        // native 캡처 뒤에도 실제 결과 UI가 렌더될 때까지 기다린 뒤 같은 표시 계약을 검사한다.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onNodeWithText("추가 요청을 보냈어요. 홈 화면에서 완료해 주세요.").isDisplayed()
        }
        composeRule.onNodeWithText("추가 요청을 보냈어요. 홈 화면에서 완료해 주세요.").assertIsDisplayed()
        composeRule.onNodeWithText("아직 위젯을 추가하지 않았어요").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, requests) }
        val readsBeforeResume = reads
        composeRule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            installed = true
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onNodeWithText("홈 화면에 위젯을 추가했어요").isDisplayed()
        }
        composeRule.onNodeWithText("홈 화면에 위젯을 추가했어요").assertIsDisplayed()
        composeRule.onNodeWithText("홈 화면에 추가").assertDoesNotExist()
        capture("s13-widget-installed")
        composeRule.runOnIdle { assertTrue(reads > readsBeforeResume) }
        composeRule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            installed = false
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onNodeWithText("아직 위젯을 추가하지 않았어요").isDisplayed()
        }
        composeRule.onNodeWithText("아직 위젯을 추가하지 않았어요").assertIsDisplayed()
        composeRule.onNodeWithText("홈 화면에 추가").assertIsDisplayed()
    }

    @Test
    fun pinConfirmationRefreshesInstalledStatusWithoutLifecycleTransition() {
        val changes = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1)
        var installed = false
        composeRule.setContent {
            FestiCoreTheme {
                HomeWidgetSettingsScreen(
                    viewModel = HomeWidgetSettingsViewModel(),
                    readStatus = { HomeWidgetStatus(true, installed) },
                    requestPin = { true },
                    onBackRequested = {},
                    installationChanges = changes,
                )
            }
        }
        composeRule.onNodeWithText("아직 위젯을 추가하지 않았어요").assertIsDisplayed()
        composeRule.runOnIdle {
            installed = true
            changes.tryEmit(Unit)
        }
        composeRule.waitUntil(timeoutMillis = 1000) { composeRule.onNodeWithText("홈 화면에 위젯을 추가했어요").isDisplayed() }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private fun capture(name: String) {
        val bitmap = composeRule.onNodeWithTag("screen").captureToImage().asAndroidBitmap()
        val output = File("build/reports/design-parity/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
