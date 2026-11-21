package io.festicore.ui.designkit.component.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.divider.FCDivider
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val IconSize = 24.dp
private val TopInset = 10.dp

/** iOS `FCTabBar.Tab` */
enum class FCTabBarTab {
    Live,
    Timetable,
    Flag,
    My,
}

/** 탭 표시 이름. 탭 루트 화면 제목에도 쓴다. */
val FCTabBarTab.title: String
    get() =
        when (this) {
            FCTabBarTab.Live -> "라이브"
            FCTabBarTab.Timetable -> "타임테이블"
            FCTabBarTab.Flag -> "깃발"
            FCTabBarTab.My -> "마이"
        }

@DrawableRes
internal fun FCTabBarTab.icon(isSelected: Boolean): Int = when (this) {
    FCTabBarTab.Live -> if (isSelected) R.drawable.nav_home_active else R.drawable.nav_home_inactive
    FCTabBarTab.Timetable -> if (isSelected) R.drawable.nav_timetable_active else R.drawable.nav_timetable_inactive
    FCTabBarTab.Flag -> if (isSelected) R.drawable.nav_flag_active else R.drawable.nav_flag_inactive
    FCTabBarTab.My -> if (isSelected) R.drawable.nav_my_active else R.drawable.nav_my_inactive
}

/**
 * 하단 탭바. 배경은 아래 시스템 내비게이션 영역까지 칠하고, 위에 구분선을 둔다. 선택 상태는 들지 않는다.
 */
@Composable
fun FCTabBar(
    selection: FCTabBarTab,
    onSelectionChange: (FCTabBarTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(FestiCoreSemanticColor.backgroundNormal.color)) {
        // Figma S01 · S02 실측: 구분선→아이콘 10, 글자 아래는 안전 영역에 바로 붙는다.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(top = TopInset),
        ) {
            FCTabBarTab.entries.forEach { tab ->
                val isSelected = selection == tab
                val color = if (isSelected) FestiCoreSemanticColor.primaryNormal.color else FestiCoreSemanticColor.labelAssistive.color
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .selectable(
                                selected = isSelected,
                                interactionSource = null,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelectionChange(tab) },
                            ),
                    verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(tab.icon(isSelected)),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize),
                        colorFilter = ColorFilter.tint(color),
                    )
                    // iOS는 `festiCoreFont`만 쓰고 `festiCoreLineBox`를 쓰지 않는다(C1).
                    BasicText(
                        text = tab.title,
                        style =
                            (if (isSelected) FestiCoreTextStyle.baseCaption2Bold else FestiCoreTextStyle.baseCaption2Medium)
                                .textStyleWithoutLineBox
                                .copy(color = color),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        FCDivider(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth())
    }
}
