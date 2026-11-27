package io.festicore.feature.festival.prepguide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.festicore.feature.festival.flag.FilterChips
import io.festicore.feature.festival.flag.FlagCard
import io.festicore.feature.festival.flag.FlagPresentation
import io.festicore.feature.festival.flag.FlagSideEffect
import io.festicore.feature.festival.flag.FlagState
import io.festicore.feature.festival.flag.FlagTag
import io.festicore.feature.festival.flag.FlagToast
import io.festicore.feature.festival.flag.FlagViewModel
import io.festicore.feature.festival.flag.FlagViewState
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.navigation.FCStepHeader
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

@Composable
fun FestivalPrepFlagsScreen(
    viewModel: FlagViewModel,
    zoneNames: Map<String, String>,
    onNext: () -> Unit,
    onLater: () -> Unit,
    onNavigationBlocked: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onTerms: () -> Unit,
    onBack: () -> Unit = {},
) {
    val state by viewModel.collectAsState()
    BackHandler { if (!viewModel.hasPendingFollows) onBack() }
    val pending = state.directory.pendingFollows.isNotEmpty()
    LaunchedEffect(pending) { onNavigationBlocked(pending) }
    LaunchedEffect(zoneNames) { viewModel.onZoneNamesChanged(zoneNames) }
    DisposableEffect(viewModel) {
        viewModel.onAppeared()
        onDispose { viewModel.onDisappeared() }
    }
    viewModel.collectSideEffect {
        when (it) {
            FlagSideEffect.LoginRequested -> onLogin()
            FlagSideEffect.TermsAcceptanceRequested -> onTerms()
            is FlagSideEffect.DetailRequested -> Unit
        }
    }
    FestivalPrepFlagsContent(
        state = state,
        onTag = viewModel::onTagSelected,
        onFollow = viewModel::onFollowTapped,
        onNext = { if (!viewModel.hasPendingFollows) onNext() },
        onLater = { if (!viewModel.hasPendingFollows) onLater() },
    )
}

@Composable
fun FestivalPrepFlagsContent(
    state: FlagState,
    onTag: (FlagTag?) -> Unit,
    onFollow: (String, Boolean, Int?) -> Unit,
    onNext: () -> Unit,
    onLater: () -> Unit,
) {
    val pending = state.directory.pendingFollows.isNotEmpty()
    val snapshot = state.snapshot
    val count =
        snapshot?.let {
            FlagPresentation.rows(state.directory.copy(tag = null), it, state.isRefreshFailed).count { row -> row.isFollowing }
        }
            ?: 0
    Box(Modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundElevated.color).windowInsetsPadding(WindowInsets.statusBars)) {
        Column {
            FCStepHeader(step = 2, laterTitle = if (pending) "저장 중…" else "나중에 할게요", onLater = { if (!pending) onLater() })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(FestiCoreSpacing.paddingP16),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP20),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                    Text(
                        "Step 2. 깃발 팔로우 하기",
                        style = FestiCoreTextStyle.baseCaption1Medium.textStyle,
                        color = FestiCoreSemanticColor.labelAssistive.color,
                    )
                    Text(
                        "만나보고 싶은 깃발이 있나요?",
                        modifier = Modifier.semantics {
                            heading()
                        },
                        style = FestiCoreTextStyle.baseTitle2Bold.textStyle,
                        color = FestiCoreSemanticColor.labelNormal.color,
                    )
                    Text(
                        "없어도 공연은 그대로 즐길 수 있어요.",
                        style = FestiCoreTextStyle.baseSubheadlineRegular.textStyle,
                        color = FestiCoreSemanticColor.labelNeutral.color,
                    )
                }
                FilterChips(state.directory.tag, onTag)
                if (snapshot != null) {
                    Text(
                        "${count}개 팔로우",
                        modifier = Modifier.align(Alignment.End),
                        style = FestiCoreTextStyle.baseFootnoteBold.textStyle,
                        color = FestiCoreSemanticColor.labelNormal.color,
                    )
                    val rows = FlagPresentation.rows(state.directory, snapshot, state.isRefreshFailed)
                    if (rows.isEmpty()) Text(if (snapshot.directoryFlags.isEmpty()) "아직 깃발이 없어요" else "이 성향의 깃발이 아직 없어요")
                    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
                        rows.forEach { row -> FlagCard(row, state.isOffline, onFollow, {}) }
                    }
                } else {
                    Text(
                        if (state.viewState == FlagViewState.Loading ||
                            state.viewState == FlagViewState.Idle
                        ) {
                            "깃발을 불러오는 중"
                        } else {
                            "깃발을 불러오지 못했어요. 건너뛰고 나중에 깃발 탭에서 팔로우할 수 있어요."
                        },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().background(
                    FestiCoreSemanticColor.materialGlass.color,
                ).navigationBarsPadding().padding(FestiCoreSpacing.paddingP16),
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
            ) {
                FCButton("건너뛰기", style = FCButtonStyle.Ghost, isEnabled = !pending, onClick = onNext)
                FCButton(
                    if (pending) "저장 중…" else "다음 · ${count}개 팔로우",
                    modifier = Modifier.weight(1f),
                    isEnabled = !pending,
                    isFullWidth = true,
                    onClick = onNext,
                )
            }
        }
        FlagToast(state.directory.toast, Modifier.align(Alignment.BottomCenter))
    }
}
