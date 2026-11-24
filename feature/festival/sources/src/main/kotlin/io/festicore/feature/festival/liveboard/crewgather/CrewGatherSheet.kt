package io.festicore.feature.festival.liveboard.crewgather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.modal.FCSheet
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.component.selection.FCChoiceTile
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** iOS `FestivalCrewGatherView.Metric`. */
private val MaxGridHeight = 420.dp

/**
 * "모여!" 시트(iOS `FestivalCrewGatherView`를 `fcModal(.auto)`로 띄운 것). [input]이 있으면 열린다.
 * ViewModel은 시트를 열 때마다 새로 만든다([CrewGatherInput.presentation] 키). 보내면 [onGathered], 닫으면 [onDismiss].
 */
@Composable
fun CrewGatherSheet(
    input: CrewGatherInput?,
    onDismiss: () -> Unit,
    onGathered: () -> Unit,
) {
    val viewModel =
        input?.let {
            koinViewModel<CrewGatherViewModel>(key = "crewGather-${it.crewId}-${it.presentation}") { parametersOf(it) }
        }
    FCSheet(isPresented = viewModel != null, onDismissRequest = onDismiss) {
        if (viewModel != null) CrewGatherSheetBody(viewModel, onGathered)
    }
}

@Composable
private fun CrewGatherSheetBody(viewModel: CrewGatherViewModel, onGathered: () -> Unit) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            CrewGatherSideEffect.Gathered -> onGathered()
        }
    }
    CrewGatherContent(state = state, onZoneTapped = viewModel::onZoneTapped)
    state.alert?.let { alert ->
        FCDialog(
            item = FCDialogItem(title = alert.title, message = alert.message, onConfirm = {}),
            onDismiss = viewModel::onAlertDismissed,
        )
    }
}

/** 시트 내용(순수 UI). 시트가 손잡이 아래 여백을 두므로 위 여백은 넣지 않는다. */
@Composable
fun CrewGatherContent(
    state: CrewGatherState,
    modifier: Modifier = Modifier,
    onZoneTapped: (String) -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = FestiCoreSpacing.paddingP16, end = FestiCoreSpacing.paddingP16, bottom = FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM4)) {
            Text(
                text = "어디로 모일까요?",
                modifier = Modifier.semantics { heading() },
                style = FestiCoreTextStyle.baseTitle2Bold.textStyle,
                color = FestiCoreSemanticColor.labelStrong.color,
            )
            Text(
                text = state.subtitle,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                color = FestiCoreSemanticColor.labelAssistive.color,
            )
        }
        if (state.zones.isEmpty()) {
            FCEmptyCard(title = "공개된 존이 없어요", message = "공식 존 정보가 공개되면 집결을 요청할 수 있어요.")
        } else {
            // 2열 타일. 존이 많으면 최대 높이 안에서 스크롤한다.
            Column(
                modifier = Modifier.heightIn(max = MaxGridHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
            ) {
                state.zones.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12)) {
                        row.forEach { zone ->
                            FCChoiceTile(
                                title = zone.name,
                                icon = R.drawable.ic_location_dot_16,
                                modifier = Modifier.weight(1f),
                                isSelected = zone.id == state.selectedZoneId,
                                onClick = { if (!state.isSubmitting) onZoneTapped(zone.id) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
