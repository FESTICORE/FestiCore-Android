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
 * iOS `MainTabView` 껍데기. 선택한 탭([selection])은 `MainTabCoordinator`가 가지고(설계 P7), 탭마다 화면 상태(스크롤 위치 등)를 따로 보존한다.
 * 라이브 탭은 [liveContent](라이브 흐름)를 그린다. 라이브 흐름이 숨기라고 알리면(스택이 목록뿐 · 보드에 시트나 알림창) 탭 바를 숨기고 자리도 비운다
 * (iOS `MainTabCoordinator.shouldHideTabBar` · `shouldReserveTabBarSpace`). 표시 · 숨김은 애니메이션 없이 바로 바꾼다. 다른 탭 내용은 이후 단계에서 옮긴다.
 * 라이브 흐름이 깃발 탭을 요청하면(존 지도 "깃발을 팔로우하면…", iOS `flagTabRequested`) [onFlagTabRequested]로 알린다.
 */
@Composable
fun MainTabScreen(
    selection: FCTabBarTab,
    onSelectionChange: (FCTabBarTab) -> Unit,
    onFlagTabRequested: () -> Unit,
    liveContent: @Composable (onHidesTabBarChanged: (Boolean) -> Unit, onFlagTabRequested: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hidesLiveTabBar by rememberSaveable { mutableStateOf(true) }
    val tabStates = rememberSaveableStateHolder()
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            tabStates.SaveableStateProvider(selection.name) {
                when (selection) {
                    FCTabBarTab.Live -> liveContent({ hidesLiveTabBar = it }, onFlagTabRequested)
                    else -> TabRoot(tab = selection)
                }
            }
        }
        if (!(selection == FCTabBarTab.Live && hidesLiveTabBar)) {
            FCTabBar(selection = selection, onSelectionChange = onSelectionChange)
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
