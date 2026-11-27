package io.festicore.feature.profile.prepchecklist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.festicore.feature.profile.packdetail.PackDetailLoad
import io.festicore.feature.profile.packdetail.PackDetailState
import io.festicore.feature.profile.packdetail.PackDetailViewModel
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.navigation.FCStepHeader
import io.festicore.ui.designkit.component.selection.FCAddItemRow
import io.festicore.ui.designkit.component.selection.FCChecklistRow
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun PrepChecklistScreen(
    viewModel: PackDetailViewModel,
    onDone: () -> Unit,
    onLater: () -> Unit,
    onNavigationBlocked: (Boolean) -> Unit,
    onBack: () -> Unit = {},
) {
    val state by viewModel.collectAsState()
    BackHandler { if (!viewModel.hasPendingChecklistWrites) onBack() }
    LaunchedEffect(viewModel) { viewModel.onAppear() }
    LaunchedEffect(state.pendingChecklistWrites) { onNavigationBlocked(state.pendingChecklistWrites > 0) }
    PrepChecklistContent(
        state = state,
        onToggle = viewModel::onItemToggled,
        onTextChanged = viewModel::onNewItemTextChanged,
        onAdd = viewModel::onAddItemTapped,
        onDone = { if (!viewModel.hasPendingChecklistWrites) onDone() },
        onLater = { if (!viewModel.hasPendingChecklistWrites) onLater() },
    )
}

@Composable
fun PrepChecklistContent(
    state: PackDetailState,
    onToggle: (String) -> Unit,
    onTextChanged: (String) -> Unit,
    onAdd: () -> Unit,
    onDone: () -> Unit,
    onLater: () -> Unit,
) {
    val pending = state.pendingChecklistWrites > 0
    Column(
        Modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundElevated.color).windowInsetsPadding(WindowInsets.statusBars),
    ) {
        FCStepHeader(step = 3, laterTitle = if (pending) "저장 중…" else "나중에 할게요", onLater = { if (!pending) onLater() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(FestiCoreSpacing.paddingP16),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP20),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                Text(
                    "Step 3. 준비물 챙기기",
                    style = FestiCoreTextStyle.baseCaption1Medium.textStyle,
                    color = FestiCoreSemanticColor.labelAssistive.color,
                )
                Text(
                    "준비물 체크리스트를 미리 챙겨 볼까요?",
                    modifier = Modifier.semantics {
                        heading()
                    },
                    style = FestiCoreTextStyle.baseTitle2Bold.textStyle,
                    color = FestiCoreSemanticColor.labelNormal.color,
                )
            }
            when (state.load) {
                PackDetailLoad.Idle, PackDetailLoad.Loading -> Text("준비물을 불러오는 중")

                PackDetailLoad.Missing, PackDetailLoad.Failed -> Text("준비물을 불러오지 못했어요. 건너뛰고 나중에 마이 > 받은 팩에서 챙길 수 있어요.")

                PackDetailLoad.Loaded -> Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                    Text(
                        "${state.checklistRows.size}개 중 ${state.checkedCount}개 챙겼어요",
                        modifier = Modifier.align(Alignment.End),
                        style = FestiCoreTextStyle.baseFootnoteBold.textStyle,
                        color = FestiCoreSemanticColor.labelNormal.color,
                    )
                    val rules = state.detail?.rules.orEmpty()
                    if (rules.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(FestiCoreRadius.r12))
                                .background(FestiCoreSemanticColor.accentPointOrange.color.copy(alpha = 0.1f))
                                .padding(horizontal = FestiCoreSpacing.paddingP16, vertical = FestiCoreSpacing.paddingP12),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(R.drawable.ic_triangle_exclamation_16),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.accentPointOrange.color),
                                )
                                Text(
                                    "반입 금지 · ${state.title}",
                                    style = FestiCoreTextStyle.baseFootnoteBold.textStyle,
                                    color = FestiCoreSemanticColor.labelNormal.color,
                                )
                            }
                            Text(
                                rules.joinToString(" · "),
                                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                                color = FestiCoreSemanticColor.labelNeutral.color,
                            )
                        }
                    }
                    val shape = RoundedCornerShape(FestiCoreRadius.r16)
                    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, FestiCoreSemanticColor.lineNormal.color, shape)) {
                        state.checklistRows.forEach { row ->
                            FCChecklistRow(label = row.label, isChecked = row.isChecked, isRequired = row.isRequired, onClick = {
                                onToggle(row.id)
                            })
                            Box(Modifier.fillMaxWidth().height(1.dp).background(FestiCoreSemanticColor.lineNormal.color))
                        }
                        FCAddItemRow(text = state.newItemText, onTextChange = onTextChanged, onAdd = onAdd)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(
                FestiCoreSemanticColor.materialGlass.color,
            ).navigationBarsPadding().padding(FestiCoreSpacing.paddingP16),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        ) {
            FCButton("건너뛰기", style = FCButtonStyle.Ghost, isEnabled = !pending, onClick = onDone)
            FCButton(
                if (pending) "저장 중…" else "준비 완료",
                modifier = Modifier.weight(1f),
                isEnabled = !pending,
                isFullWidth = true,
                onClick = onDone,
            )
        }
    }
}
