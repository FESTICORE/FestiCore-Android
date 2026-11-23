package io.festicore.feature.profile.notice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.festicore.domain.notice.entity.NoticeDetail
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

private val SkeletonTitleHeight = 24.dp
private val SkeletonDateWidth = 90.dp
private val SkeletonDateHeight = 14.dp
private val SkeletonBodyHeight = 180.dp

/**
 * 공지 상세(iOS `NoticeDetailView`). 뒤로는 [onBackRequested]로 알리고 Coordinator가 닫는다(T1).
 * 처음 나타날 때만 읽는다(iOS `onAppear`, `Idle`일 때만). 본문은 iOS `Text(String)`처럼 일반 텍스트로 보여 준다.
 */
@Composable
fun NoticeDetailScreen(
    viewModel: NoticeDetailViewModel,
    onBackRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            NoticeDetailSideEffect.BackRequested -> onBackRequested()
        }
    }
    LaunchedEffect(viewModel) { viewModel.onAppear() }
    NoticeDetailContent(
        state = state,
        modifier = modifier,
        onBackTapped = viewModel::onBackTapped,
        onRetryTapped = viewModel::onRetryTapped,
    )
}

/** 공지 상세(순수 UI). */
@Composable
fun NoticeDetailContent(
    state: NoticeDetailState,
    modifier: Modifier = Modifier,
    onBackTapped: () -> Unit = {},
    onRetryTapped: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        FCTopBar(style = FCTopBarStyle.Back, title = "공지사항", onBack = onBackTapped)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.load) {
                NoticeLoad.Idle, NoticeLoad.Loading -> NoticeDetailLoading()
                NoticeLoad.Failed -> NoticeMessage(message = "공지사항을 불러오지 못했어요", onRetryTapped = onRetryTapped)
                NoticeLoad.Loaded -> state.detail?.let { NoticeDetailBody(detail = it) }
            }
        }
    }
}

@Composable
private fun NoticeDetailBody(detail: NoticeDetail) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FestiCoreSpacing.paddingP20)
                .padding(top = FestiCoreSpacing.paddingP20)
                .fcScrollBottomInset(),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        Text(
            text = detail.title,
            style = FestiCoreTextStyle.baseTitle3Bold.textStyle,
            color = FestiCoreSemanticColor.labelNormal.color,
        )
        Text(
            text = NoticePresentation.day(detail.publishedAt),
            style = FestiCoreTextStyle.baseCaption1Regular.textStyle,
            color = FestiCoreSemanticColor.labelAssistive.color,
        )
        Box(modifier = Modifier.fillMaxWidth().height(NoticeDividerHeight).background(FestiCoreSemanticColor.lineNormal.color))
        Text(
            text = detail.content,
            modifier = Modifier.fillMaxWidth(),
            style = FestiCoreTextStyle.baseBodyRegular.textStyle,
            color = FestiCoreSemanticColor.labelNormal.color,
        )
    }
}

@Composable
private fun NoticeDetailLoading() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(FestiCoreSpacing.paddingP20)
                .clearAndSetSemantics { contentDescription = "공지사항을 불러오는 중" },
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        FCSkeletonView(
            shape = FCSkeletonShape.Round(cornerRadius = FestiCoreRadius.r4),
            modifier = Modifier.fillMaxWidth().height(SkeletonTitleHeight),
        )
        FCSkeletonView(
            shape = FCSkeletonShape.Round(cornerRadius = FestiCoreRadius.r4),
            modifier = Modifier.size(width = SkeletonDateWidth, height = SkeletonDateHeight),
        )
        FCSkeletonView(
            shape = FCSkeletonShape.Round(cornerRadius = FestiCoreRadius.r4),
            modifier = Modifier.fillMaxWidth().height(SkeletonBodyHeight),
        )
    }
}
