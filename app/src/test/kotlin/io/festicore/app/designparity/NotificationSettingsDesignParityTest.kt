package io.festicore.app.designparity
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.festicore.domain.festival.notificationsettings.NotificationSetting
import io.festicore.feature.profile.notification.NotificationSettingsContent
import io.festicore.feature.profile.notification.NotificationSettingsState
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h600dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationSettingsDesignParityTest {
    @get:Rule val composeRule = createComposeRule()
    private val values = listOf(
        NotificationSetting.FlagStatus,
        NotificationSetting.CrewStatus,
        NotificationSetting.CrewGather,
        NotificationSetting.SetReminders,
        NotificationSetting.Homecoming,
        NotificationSetting.PackUpdates,
    ).associateWith {
        true
    }

    @Test fun systemOffKeepsServerPreferencesEditableAndLastGroupReachable() {
        val changes = mutableListOf<Pair<NotificationSetting, Boolean>>()
        composeRule.setContent {
            FestiCoreTheme {
                NotificationSettingsContent(
                    NotificationSettingsState(
                        values = values + (NotificationSetting.OfficialNews to true),
                    ),
                    false,
                    onToggle = {
                            key,
                            value,
                        ->
                        changes +=
                            key to value
                    },
                )
            }
        }
        composeRule.onNodeWithText("기기 설정에서 알림이 꺼져 있어요 · 설정 열기").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("팔로우한 깃발 이동").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("공식 인스타그램 새 게시물").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(NotificationSetting.FlagStatus to false, NotificationSetting.OfficialNews to false), changes)
        }
    }

    @Test fun absentOfficialNewsIsHiddenAndTerminalStateHasNoRetry() {
        composeRule.setContent {
            FestiCoreTheme { NotificationSettingsContent(NotificationSettingsState(terminal = true, error = "기기 등록을 확인할 수 없어요"), true) }
        }
        composeRule.onNodeWithText("공식 인스타그램 새 게시물").assertDoesNotExist()
        composeRule.onNodeWithText("다시 시도").assertDoesNotExist()
        composeRule.onNodeWithText("기기 등록을 확인할 수 없어요").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-mdpi")
    fun captureLight() = capture("s13-notifications-light")

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-mdpi")
    fun captureDark() = capture("s13-notifications-dark")

    @Test fun undeclaredNotificationPermissionDoesNotOfferAnUnusableSettingsAction() {
        composeRule.setContent {
            FestiCoreTheme {
                NotificationSettingsContent(NotificationSettingsState(values = values), false, systemSettingsAvailable = false)
            }
        }
        composeRule.onNodeWithText("기기 설정에서 알림이 꺼져 있어요 · 설정 열기").assertDoesNotExist()
    }

    private fun capture(name: String) {
        composeRule.setContent {
            FestiCoreTheme {
                NotificationSettingsContent(NotificationSettingsState(values = values), true, Modifier.testTag("screen"))
            }
        }
        val bitmap = composeRule.onNodeWithTag("screen").captureToImage().asAndroidBitmap()
        val file = File("build/reports/design-parity/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
