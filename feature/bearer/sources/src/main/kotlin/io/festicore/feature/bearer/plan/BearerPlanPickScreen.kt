package io.festicore.feature.bearer.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.festicore.domain.bearer.entity.BearerSet
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.navigation.FCSegmented
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.selection.FCSetSelectRow
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

// iOS `BearerPlanPickView.Metric.divider`
private val Divider = 1.dp

/**
 * 오늘 계획 고르기(iOS `BearerPlanPickView`). ViewModel을 잇는다.
 * 결과는 콜백으로만 알린다. 경로 연결은 BC1(`BearerEntryCoordinator`)이다. 시스템 뒤로는 상단 뒤로와 같다.
 */
@Composable
fun BearerPlanPickScreen(
    viewModel: BearerPlanPickViewModel,
    onPicked: (setIds: List<String>) -> Unit,
    onCloseRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is BearerPlanPickSideEffect.Picked -> onPicked(sideEffect.setIds)
            BearerPlanPickSideEffect.CloseRequested -> onCloseRequested()
        }
    }
    BackHandler(onBack = viewModel::onBackTapped)
    BearerPlanPickContent(
        state = state,
        modifier = modifier,
        onDayTapped = viewModel::onDayTapped,
        onSetTapped = viewModel::onSetTapped,
        onDoneTapped = viewModel::onDoneTapped,
        onBackTapped = viewModel::onBackTapped,
    )
}

/** 오늘 계획 고르기(순수 UI). 머리글 · 날짜 · 공연 목록을 스크롤하고 아래에 완료 버튼을 고정한다. */
@Composable
fun BearerPlanPickContent(
    state: BearerPlanPickState,
    modifier: Modifier = Modifier,
    onDayTapped: (Int) -> Unit = {},
    onSetTapped: (String) -> Unit = {},
    onDoneTapped: () -> Unit = {},
    onBackTapped: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundNormal.color)
                .navigationBarsPadding(),
    ) {
        FCTopBar(style = FCTopBarStyle.Back, title = "오늘 계획", onBack = onBackTapped)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(FestiCoreSpacing.paddingP16),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP20),
        ) {
            Header()
            if (state.days.isEmpty()) {
                EmptyMessage()
            } else {
                FCSegmented(selection = state.selectedDayIndex, onSelectionChange = onDayTapped, items = state.days.map { it.title })
                SetList(state = state, onSetTapped = onSetTapped)
            }
        }
        BottomBar(count = state.selectedSetIds.size, onDoneTapped = onDoneTapped)
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        BasicText(
            text = "오늘 갈 무대를 골라 주세요",
            modifier = Modifier.semantics { heading() },
            style = FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        BasicText(
            text = "고른 무대는 깃발의 오늘 계획으로 보여요.\n깃발 프로필을 저장하면 반영돼요.",
            style = FestiCoreTextStyle.baseSubheadlineRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
    }
}

@Composable
private fun EmptyMessage() {
    BasicText(
        text = "받아 둔 팩에 공연 시간이 아직 없어요.",
        modifier = Modifier.fillMaxWidth().padding(vertical = FestiCoreSpacing.paddingP24),
        style =
            FestiCoreTextStyle.baseSubheadlineRegular.textStyle.copy(
                color = FestiCoreSemanticColor.labelAssistive.color,
                textAlign = TextAlign.Center,
            ),
    )
}

@Composable
private fun SetList(state: BearerPlanPickState, onSetTapped: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        BasicText(
            text = "${state.selectedSetIds.size}개 선택",
            style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        state.selectedDay?.let { day ->
            val shape = RoundedCornerShape(FestiCoreRadius.r16)
            Column(modifier = Modifier.fillMaxWidth().clip(shape).border(Divider, FestiCoreSemanticColor.lineNormal.color, shape)) {
                day.sets.forEachIndexed { index, set ->
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().height(Divider).background(FestiCoreSemanticColor.lineNormal.color))
                    }
                    SetRow(set = set, isSelected = set.id in state.selectedSetIds, onClick = { onSetTapped(set.id) })
                }
            }
        }
        state.limitMessage?.let { message ->
            BasicText(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                style = FestiCoreTextStyle.baseCaption1Regular.textStyle.copy(color = FestiCoreSemanticColor.statusNegative.color),
            )
        }
    }
}

@Composable
private fun SetRow(
    set: BearerSet,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    FCSetSelectRow(
        artist = set.artist,
        stage = set.stageName ?: "무대 미정",
        start = BearerPlanFormat.time(set.startsAt),
        end = BearerPlanFormat.time(set.endsAt),
        isSelected = isSelected,
        onClick = onClick,
    )
}

@Composable
private fun BottomBar(count: Int, onDoneTapped: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(FestiCoreSemanticColor.backgroundElevated.color)) {
        Box(Modifier.fillMaxWidth().height(Divider).background(FestiCoreSemanticColor.lineNormal.color))
        FCButton(
            title = "${count}개 선택 완료",
            modifier =
                Modifier
                    .padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(top = FestiCoreSpacing.paddingP12 - Divider, bottom = FestiCoreSpacing.paddingP8),
            isFullWidth = true,
            onClick = onDoneTapped,
        )
    }
}
