package io.festicore.app.navigation

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.festicore.ui.designkit.component.navigation.FCTabBarTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MainTabScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tab(title: String) = composeRule.onNode(hasText(title) and isSelectable())

    @Test
    fun `라이브 스택이 목록뿐이면 탭 바를 숨긴다`() {
        composeRule.setContent {
            HoistedMainTabScreen(liveContent = { onHidesTabBarChanged, _ ->
                LaunchedEffect(Unit) { onHidesTabBarChanged(true) }
                Text("라이브 목록")
            })
        }

        composeRule.onNodeWithText("라이브 목록").assertExists()
        composeRule.onAllNodesWithText("타임테이블").assertCountEquals(0)
    }

    @Test
    fun `라이브 스택이 쌓이면 탭 바가 보이고 탭을 바꿔 다시 만들어도 선택이 유지된다`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            HoistedMainTabScreen(liveContent = { onHidesTabBarChanged, _ ->
                LaunchedEffect(Unit) { onHidesTabBarChanged(false) }
                Text("라이브 보드")
            })
        }

        tab("라이브").assertIsSelected()
        composeRule.onNodeWithText("라이브 보드").assertExists()

        tab("타임테이블").performClick()
        tab("타임테이블").assertIsSelected()
        tab("라이브").assertIsNotSelected()
        composeRule.onNodeWithText("타임테이블 흐름").assertExists()

        restoration.emulateSavedInstanceStateRestore()
        tab("타임테이블").assertIsSelected()
    }

    @Test
    fun `라이브 흐름이 깃발 탭을 요청하면 깃발 탭으로 바꾼다`() {
        composeRule.setContent {
            HoistedMainTabScreen(liveContent = { onHidesTabBarChanged, onFlagTabRequested ->
                LaunchedEffect(Unit) { onHidesTabBarChanged(false) }
                Text("깃발 찾기", modifier = Modifier.clickable(onClick = onFlagTabRequested))
            })
        }

        composeRule.onNodeWithText("깃발 찾기").performClick()

        tab("깃발").assertIsSelected()
        tab("라이브").assertIsNotSelected()
    }

    /** iOS `FlagRoutingTests` `cardTapOpensDetailWithContext` · `backAndSwipeCloseDetail`: 깃발 탭은 목록 · 상세 모두 탭 바를 보인다. */
    @Test
    fun `깃발 탭은 깃발 흐름을 그리고 라이브 스택과 상관없이 탭 바를 보인다`() {
        composeRule.setContent {
            HoistedMainTabScreen(
                initial = FCTabBarTab.Flag,
                liveContent = { onHidesTabBarChanged, _ ->
                    LaunchedEffect(Unit) { onHidesTabBarChanged(true) }
                    Text("라이브 목록")
                },
            )
        }

        composeRule.onNodeWithText("깃발 흐름").assertExists()
        tab("깃발").assertIsSelected()
    }

    /** iOS `profileInformationRoutesHideTabBarUntilDismissed`: 마이 루트 · 팩 상세는 탭 바를 보인다. */
    @Test
    fun `마이 흐름이 숨기지 않으면 탭 바를 보인다`() {
        composeRule.setContent {
            HoistedMainTabScreen(initial = FCTabBarTab.My, liveContent = { _, _ -> }, myContent = { onHidesTabBarChanged ->
                LaunchedEffect(Unit) { onHidesTabBarChanged(false, true) }
                Text("마이 흐름")
            })
        }

        composeRule.onNodeWithText("마이 흐름").assertExists()
        tab("마이").assertIsSelected()
    }

    /** iOS `#557`: 설정 홈과 그 아래는 탭 바를 숨기고 자리도 비운다. */
    @Test
    fun `마이 흐름이 숨기고 자리를 비우라고 하면 탭 바가 없다`() {
        composeRule.setContent {
            HoistedMainTabScreen(initial = FCTabBarTab.My, liveContent = { _, _ -> }, myContent = { onHidesTabBarChanged ->
                LaunchedEffect(Unit) { onHidesTabBarChanged(true, false) }
                Text("설정")
            })
        }

        composeRule.onNodeWithText("설정").assertExists()
        composeRule.onAllNodesWithText("타임테이블").assertCountEquals(0)
    }

    /** iOS `shouldReserveTabBarSpace`: 팩 삭제 팝업으로만 숨기면 자리는 남기고 읽기 도구 · 누르기는 받지 않는다. */
    @Test
    fun `마이 팝업으로 숨기면 탭 바 자리만 남고 눌러도 탭이 바뀌지 않는다`() {
        composeRule.setContent {
            HoistedMainTabScreen(initial = FCTabBarTab.My, liveContent = { _, _ -> }, myContent = { onHidesTabBarChanged ->
                LaunchedEffect(Unit) { onHidesTabBarChanged(true, true) }
                Text("팩 상세")
            })
        }

        composeRule.onAllNodes(hasText("라이브") and isSelectable()).assertCountEquals(0)
        composeRule.onAllNodesWithText("타임테이블").assertCountEquals(0)
    }

    /** iOS `searchPushHidesTabBarAndBackRestoresIt`: 타임테이블 루트는 탭 바를 보인다. */
    @Test
    fun `타임테이블 흐름이 숨기지 않으면 탭 바를 보인다`() {
        composeRule.setContent {
            HoistedMainTabScreen(initial = FCTabBarTab.Timetable, liveContent = { _, _ -> })
        }

        composeRule.onNodeWithText("타임테이블 흐름").assertExists()
        tab("타임테이블").assertIsSelected()
    }

    /** iOS `MTC:101-104`: 검색 · 변경 목록 · 고르기 · 공연 시트가 있으면 탭 바를 숨긴다(자리 예약 없음). */
    @Test
    fun `타임테이블 흐름이 숨기라고 하면 탭 바가 없다`() {
        composeRule.setContent {
            HoistedMainTabScreen(initial = FCTabBarTab.Timetable, liveContent = { _, _ -> }, timetableContent = { onHidesTabBarChanged ->
                LaunchedEffect(Unit) { onHidesTabBarChanged(true) }
                Text("공연 검색")
            })
        }

        composeRule.onNodeWithText("공연 검색").assertExists()
        composeRule.onAllNodes(hasText("라이브") and isSelectable()).assertCountEquals(0)
    }

    /** iOS `schedulePickHidesTabBarUntilDismissed`: 다른 탭으로 가면 타임테이블 숨김과 상관없이 탭 바를 보인다. */
    @Test
    fun `타임테이블 숨김은 다른 탭에 영향을 주지 않는다`() {
        composeRule.setContent {
            HoistedMainTabScreen(initial = FCTabBarTab.Timetable, liveContent = { _, _ -> }, timetableContent = { onHidesTabBarChanged ->
                LaunchedEffect(Unit) { onHidesTabBarChanged(true) }
                Text("내 일정 고르기")
            }, onSelectionChangedTo = FCTabBarTab.Flag)
        }

        composeRule.onNodeWithText("깃발 흐름").assertExists()
        tab("깃발").assertIsSelected()
    }

    /** iOS `MTC:93`: 로그인 시트가 떠 있는 동안은 어느 탭이든 탭 바를 숨긴다. */
    @Test
    fun `로그인 시트가 떠 있으면 탭 바를 숨긴다`() {
        composeRule.setContent {
            HoistedMainTabScreen(initial = FCTabBarTab.Flag, isLoginSheetPresented = true, liveContent = { _, _ -> })
        }

        composeRule.onNodeWithText("깃발 흐름").assertExists()
        composeRule.onAllNodesWithText("타임테이블").assertCountEquals(0)
    }
}

