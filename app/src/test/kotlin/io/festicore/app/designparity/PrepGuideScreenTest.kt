package io.festicore.app.designparity

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.feature.festival.flag.FlagDirectoryState
import io.festicore.feature.festival.flag.FlagState
import io.festicore.feature.festival.flag.FlagTag
import io.festicore.feature.festival.flag.FlagViewState
import io.festicore.feature.festival.prepguide.FestivalPrepFlagsContent
import io.festicore.feature.profile.packdetail.PackDetailLoad
import io.festicore.feature.profile.packdetail.PackDetailState
import io.festicore.feature.profile.prepchecklist.PrepChecklistContent
import io.festicore.feature.timetable.schedulepick.SchedulePickContent
import io.festicore.feature.timetable.schedulepick.SchedulePickState
import io.festicore.ui.designkit.theme.FestiCoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S15 구현 후 추가한 렌더 회귀 검증. 선행 RED 증거로 사용하지 않는다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi", application = Application::class)
class PrepGuideScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 아직 제품 UI를 바꾸지 않은 회귀: 저장 중 단계 이동을 시각적으로도 막는다. */
    @Test
    fun savingScheduleDisablesSkipAndShowsPendingInsteadOfLater() {
        composeRule.setContent {
            FestiCoreTheme {
                SchedulePickContent(
                    state = SchedulePickState(festivalId = "festival", isSaving = true),
                    onDayTapped = {},
                    onSetTapped = {},
                    onNextTapped = {},
                    onSkipTapped = {},
                    onLaterTapped = {},
                )
            }
        }
        composeRule.onNodeWithText("Step 1. 내 일정 고르기").assertExists()
        composeRule.onNodeWithText("건너뛰기").assertIsNotEnabled()
        composeRule.onNodeWithText("나중에 할게요").assertDoesNotExist()
    }

    @Test
    fun failedFlagLoadStillAllowsSkipAndLater() {
        var next = 0
        var later = 0
        composeRule.setContent {
            FestiCoreTheme {
                FestivalPrepFlagsContent(
                    FlagState(viewState = FlagViewState.Failed),
                    onTag = {},
                    onFollow = { _, _, _ -> },
                    onNext = { next++ },
                    onLater = { later++ },
                )
            }
        }
        composeRule.onNodeWithText("만나보고 싶은 깃발이 있나요?").assertExists()
        composeRule.onNodeWithText("건너뛰기").performClick()
        composeRule.onNodeWithText("나중에 할게요").performClick()
        assertEquals(1, next)
        assertEquals(1, later)
    }

    @Test
    fun pendingFollowDisablesSkip() {
        composeRule.setContent {
            FestiCoreTheme {
                FestivalPrepFlagsContent(
                    FlagState(directory = FlagDirectoryState(pendingFollows = mapOf("flag" to 1))),
                    onTag = {},
                    onFollow = { _, _, _ -> },
                    onNext = {},
                    onLater = {},
                )
            }
        }
        composeRule.onNodeWithText("건너뛰기").assertIsNotEnabled()
        composeRule.onNodeWithText("나중에 할게요").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-xxhdpi")
    fun checklistRemainsInteractiveWhileNavigationIsPendingInDarkMode() {
        var toggled: String? = null
        composeRule.setContent {
            FestiCoreTheme {
                PrepChecklistContent(
                    PackDetailState(
                        festivalId = "festival",
                        title = "축제",
                        load = PackDetailLoad.Loaded,
                        checklist = FestivalChecklist(customItems = listOf(FestivalChecklist.CustomItem("water", "물"))),
                        pendingChecklistWrites = 1,
                    ),
                    onToggle = { toggled = it },
                    onTextChanged = {},
                    onAdd = {},
                    onDone = {},
                    onLater = {},
                )
            }
        }
        composeRule.onNodeWithText("1개 중 0개 챙겼어요").assertExists()
        composeRule.onNodeWithText("건너뛰기").assertIsNotEnabled()
        composeRule.onNodeWithText("물").performClick()
        assertEquals("water", toggled)
    }

    @Test
    fun filteredFlagsKeepTotalFollowCountAndUseExistingFollowCallback() {
        val followed = FestivalLiveBoardSnapshot.Flag("calm", "차분 깃발", "active", null, null, null, tags = listOf("calm"))
        val candidate = followed.copy(id = "slam", name = "슬램 깃발", tags = listOf("slam"), followerCount = 4)
        var selectedTag: FlagTag? = null
        var follow: Triple<String, Boolean, Int?>? = null
        composeRule.setContent {
            FestiCoreTheme {
                FestivalPrepFlagsContent(
                    FlagState(
                        viewState = FlagViewState.Loaded,
                        snapshot = FestivalLiveBoardSnapshot(
                            "festival",
                            emptyList(),
                            listOf(followed),
                            null,
                            directoryFlags = listOf(followed, candidate),
                        ),
                        directory = FlagDirectoryState(tag = FlagTag.Slam),
                    ),
                    onTag = { selectedTag = it },
                    onFollow = { id, value, count -> follow = Triple(id, value, count) },
                    onNext = {},
                    onLater = {},
                )
            }
        }
        composeRule.onNodeWithText("다음 · 1개 팔로우").assertExists()
        composeRule.onNodeWithText("차분 깃발").assertDoesNotExist()
        composeRule.onNodeWithText("슬램 깃발").performClick()
        assertEquals(null, follow)
        composeRule.onNodeWithText("팔로우").performClick()
        assertEquals(Triple("slam", true, 4), follow)
        composeRule.onNodeWithText("차분").performClick()
        assertEquals(FlagTag.Calm, selectedTag)
    }

    @Test
    fun checklistLastRowAndAddRemainReachableWithFixedDoneButton() {
        var added = 0
        composeRule.setContent {
            var text by remember { mutableStateOf("") }
            FestiCoreTheme {
                PrepChecklistContent(
                    PackDetailState(
                        festivalId = "festival",
                        title = "축제",
                        load = PackDetailLoad.Loaded,
                        checklist = FestivalChecklist(customItems = (1..20).map { FestivalChecklist.CustomItem("item$it", "준비물 $it") }),
                        newItemText = text,
                    ),
                    onToggle = {},
                    onTextChanged = { text = it },
                    onAdd = { added++ },
                    onDone = {},
                    onLater = {},
                )
            }
        }
        composeRule.onNodeWithText("준비물 20").performScrollTo().assertExists()
        composeRule.onNodeWithText("준비 완료").assertExists()
        composeRule.onNodeWithText("내 항목 추가").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("내 항목").performTextInput("모자")
        composeRule.onNodeWithText("추가").performClick()
        assertEquals(1, added)
    }

    @Test
    fun missingChecklistCanFinishWithoutPromisingReminder() {
        var finished = 0
        composeRule.setContent {
            FestiCoreTheme {
                PrepChecklistContent(
                    PackDetailState(festivalId = "festival", title = "축제", load = PackDetailLoad.Missing),
                    onToggle = {},
                    onTextChanged = {},
                    onAdd = {},
                    onDone = { finished++ },
                    onLater = {},
                )
            }
        }
        composeRule.onNodeWithText("준비 완료").performClick()
        assertEquals(1, finished)
        composeRule.onNodeWithText("D-1", substring = true).assertDoesNotExist()
    }
}
