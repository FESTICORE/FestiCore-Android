package io.festicore.app.designparity

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.timetable.entity.TimetableDay
import io.festicore.domain.timetable.entity.TimetableSet
import io.festicore.domain.timetable.entity.TimetableStage
import io.festicore.feature.festival.flag.FlagState
import io.festicore.feature.festival.flag.FlagViewState
import io.festicore.feature.festival.prepguide.FestivalPrepFlagsContent
import io.festicore.feature.profile.packdetail.PackDetailLoad
import io.festicore.feature.profile.packdetail.PackDetailState
import io.festicore.feature.profile.prepchecklist.PrepChecklistContent
import io.festicore.feature.timetable.schedulepick.SchedulePickContent
import io.festicore.feature.timetable.schedulepick.SchedulePickState
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 실제 Compose 출력. 원본 Figma 렌더 일치 판정은 별도다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrepGuideCaptureTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun scheduleLight() = capture("s15-schedule-light") { Schedule() }

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-mdpi")
    fun scheduleDark() = capture("s15-schedule-dark") { Schedule() }

    @Test fun flagsLight() = capture("s15-flags-light") { Flags() }

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-mdpi")
    fun flagsDark() = capture("s15-flags-dark") { Flags() }

    @Test fun checklistLight() = capture("s15-checklist-light") { Checklist() }

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-mdpi")
    fun checklistDark() = capture("s15-checklist-dark") { Checklist() }

    /** Figma 145:1549: 화면 16 + 카드 16 + 아이콘 16 + 간격 6 = 제목 x54. */
    @Test
    fun rulesTitleReservesWarningIconAndSixPointGap() {
        composeRule.setContent {
            FestiCoreTheme { Box(Modifier.fillMaxSize().testTag("screen")) { Checklist() } }
        }
        val screen = composeRule.onNodeWithTag("screen").getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("반입 금지 · 원더리벳 2026").getUnclippedBoundsInRoot()
        assertEquals(54f, (title.left - screen.left).value, 0.5f)
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            FestiCoreTheme { Box(Modifier.fillMaxSize().testTag("screen")) { content() } }
        }
        val bitmap = composeRule.onNodeWithTag("screen").captureToImage().asAndroidBitmap()
        val output = File("build/reports/design-parity/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Schedule() {
        val stages = listOf(TimetableStage("livet", "LIVET STAGE"), TimetableStage("wonder", "WONDER STAGE"))
        val days = listOf(
            TimetableDay(
                "day1",
                "11/20 금",
                Instant.ofEpochSecond(1_795_100_400),
                stages,
                listOf(
                    TimetableSet("saucy", "Saucy Dog", "livet", 930, 980),
                    TimetableSet("coldrain", "coldrain", "wonder", 1000, 1050),
                    TimetableSet("spyair", "SPYAIR", "livet", 1030, 1080),
                    TimetableSet("natori", "natori", "wonder", 1100, 1160),
                ),
            ),
            TimetableDay("day2", "11/21 토", Instant.ofEpochSecond(1_795_186_800), stages, emptyList()),
        )
        SchedulePickContent(
            SchedulePickState("festival", days = days, selectedSetIds = setOf("saucy", "spyair")),
            onDayTapped = {},
            onSetTapped = {},
            onNextTapped = {},
            onSkipTapped = {},
            onLaterTapped = {},
        )
    }

    @Composable
    private fun Flags() {
        val flags = listOf("슬램 깃발", "떼창 깃발", "차분한 깃발").mapIndexed { index, name ->
            FestivalLiveBoardSnapshot.Flag(
                "flag$index",
                name,
                "active",
                null,
                null,
                null,
                tags = listOf("slam"),
                followerCount = 12 + index,
            )
        }
        FestivalPrepFlagsContent(
            FlagState(
                viewState = FlagViewState.Loaded,
                snapshot = FestivalLiveBoardSnapshot("festival", emptyList(), flags.take(1), null, directoryFlags = flags),
            ),
            onTag = {},
            onFollow = { _, _, _ -> },
            onNext = {},
            onLater = {},
        )
    }

    @Composable
    private fun Checklist() {
        val detail = FestivalPackDetail(
            pack = FestivalReceivedPack("festival", "원더리벳 2026", packVersion = 1),
            checklist = listOf(
                FestivalPackDetail.ChecklistItem("ticket", "모바일 티켓", required = true),
                FestivalPackDetail.ChecklistItem("id", "신분증", required = true),
                FestivalPackDetail.ChecklistItem("battery", "보조 배터리"),
                FestivalPackDetail.ChecklistItem("earplug", "귀마개"),
            ),
            rules = listOf("유리병", "외부 음식", "전문 촬영 장비"),
        )
        PrepChecklistContent(
            PackDetailState(
                "festival",
                "원더리벳 2026",
                load = PackDetailLoad.Loaded,
                detail = detail,
                checklist = FestivalChecklist(checkedIds = setOf("ticket")),
            ),
            onToggle = {},
            onTextChanged = {},
            onAdd = {},
            onDone = {},
            onLater = {},
        )
    }
}
