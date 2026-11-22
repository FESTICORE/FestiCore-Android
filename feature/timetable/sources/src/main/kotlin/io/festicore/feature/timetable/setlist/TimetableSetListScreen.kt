package io.festicore.feature.timetable.setlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.festicore.feature.timetable.TimetableSetListMode
import io.festicore.ui.designkit.component.list.FCSearchResultRow
import io.festicore.ui.designkit.component.list.FCSearchResultRowKind
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.overlay.FCSearchEmpty
import io.festicore.ui.designkit.component.selection.FCSearchField
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** 공연 검색 · 시간표 변경(iOS `TimetableSetListView`). 결과 행을 누르면 [onSetSelected], 뒤로는 [onClose]. */
@Composable
fun TimetableSetListScreen(
    viewModel: TimetableSetListViewModel,
    onSetSelected: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is TimetableSetListSideEffect.SetSelected -> onSetSelected(sideEffect.setId)
            TimetableSetListSideEffect.Close -> onClose()
        }
    }
    TimetableSetListContent(
        state = state,
        onQueryChanged = viewModel::onQueryChanged,
        onSetClicked = viewModel::onSetClicked,
        onBackClicked = viewModel::onBackClicked,
        modifier = modifier,
    )
}

@Composable
fun TimetableSetListContent(
    state: TimetableSetListState,
    onQueryChanged: (String) -> Unit,
    onSetClicked: (String) -> Unit,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSearch = state.mode == TimetableSetListMode.Search
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        FCTopBar(style = FCTopBarStyle.Back, title = if (isSearch) "공연 검색" else "시간표 변경", onBack = onBackClicked)
        if (isSearch) {
            FCSearchField(
                text = state.query,
                onTextChange = onQueryChanged,
                modifier = Modifier.padding(FestiCoreSpacing.paddingP16),
                placeholder = "아티스트, 무대 검색",
            )
        }
        val results = state.results
        if (results.isEmpty()) {
            FCSearchEmpty(
                query = state.query,
                message = if (isSearch) "아티스트 이름이나 무대로 다시 찾아보세요" else "확인된 시간표 변경이 없어요",
            )
        } else {
            // 끝까지 스크롤해도 하단에서 20만큼 띄운다(iOS `fcScrollBottomInset`).
            LazyColumn(contentPadding = PaddingValues(bottom = FestiCoreSpacing.paddingP20)) {
                results.forEach { day ->
                    items(day.sets, key = { it.id }) { set ->
                        FCSearchResultRow(
                            kind = FCSearchResultRowKind.Artist(initial = set.artist.take(1)),
                            title = set.artist,
                            meta = "${day.title} · ${set.timeRange}${set.startChange?.let { " · $it" }.orEmpty()}",
                            onClick = { onSetClicked(set.id) },
                        )
                    }
                }
            }
        }
    }
}
