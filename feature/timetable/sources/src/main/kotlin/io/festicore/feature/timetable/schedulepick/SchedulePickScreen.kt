package io.festicore.feature.timetable.schedulepick

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import io.festicore.domain.timetable.TimetableDay
import io.festicore.feature.timetable.SchedulePickResult
import io.festicore.feature.timetable.TimetablePushGate
import io.festicore.feature.timetable.TimetableRoute
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.navigation.FCSegmented
import io.festicore.ui.designkit.component.navigation.FCStepHeader
import io.festicore.ui.designkit.component.selection.FCSetSelectRow
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

private const val UNASSIGNED_STAGE = "무대 미정"
private val LineWidth = 1.dp

/**
 * S15-1 내 일정 고르기(iOS `SchedulePickView`). 타임테이블 "내 일정 N개"와 준비 안내 1단계가 같은 화면을 쓴다.
 * 결과(저장 · 건너뛰기 · 나중에)는 [onResult]로 부르는 쪽에 돌려준다(iOS `SchedulePickDelegate`).
 */
@Composable
fun SchedulePickScreen(
    route: TimetableRoute.SchedulePick,
    onResult: (SchedulePickResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SchedulePickViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is SchedulePickSideEffect.Finished -> onResult(sideEffect.result)
        }
    }
    SchedulePickContent(
        state = state,
        onDayTapped = viewModel::onDayTapped,
        onSetTapped = viewModel::onSetTapped,
        onNextTapped = viewModel::onNextTapped,
        onSkipTapped = viewModel::onSkipTapped,
        onLaterTapped = viewModel::onLaterTapped,
        modifier = modifier,
    )
}

/** 내 일정 고르기 순수 UI. 프리뷰는 이 함수를 쓴다. */
@Composable
fun SchedulePickContent(
    state: SchedulePickState,
    onDayTapped: (Int) -> Unit,
    onSetTapped: (String) -> Unit,
    onNextTapped: () -> Unit,
    onSkipTapped: () -> Unit,
    onLaterTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        FCStepHeader(step = 1, onLater = onLaterTapped)
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
            FCSegmented(selection = state.selectedDayIndex, onSelectionChange = onDayTapped, items = state.days.map { it.title })
            SetList(state, onSetTapped)
        }
        BottomBar(state, onNextTapped = onNextTapped, onSkipTapped = onSkipTapped)
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        BasicText(
            text = "Step 1. 내 일정 고르기",
            style = FestiCoreTextStyle.baseCaption1Medium.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
        )
        BasicText(
            text = "보고 싶은 무대를 골라 주세요",
            modifier = Modifier.semantics { heading() },
            style = FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        BasicText(
            text = schedulePickGuide(),
            style = FestiCoreTextStyle.baseSubheadlineRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
    }
}

/** 머리 안내(iOS 그대로). 푸시 전([TimetablePushGate])에는 알림 약속 줄을 빼고 바꾸기 안내만 둔다(설계 "P0 전 임시 숨김"). */
internal fun schedulePickGuide(isPushReady: Boolean = TimetablePushGate.IS_PUSH_READY): String =
    if (isPushReady) "고른 무대는 시작 10분 전에 알려 드려요.\n나중에 타임테이블에서 언제든 바꿀 수 있어요." else "나중에 타임테이블에서 언제든 바꿀 수 있어요."

@Composable
private fun SetList(state: SchedulePickState, onSetTapped: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        BasicText(
            text = "${state.selectedSetIds.size}개 선택",
            style = FestiCoreTextStyle.baseFootnoteBold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        val day = state.selectedDay
        val sets = state.sortedSets
        if (day != null && sets.isNotEmpty()) {
            val shape = RoundedCornerShape(FestiCoreRadius.r16)
            Column(modifier = Modifier.fillMaxWidth().clip(shape).border(LineWidth, FestiCoreSemanticColor.lineNormal.color, shape)) {
                sets.forEachIndexed { index, set ->
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().height(LineWidth).background(FestiCoreSemanticColor.lineNormal.color))
                    }
                    FCSetSelectRow(
                        artist = set.artist,
                        stage = day.stageName(set.stageId),
                        start = SchedulePickFormat.clock(set.start),
                        end = SchedulePickFormat.clock(set.end),
                        isSelected = set.id in state.selectedSetIds,
                        overlapText = state.overlaps[set.id]?.let(SchedulePickFormat::overlapText),
                        onClick = { onSetTapped(set.id) },
                    )
                }
            }
        } else {
            BasicText(
                text = "이 날은 아직 공연 시간이 없어요.",
                modifier = Modifier.fillMaxWidth().padding(vertical = FestiCoreSpacing.paddingP24),
                style =
                    FestiCoreTextStyle.baseSubheadlineRegular.textStyleWithoutLineBox.copy(
                        color = FestiCoreSemanticColor.labelAssistive.color,
                        textAlign = TextAlign.Center,
                    ),
            )
        }
        state.errorMessage?.let { message ->
            BasicText(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                style = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.statusNegative.color,
                ),
            )
        }
    }
}

/** 아래 바. iOS는 흐림(`ultraThinMaterial`) 위에 `materialGlass`를 얹지만 Compose 표준 흐림이 없어 단색만 쓴다(설계 적응 표). */
@Composable
private fun BottomBar(
    state: SchedulePickState,
    onNextTapped: () -> Unit,
    onSkipTapped: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(FestiCoreSemanticColor.materialGlass.color).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(LineWidth).background(FestiCoreSemanticColor.lineNormal.color))
        Row(
            modifier =
                Modifier.padding(
                    start = FestiCoreSpacing.paddingP16,
                    end = FestiCoreSpacing.paddingP16,
                    top = FestiCoreSpacing.paddingP12,
                    bottom = FestiCoreSpacing.paddingP8,
                ),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        ) {
            FCButton(title = "건너뛰기", style = FCButtonStyle.Ghost, onClick = onSkipTapped)
            FCButton(
                title = if (state.isSaving) "저장하는 중" else "다음 · ${state.selectedSetIds.size}개 선택",
                modifier = Modifier.weight(1f),
                isEnabled = state.canSubmit,
                isFullWidth = true,
                onClick = onNextTapped,
            )
        }
    }
}

private fun TimetableDay.stageName(stageId: String): String = stages.firstOrNull { it.id == stageId }?.name ?: UNASSIGNED_STAGE
