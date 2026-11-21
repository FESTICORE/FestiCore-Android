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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.festicore.ui.designkit.component.navigation.FCTabBar
import io.festicore.ui.designkit.component.navigation.FCTabBarTab
import io.festicore.ui.designkit.component.navigation.title
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

/** iOS `MainTabView` 껍데기. 선택한 탭은 화면을 다시 만들어도 유지한다. 탭 내용은 이후 단계에서 옮긴다. */
@Composable
fun MainTabScreen(modifier: Modifier = Modifier) {
    var selection by rememberSaveable { mutableStateOf(FCTabBarTab.Live) }
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            TabRoot(tab = selection)
        }
        FCTabBar(selection = selection, onSelectionChange = { selection = it })
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
