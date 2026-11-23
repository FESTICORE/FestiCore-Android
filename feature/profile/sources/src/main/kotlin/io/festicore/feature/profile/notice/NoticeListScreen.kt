package io.festicore.feature.profile.notice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.list.FCListRow
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

private val SkeletonRowHeight = 56.dp
private const val SKELETON_ROW_COUNT = 4
internal val NoticeDividerHeight = 1.dp

/**
 * 공지 목록(iOS `NoticeListView`). 뒤로 · 상세 열기는 콜백으로만 알리고 Coordinator가 정한다(T1).
 * 처음 나타날 때만 읽는다(iOS `onAppear`, `Idle`일 때만).
 */
@Composable
fun NoticeListScreen(
    viewModel: NoticeListViewModel,
    onBackRequested: () -> Unit,
    onNoticeDetailRequested: (id: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            NoticeListSideEffect.BackRequested -> onBackRequested()
            is NoticeListSideEffect.NoticeDetailRequested -> onNoticeDetailRequested(sideEffect.id, sideEffect.title)
        }
    }
    LaunchedEffect(viewModel) { viewModel.onAppear() }
    NoticeListContent(
        state = state,
        modifier = modifier,
        onBackTapped = viewModel::onBackTapped,
        onRetryTapped = viewModel::onRetryTapped,
        onNoticeTapped = viewModel::onNoticeTapped,
    )
}

/** 공지 목록(순수 UI). */
@Composable
fun NoticeListContent(
    state: NoticeListState,
    modifier: Modifier = Modifier,
    onBackTapped: () -> Unit = {},
    onRetryTapped: () -> Unit = {},
    onNoticeTapped: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        FCTopBar(style = FCTopBarStyle.Back, title = "공지사항", onBack = onBackTapped)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.load == NoticeLoad.Idle || state.load == NoticeLoad.Loading -> NoticeListLoading()
                state.load == NoticeLoad.Failed -> NoticeMessage(message = "공지사항을 불러오지 못했어요", onRetryTapped = onRetryTapped)
                state.notices.isEmpty() -> NoticeMessage(message = "등록된 공지사항이 없어요", onRetryTapped = null)
                else -> NoticeRows(state = state, onNoticeTapped = onNoticeTapped)
            }
        }
    }
}

@Composable
private fun NoticeRows(state: NoticeListState, onNoticeTapped: (String) -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .fcScrollBottomInset(),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(FestiCoreSpacing.paddingP16)
                    .clip(RoundedCornerShape(FestiCoreRadius.r16)),
        ) {
            state.notices.forEachIndexed { index, notice ->
                // iOS: 첫 줄이 아닌 줄 위에 왼쪽 16 안쪽 구분선.
                if (index > 0) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = FestiCoreSpacing.paddingP16)
                                .height(NoticeDividerHeight)
                                .background(FestiCoreSemanticColor.lineNormal.color),
                    )
                }
                FCListRow(
                    label = notice.title,
                    value = NoticePresentation.day(notice.publishedAt),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onNoticeTapped(notice.id) },
                )
            }
        }
    }
}

@Composable
private fun NoticeListLoading() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(FestiCoreSpacing.paddingP16)
                .clearAndSetSemantics { contentDescription = "공지사항을 불러오는 중" },
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        repeat(SKELETON_ROW_COUNT) {
            FCSkeletonView(
                shape = FCSkeletonShape.Round(cornerRadius = FestiCoreRadius.r16),
                modifier = Modifier.fillMaxWidth().height(SkeletonRowHeight),
            )
        }
    }
}

/**
 * 실패 · 빈 목록 문구. [onRetryTapped]가 있으면 "다시 시도"를 둔다. iOS `messageContent`(NoticeListView.swift:88).
 * iOS는 아래 안전 영역을 뺀 높이의 가운데에 두므로 내비게이션 바 인셋을 빼고 가운데 맞춘다.
 */
@Composable
internal fun NoticeMessage(message: String, onRetryTapped: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12, Alignment.CenterVertically),
    ) {
        Text(
            text = message,
            style = FestiCoreTextStyle.baseBodyRegular.textStyleWithoutLineBox,
            color = FestiCoreSemanticColor.labelNeutral.color,
        )
        if (onRetryTapped != null) {
            FCButton(
                title = "다시 시도",
                style = FCButtonStyle.Secondary,
                size = FCButtonSize.Medium,
                onClick = onRetryTapped,
            )
        }
    }
}
