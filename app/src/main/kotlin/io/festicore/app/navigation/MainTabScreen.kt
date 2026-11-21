package io.festicore.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.festicore.ui.designkit.component.navigation.FCTabBar
import io.festicore.ui.designkit.component.navigation.FCTabBarTab
import io.festicore.ui.designkit.component.navigation.title
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

/**
 * iOS `MainTabView` 껍데기. 선택한 탭은 화면을 다시 만들어도 유지하고, 탭마다 화면 상태(스크롤 위치 등)를 따로 보존한다.
 * 라이브 탭은 [liveContent](라이브 흐름)를 그린다. 라이브 스택이 첫 화면(축제 목록)뿐이면 탭 바를 숨기고 자리도 비운다
 * (iOS `MainTabCoordinator.shouldHideTabBar` · `shouldReserveTabBarSpace`의 `festivalState.routes.count == 1`). 다른 탭 내용은 이후 단계에서 옮긴다.
 */
@Composable
fun MainTabScreen(liveContent: @Composable (onAtRootChanged: (Boolean) -> Unit) -> Unit, modifier: Modifier = Modifier) {
    var selection by rememberSaveable { mutableStateOf(FCTabBarTab.Live) }
    var isLiveAtRoot by rememberSaveable { mutableStateOf(true) }
    val tabStates = rememberSaveableStateHolder()
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            tabStates.SaveableStateProvider(selection.name) {
                when (selection) {
                    FCTabBarTab.Live -> liveContent { isLiveAtRoot = it }
                    else -> TabRoot(tab = selection)
                }
            }
        }
        if (!(selection == FCTabBarTab.Live && isLiveAtRoot)) {
            FCTabBar(selection = selection, onSelectionChange = { selection = it })
        }
    }
}

@Composable
private fun TabRoot(tab: FCTabBarTab) {
    Text(
        text = tab.title,
        style = FestiCoreTextStyle.baseTitle2Bold.textStyle,
        color = FestiCoreSemanticColor.labelNormal.color,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(FestiCoreSpacing.marginM16),
    )
}
