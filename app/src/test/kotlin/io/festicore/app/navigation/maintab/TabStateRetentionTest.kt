package io.festicore.app.navigation.maintab

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.festicore.ui.designkit.component.navigation.FCTabBarTab
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 탭마다 스택 항목을 탭 밖(`HomeWithCovers`)에서 만들고 고른 탭만 그리는 방식을 확인한다(이슈 #200).
 * 탭을 바꿔도 화면 ViewModel · 저장 상태가 남고, 스택에서 빠진 항목은 탭이 안 보여도 정리된다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TabStateRetentionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tab(title: String) = composeRule.onNode(hasText(title) and isSelectable())

    @Test
    fun `탭을 바꿔도 화면 ViewModel과 저장 상태가 남는다`() {
        val probe = Probe()
        composeRule.setContent { TwoTabs(probe = probe, liveStack = liveStackOf(TestRoute.Board("A"))) }
        composeRule.onNodeWithText("보드 A 누름 0").performClick()
        composeRule.onNodeWithText("보드 A 누름 1").assertExists()
        val first = probe.boards.getValue("A")

        tab("타임테이블").performClick()
        composeRule.onNodeWithText("타임테이블 화면").assertExists()
        tab("라이브").performClick()

        composeRule.onNodeWithText("보드 A 누름 1").assertExists()
        assertSame(first, probe.boards.getValue("A"))
        assertEquals(0, first.clearedCount)
    }

    @Test
    fun `안 보이는 탭 스택에서 빠진 항목은 바로 정리되고 다시 열면 새로 만든다`() {
        val probe = Probe()
        val liveStack = liveStackOf(TestRoute.Board("A"))
        composeRule.setContent { TwoTabs(probe = probe, liveStack = liveStack) }
        val first = probe.boards.getValue("A")

        tab("타임테이블").performClick()
        // 계정 변경 정리처럼 탭이 안 보일 때 스택이 바뀐다.
        composeRule.runOnIdle { liveStack.removeAt(liveStack.lastIndex) }
        composeRule.waitForIdle()
        assertEquals(1, first.clearedCount)

        composeRule.runOnIdle { liveStack.add(TestRoute.Board("A")) }
        tab("라이브").performClick()
        composeRule.onNodeWithText("보드 A 누름 0").assertExists()
        assertNotSame(first, probe.boards.getValue("A"))
    }

    private fun liveStackOf(top: TestRoute) = mutableStateListOf<NavKey>(TestRoute.List, top)

    @Composable
    private fun TwoTabs(probe: Probe, liveStack: List<NavKey>) {
        var selection by remember { mutableStateOf(FCTabBarTab.Live) }
        val liveEntries = rememberTestEntries(liveStack, probe)
        val timetableEntries = rememberTestEntries(listOf(TestRoute.Timetable), probe)
        MainTabScreen(
            selection = selection,
            onSelectionChange = { selection = it },
            onFlagTabRequested = {},
            liveContent = { onHidesTabBarChanged, _ ->
                // 스택이 쌓인 라이브 탭처럼 탭 바를 보인다.
                LaunchedEffect(Unit) { onHidesTabBarChanged(false) }
                NavDisplay(entries = liveEntries, onBack = {})
            },
            flagContent = { Text("깃발 흐름") },
            myContent = { Text("마이 흐름") },
            timetableContent = { NavDisplay(entries = timetableEntries, onBack = {}) },
        )
    }

    @Composable
    private fun rememberTestEntries(backStack: List<NavKey>, probe: Probe): List<NavEntry<NavKey>> = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<TestRoute.List> { Text("목록") }
                entry<TestRoute.Timetable> { Text("타임테이블 화면") }
                entry<TestRoute.Board> { key ->
                    val board: BoardViewModel = viewModel()
                    probe.boards[key.id] = board
                    var taps by rememberSaveable { mutableIntStateOf(0) }
                    Text("보드 ${key.id} 누름 $taps", modifier = Modifier.clickable { taps++ })
                }
            },
    )

    private class Probe {
        val boards = mutableMapOf<String, BoardViewModel>()
    }

    class BoardViewModel : ViewModel() {
        var clearedCount = 0
            private set

        override fun onCleared() {
            clearedCount++
        }
    }

    @Serializable
    private sealed interface TestRoute : NavKey {
        @Serializable data object List : TestRoute

        @Serializable data object Timetable : TestRoute

        @Serializable data class Board(val id: String) : TestRoute
    }
}
