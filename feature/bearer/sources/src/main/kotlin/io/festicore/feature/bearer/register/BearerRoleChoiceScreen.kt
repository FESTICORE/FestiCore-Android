package io.festicore.feature.bearer.register

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.button.FCPrimaryButton
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.selection.FCCheckCircle
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

// iOS 역할 카드(`BRCV:88-130`): 아이콘 20 in 원 44, 테두리 고름 2 · 기본 1
private val IconSize = 20.dp
private val IconCircleSize = 44.dp
private val SelectedBorderWidth = 2.dp
private val BorderWidth = 1.dp

/**
 * 기수 여부(iOS `BearerRoleChoiceView`, Figma S14 C · C2 "페스티벌에서 깃발을 드시나요?"). ViewModel을 잇는다.
 * 결과는 콜백으로만 알린다. 경로 연결은 BC1 · BC2다. 시스템 뒤로는 상단 뒤로와 같다.
 */
@Composable
fun BearerRoleChoiceScreen(
    viewModel: BearerRoleChoiceViewModel,
    onAudienceChosen: () -> Unit,
    onBearerChosen: () -> Unit,
    onCloseRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            BearerRoleChoiceSideEffect.AudienceChosen -> onAudienceChosen()
            BearerRoleChoiceSideEffect.BearerChosen -> onBearerChosen()
            BearerRoleChoiceSideEffect.CloseRequested -> onCloseRequested()
        }
    }
    BackHandler(onBack = viewModel::onBackTapped)
    BearerRoleChoiceContent(
        state = state,
        modifier = modifier,
        onBackTapped = viewModel::onBackTapped,
        onRoleTapped = viewModel::onRoleTapped,
        onNextTapped = viewModel::onNextTapped,
    )
}

/** 기수 여부(순수 UI). */
@Composable
fun BearerRoleChoiceContent(
    state: BearerRoleChoiceState,
    modifier: Modifier = Modifier,
    onBackTapped: () -> Unit = {},
    onRoleTapped: (BearerRole) -> Unit = {},
    onNextTapped: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundNormal.color),
    ) {
        FCTopBar(style = FCTopBarStyle.Back, onBack = onBackTapped)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(FestiCoreSpacing.paddingP16),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM24),
        ) {
            Header()
            Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
                BearerRole.entries.forEach { role ->
                    RoleCard(role = role, isSelected = state.role == role, onClick = { onRoleTapped(role) })
                }
            }
        }
        // iOS `safeAreaInset(.bottom)`: 좌우 P16 · 위아래 P12, 배경 Background/Normal
        FCPrimaryButton(
            title = "다음",
            modifier =
                Modifier
                    .background(FestiCoreSemanticColor.backgroundNormal.color)
                    .navigationBarsPadding()
                    .padding(horizontal = FestiCoreSpacing.paddingP16, vertical = FestiCoreSpacing.paddingP12),
            isEnabled = state.role != null,
            onClick = onNextTapped,
        )
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8)) {
        BasicText(
            text = "페스티벌에서\n깃발을 드시나요?",
            style = FestiCoreTextStyle.baseTitle2Bold.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        BasicText(
            text = "기수는 깃발을 들고 사람들을 이끄는 사람이에요. 나중에 마이에서도 바꿀 수 있어요.",
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
    }
}

@Composable
private fun RoleCard(
    role: BearerRole,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(FestiCoreRadius.r16)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(FestiCoreSemanticColor.backgroundNormal.color)
                .border(
                    width = if (isSelected) SelectedBorderWidth else BorderWidth,
                    color = if (isSelected) FestiCoreSemanticColor.primaryNormal.color else FestiCoreSemanticColor.lineNormal.color,
                    shape = shape,
                ).semantics { selected = isSelected }
                .clickable(role = Role.Button, onClick = onClick)
                .padding(FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(IconCircleSize).background(FestiCoreSemanticColor.backgroundAlternative.color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon(role)),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNormal.color),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
            BasicText(
                text = title(role),
                style = FestiCoreTextStyle.baseCalloutBold.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNormal.color),
            )
            BasicText(
                text = message(role),
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.labelNeutral.color,
                ),
            )
        }
        FCCheckCircle(isChecked = isSelected)
    }
}

private fun icon(role: BearerRole): Int = when (role) {
    BearerRole.Audience -> R.drawable.ic_users_24
    BearerRole.Bearer -> R.drawable.ic_flag_20
}

private fun title(role: BearerRole): String = when (role) {
    BearerRole.Audience -> "관객이에요"
    BearerRole.Bearer -> "기수예요"
}

private fun message(role: BearerRole): String = when (role) {
    BearerRole.Audience -> "깃발을 팔로우하고 크루와 함께 놀아요"
    BearerRole.Bearer -> "내 깃발 프로필을 만들고 팔로워를 모아요"
}
