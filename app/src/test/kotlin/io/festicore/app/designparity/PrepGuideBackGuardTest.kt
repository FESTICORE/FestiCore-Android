package io.festicore.app.designparity

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.festicore.core.location.client.MapAppClient
import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.festival.packdetail.FestivalPackDetailUseCase
import io.festicore.feature.profile.packdetail.PackDetailLoad
import io.festicore.feature.profile.packdetail.PackDetailViewModel
import io.festicore.feature.profile.prepchecklist.PrepChecklistScreen
import io.festicore.ui.designkit.theme.FestiCoreTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PrepGuideBackGuardTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun pendingChecklistWriteBlocksBackBeforeNextCompositionReportsPending() {
        val release = CompletableDeferred<Unit>()
        val vm = PackDetailViewModel("festival", "축제", ChecklistUseCase(release), MapAppClient { false }, tracksChecklistWrites = true)
        var popped = 0
        var reportedBlocked = false
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            BackHandler { popped++ }
            FestiCoreTheme {
                PrepChecklistScreen(vm, onDone = {}, onLater = {}, onNavigationBlocked = { reportedBlocked = it }, onBack = { popped++ })
            }
        }
        composeRule.waitUntil { vm.container.stateFlow.value.load == PackDetailLoad.Loaded }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle { vm.onItemToggled("ticket") }
            composeRule.waitUntil { vm.container.stateFlow.value.pendingChecklistWrites == 1 }
            composeRule.runOnIdle {
                // No new Compose frame has published the pending value to the Coordinator yet.
                assertFalse(reportedBlocked)
                dispatcher.onBackPressed()
                assertEquals(0, popped)
            }
        } finally {
            release.complete(Unit)
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitUntil { vm.container.stateFlow.value.pendingChecklistWrites == 0 }
        composeRule.runOnIdle {
            dispatcher.onBackPressed()
            assertEquals(1, popped)
        }
    }

    @Test
    fun sameFrameChecklistTapThenBackCannotDiscardQueuedWrite() {
        val release = CompletableDeferred<Unit>()
        val vm = PackDetailViewModel("festival", "축제", ChecklistUseCase(release), MapAppClient { false }, tracksChecklistWrites = true)
        var popped = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            FestiCoreTheme { PrepChecklistScreen(vm, {}, {}, {}, onBack = { popped++ }) }
        }
        composeRule.waitUntil { vm.container.stateFlow.value.load == PackDetailLoad.Loaded }
        try {
            composeRule.runOnIdle {
                vm.onItemToggled("ticket")
                dispatcher.onBackPressed()
                assertEquals(0, popped)
            }
        } finally {
            release.complete(Unit)
        }
    }

    private class ChecklistUseCase(val release: CompletableDeferred<Unit>) : FestivalPackDetailUseCase {
        override suspend fun packDetail(festivalId: String) = FestivalPackDetail(
            pack = FestivalReceivedPack("festival", "축제", packVersion = 1),
            checklist = listOf(FestivalPackDetail.ChecklistItem("ticket", "티켓")),
        )
        override suspend fun packChanges(festivalId: String, fromVersion: Int): FestivalPackChanges = error("unused")
        override suspend fun updatePack(festivalId: String): FestivalPackDetail? = error("unused")
        override suspend fun deletePack(festivalId: String) = Unit
        override suspend fun checklist(festivalId: String) = FestivalChecklist()
        override suspend fun saveChecklist(checklist: FestivalChecklist, festivalId: String) {
            release.await()
        }
        override suspend fun autoDownloadOnWiFi() = true
        override suspend fun setAutoDownloadOnWiFi(isOn: Boolean) = Unit
    }
}
