package io.festicore.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

// iOS OnboardingView.Metric · Figma Onboarding 수치. 토큰에 없는 값은 iOS와 같은 리터럴이다.
private val TopActionsHeight = 52.dp
private val ContentSpacing = 32.dp
private val BottomPadding = 40.dp
private val VisualMaxHeight = 431.dp
private val VisualMinHeight = 200.dp
private val NonVisualHeight = 328.dp
private val DotSize = 8.dp
private val DotSpacing = 8.dp
private const val PAGE_ANIMATION_MILLIS = 300

@Composable
fun OnboardingScreen(onResult: (OnboardingResult) -> Unit, viewModel: OnboardingViewModel = koinViewModel()) {
    val state by viewModel.collectAsState()

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            OnboardingSideEffect.Completed -> onResult(OnboardingResult.Completed)
        }
    }

    OnboardingContent(
        state = state,
        onPageChanged = viewModel::onPageChanged,
        onNextTapped = viewModel::onNextTapped,
        onSkipTapped = viewModel::onSkipTapped,
    )
}

@Composable
internal fun OnboardingContent(
    state: OnboardingState,
    onPageChanged: (Int) -> Unit,
    onNextTapped: () -> Unit,
    onSkipTapped: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = state.page) { OnboardingPage.all.size }
    PagerSync(pagerState = pagerState, page = state.page, onPageChanged = onPageChanged)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundNormal.color),
    ) {
        // 건너뛰기와 페이지 점은 한 화면에 고정하고 그림 · 문구만 넘긴다. 그림 높이는 안전 영역을 뺀 높이로 정한다.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val visualHeight = visualHeight(maxHeight)
            Column(modifier = Modifier.fillMaxSize()) {
                TopActions(onSkipTapped = onSkipTapped)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { index ->
                    PageContents(
                        page = OnboardingPage.all[index],
                        isLast = index == OnboardingPage.all.lastIndex,
                        visualHeight = visualHeight,
                        onNextTapped = onNextTapped,
                    )
                }
                PageIndicator(page = state.page)
            }
        }
    }
}

/** 스와이프로 바뀐 페이지는 ViewModel에 알리고, ViewModel 페이지(이미지 탭)는 페이저를 0.3초 동안 민다. */
@Composable
private fun PagerSync(
    pagerState: PagerState,
    page: Int,
    onPageChanged: (Int) -> Unit,
) {
    val currentOnPageChanged by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { currentOnPageChanged(it) }
    }
    LaunchedEffect(page) {
        if (pagerState.currentPage != page) {
            pagerState.animateScrollToPage(page, animationSpec = tween(PAGE_ANIMATION_MILLIS, easing = EaseInOut))
        }
    }
}

/** Figma 그림 431 = 화면 높이 - 328. 최소 200, 최대 431. */
private fun visualHeight(screenHeight: Dp): Dp = minOf(VisualMaxHeight, maxOf(VisualMinHeight, screenHeight - NonVisualHeight))

/** Figma Onboarding Top Actions: 높이 52, 좌우 8, ghost · M 건너뛰기 버튼. */
@Composable
private fun TopActions(onSkipTapped: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TopActionsHeight)
                .padding(horizontal = FestiCoreSpacing.paddingP8),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCButton(
            title = "건너뛰기",
            style = FCButtonStyle.Ghost,
            size = FCButtonSize.Medium,
            onClick = onSkipTapped,
        )
    }
}

/** Figma Onboarding Contents: 그림 · 문구 간격 32, 좌우 24 · 위 24. 그림을 누르면 다음으로 넘어간다. */
@Composable
private fun PageContents(
    page: OnboardingPage,
    isLast: Boolean,
    visualHeight: Dp,
    onNextTapped: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = FestiCoreSpacing.paddingP24)
                .padding(top = FestiCoreSpacing.paddingP24),
        verticalArrangement = Arrangement.spacedBy(ContentSpacing),
    ) {
        Image(
            painter = painterResource(page.image),
            contentDescription = if (isLast) "시작하기" else "다음 화면",
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(visualHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onNextTapped,
                    ),
        )
        // Figma Onboarding Text: 제목 Title 1/Bold · 설명 Body/Regular, 간격 12
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
            BasicText(
                text = page.title,
                style = FestiCoreTextStyle.baseTitle1Bold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            )
            BasicText(
                text = page.description,
                style = FestiCoreTextStyle.baseBodyRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
            )
        }
    }
}

/** Figma Onboarding 페이지 점: 8x8, 간격 8, 문구와 32 · 아래 40. 넘기면 현재 점만 바뀐다. */
@Composable
private fun PageIndicator(page: Int) {
    Row(
        modifier =
            Modifier
                .padding(horizontal = FestiCoreSpacing.paddingP24)
                .padding(top = ContentSpacing, bottom = BottomPadding)
                .clearAndSetSemantics { contentDescription = "${page + 1} / ${OnboardingPage.all.size} 페이지" },
        horizontalArrangement = Arrangement.spacedBy(DotSpacing),
    ) {
        OnboardingPage.all.indices.forEach { dot ->
            val color by animateColorAsState(
                targetValue =
                    if (dot == page) {
                        FestiCoreSemanticColor.primaryNormal.color
                    } else {
                        FestiCoreSemanticColor.fillStrong.color
                    },
                animationSpec = tween(PAGE_ANIMATION_MILLIS, easing = EaseInOut),
                label = "onboardingDot",
            )
            Box(
                modifier =
                    Modifier
                        .size(DotSize)
                        .clip(CircleShape)
                        .background(color),
            )
        }
    }
}
