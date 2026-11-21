package io.festicore.ui.designkit.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

@FCPreviews
@Composable
private fun FCPaginationPreview() {
    Column(
        modifier =
            Modifier
                .background(FestiCoreSemanticColor.staticInk.color)
                .padding(FestiCoreSpacing.paddingP24),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        (1..4).forEach { page -> FCPagination(current = page) }
    }
}

@FCPreviews
@Composable
private fun FCSegmentedPreview() {
    var selection by remember { mutableIntStateOf(0) }
    FCPreviewSurface {
        FCSegmented(selection = selection, onSelectionChange = { selection = it }, items = listOf("전체", "내 일정"))
    }
}

@FCPreviews
@Composable
private fun FCStepHeaderPreview() {
    FCPreviewSurface {
        FCStepHeader(step = 1)
        FCStepHeader(step = 2, onLater = {})
        FCStepHeader(step = 3)
    }
}

@FCPreviews
@Composable
private fun FCTabPreview() {
    var selection by remember { mutableIntStateOf(0) }
    FCPreviewSurface {
        FCTab(selection = selection, onSelectionChange = { selection = it }, items = listOf("1일차", "2일차", "3일차"))
    }
}

@FCPreviews
@Composable
private fun FCTabItemPreview() {
    FCPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16)) {
            FCTabItem("선택됨", isSelected = true)
            FCTabItem("기본", isSelected = false)
        }
    }
}

@FCPreviews
@Composable
private fun FCTabBarPreview() {
    var selection by remember { mutableStateOf(FCTabBarTab.Live) }
    FCPreviewSurface {
        FCTabBar(selection = selection, onSelectionChange = { selection = it })
    }
}

@FCPreviews
@Composable
private fun FCTopBarPreview() {
    var searchText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.background(FestiCoreSemanticColor.backgroundNormal.color),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        FCTopBar(FCTopBarStyle.Logo, showsBadge = true, onTrailingAction = {})
        FCTopBar(FCTopBarStyle.LogoBearer, badgeCount = 3, onTrailingAction = {}, onBearer = {})
        FCTopBar(FCTopBarStyle.Back, title = "라인업", onBack = {})
        FCTopBar(FCTopBarStyle.Large, title = "타임테이블")
        FCTopBar(
            FCTopBarStyle.Search(text = searchText, onTextChange = { searchText = it }),
            searchPlaceholder = "아티스트 검색",
            onCancel = {},
            onSearchSubmit = {},
        )
        FCTopBar(
            FCTopBarStyle.BackOnImage,
            modifier = Modifier.background(FestiCoreSemanticColor.accentPointOrange.color),
            title = "아티스트",
            shareUrl = "https://festicore.io",
            shareTitle = "아티스트",
            onBack = {},
        )
    }
}