/** 앱에서는 `MainTabCoordinator`가 탭 선택을 가진다. 여기서는 같은 일을 하는 저장 상태로 감싼다. */
@Composable
private fun HoistedMainTabScreen(
    liveContent: @Composable (onHidesTabBarChanged: (Boolean) -> Unit, onFlagTabRequested: () -> Unit) -> Unit,
    initial: FCTabBarTab = FCTabBarTab.Live,
    isLoginSheetPresented: Boolean = false,
    myContent: @Composable (onHidesTabBarChanged: (hides: Boolean, reservesSpace: Boolean) -> Unit) -> Unit = { Text("마이 흐름") },
    timetableContent: @Composable (onHidesTabBarChanged: (Boolean) -> Unit) -> Unit = { Text("타임테이블 흐름") },
    onSelectionChangedTo: FCTabBarTab? = null,
) {
    var selection by rememberSaveable { mutableStateOf(initial) }
    // 탭 바가 숨겨져 누를 수 없을 때 다른 탭으로 옮기는 상위(딥링크 · 다른 탭 요청)를 흉내 낸다.
    LaunchedEffect(onSelectionChangedTo) { onSelectionChangedTo?.let { selection = it } }
    MainTabScreen(
        selection = selection,
        onSelectionChange = { selection = it },
        onFlagTabRequested = { selection = FCTabBarTab.Flag },
        liveContent = liveContent,
        flagContent = { Text("깃발 흐름") },
        myContent = myContent,
        timetableContent = timetableContent,
        isLoginSheetPresented = isLoginSheetPresented,
    )
}
