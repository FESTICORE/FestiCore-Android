package io.festicore.feature.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.card.FCCardAction
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * iOS `WebView`(Feature/Web). 약관 같은 문서를 앱 안에서 보여 준다. 닫기(상단 back)는 [onBackToRoot]로 부모에 맡긴다.
 */
@Composable
fun WebScreen(
    page: WebPage,
    onBackToRoot: () -> Unit,
    viewModel: WebViewModel = koinViewModel { parametersOf(page) },
) {
    val state by viewModel.collectAsState()

    LaunchedEffect(Unit) { viewModel.onAppear() }

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            WebSideEffect.BackToRoot -> onBackToRoot()
        }
    }

    WebContent(
        state = state,
        onCloseTapped = viewModel::onCloseTapped,
        onRetryTapped = viewModel::onRetryTapped,
    ) {
        WebDocumentView(
            url = state.page.url,
            loadId = state.loadId,
            isInteractive = state.phase == WebPhase.Loaded,
            onFinished = viewModel::onLoadFinished,
            onFailed = viewModel::onLoadFailed,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 웹뷰는 항상 붙여 두고 로딩 · 실패 중에는 덮개로 가린다(덮개가 터치도 막는다). */
@Composable
internal fun WebContent(
    state: WebState,
    onCloseTapped: () -> Unit,
    onRetryTapped: () -> Unit,
    webContent: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundNormal.color)
                .navigationBarsPadding(),
    ) {
        FCTopBar(style = FCTopBarStyle.Back, title = state.page.title, onBack = onCloseTapped)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            webContent()
            when (state.phase) {
                WebPhase.Loaded -> Unit

                WebPhase.Loading ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(FestiCoreSemanticColor.backgroundNormal.color)
                                .pointerInput(Unit) { consumeAllPointerEvents() }
                                .clearAndSetSemantics { contentDescription = "문서를 불러오는 중" },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = FestiCoreSemanticColor.labelAssistive.color)
                    }

                WebPhase.Failed ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(FestiCoreSemanticColor.backgroundNormal.color)
                                .pointerInput(Unit) { consumeAllPointerEvents() },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        FCEmptyCard(
                            title = "문서를 불러오지 못했어요",
                            message = "연결을 확인하고 다시 시도해 주세요.",
                            icon = R.drawable.ic_circle_info_16,
                            action = FCCardAction("다시 시도", onClick = onRetryTapped),
                            modifier = Modifier.padding(FestiCoreSpacing.paddingP16),
                        )
                    }
            }
        }
    }
}

private suspend fun PointerInputScope.consumeAllPointerEvents() {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

@Preview
@Composable
private fun WebContentLoadingPreview() {
    WebContent(state = WebState(PreviewPage, loadId = 1), onCloseTapped = {}, onRetryTapped = {}) {}
}

@Preview
@Composable
private fun WebContentFailedPreview() {
    WebContent(state = WebState(PreviewPage, loadId = 1, phase = WebPhase.Failed), onCloseTapped = {}, onRetryTapped = {}) {}
}

private val PreviewPage = WebPage(url = "https://festicore.app/terms", title = "서비스 이용약관")
