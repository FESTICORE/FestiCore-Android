package io.festicore.feature.bearer.transfer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.festicore.feature.bearer.profile.ActionBar
import io.festicore.feature.bearer.register.BearerFestivalPicker
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.button.FCPrimaryButton
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

// 깃발 등록(Figma S14 D)과 같은 섹션 사이 28
private val SectionSpacing = 28.dp

/**
 * 깃발 축제 이동(Android 먼저, 백엔드 #409). 깃발 상세의 기수 메뉴에서 연다. 새 모양 없이 깃발 등록(S14 D)의 상단 바 · 페스티벌 고르기 ·
 * 아래 버튼과 확인 팝업([FCDialog])을 쓴다. 옮기면 [onTransferred], 뒤로는 [onClose].
 */
@Composable
fun BearerFestivalTransferScreen(
    flagId: String,
    onTransferred: (festivalId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BearerFestivalTransferViewModel = koinViewModel(key = "festivalTransfer-$flagId") { parametersOf(flagId) },
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is BearerFestivalTransferSideEffect.Transferred -> onTransferred(sideEffect.festivalId)
            BearerFestivalTransferSideEffect.CloseRequested -> onClose()
        }
    }
    BackHandler(onBack = viewModel::onBackTapped)
    LaunchedEffect(viewModel) { viewModel.onAppear() }
    BearerFestivalTransferContent(
        state = state,
        modifier = modifier,
        callbacks =
            BearerFestivalTransferCallbacks(
                onBackTapped = viewModel::onBackTapped,
                onRetryTapped = viewModel::onRetryTapped,
                onFestivalTapped = viewModel::onFestivalTapped,
                onShowMoreTapped = viewModel::onShowMoreTapped,
                onTransferTapped = viewModel::onTransferTapped,
                onConfirmTapped = viewModel::onConfirmTapped,
                onConfirmDismissed = viewModel::onConfirmDismissed,
                onErrorDismissed = viewModel::onErrorDismissed,
            ),
    )
}

/** 순수 UI가 부르는 동작. */
data class BearerFestivalTransferCallbacks(
    val onBackTapped: () -> Unit = {},
    val onRetryTapped: () -> Unit = {},
    val onFestivalTapped: (String) -> Unit = {},
    val onShowMoreTapped: () -> Unit = {},
    val onTransferTapped: () -> Unit = {},
    val onConfirmTapped: () -> Unit = {},
    val onConfirmDismissed: () -> Unit = {},
    val onErrorDismissed: () -> Unit = {},
)

/** State만 받는 이동 화면. Preview · 화면 테스트가 쓴다. */
@Composable
fun BearerFestivalTransferContent(
    state: BearerFestivalTransferState,
    modifier: Modifier = Modifier,
    callbacks: BearerFestivalTransferCallbacks = BearerFestivalTransferCallbacks(),
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        FCTopBar(style = FCTopBarStyle.Back, title = "다른 페스티벌로 옮기기", onBack = callbacks.onBackTapped)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(FestiCoreSpacing.paddingP16),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Header()
            Body(state, callbacks)
        }
        ActionBar {
            FCPrimaryButton(
                title = if (state.isTransferring) "옮기는 중이에요" else "옮기기",
                isEnabled = state.canTransfer,
                onClick = callbacks.onTransferTapped,
            )
        }
    }
    if (state.isConfirming) {
        val festival = state.selectedFestival?.title ?: "고른 페스티벌"
        FCDialog(
            item =
                FCDialogItem(
                    title = "이 페스티벌로 옮길까요?",
                    message = "$festival\n계획·주로 있는 존·현장 상태가 초기화돼요.",
                    confirmTitle = "옮기기",
                    cancelTitle = "취소",
                    onConfirm = callbacks.onConfirmTapped,
                ),
            onDismiss = callbacks.onConfirmDismissed,
        )
    }
    state.errorMessage?.let { message ->
        FCDialog(
            item = FCDialogItem(title = "옮기지 못했어요", message = message, confirmTitle = "확인", cancelTitle = null, onConfirm = {}),
            onDismiss = callbacks.onErrorDismissed,
        )
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8)) {
        BasicText(
            text = "어느 페스티벌로 옮길까요?",
            style = FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        BasicText(
            text = "깃발 이름·사진·팔로워는 그대로예요.\n계획·주로 있는 존·현장 상태는 초기화돼요.",
            style = FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
    }
}

@Composable
private fun ColumnScope.Body(state: BearerFestivalTransferState, callbacks: BearerFestivalTransferCallbacks) {
    when (val eligibility = state.eligibility) {
        BearerTransferEligibility.NotOwner -> Notice("처음 깃발을 만든 기수만 옮길 수 있어요.")

        BearerTransferEligibility.HasCoBearers -> Notice("공동 기수가 있는 깃발은 옮길 수 없어요. 기수 보드에서 공동 기수를 먼저 해제해 주세요.")

        is BearerTransferEligibility.Failed -> {
            Notice(if (eligibility.offline) "인터넷에 연결되지 않아 깃발 정보를 불러오지 못했어요." else "깃발 정보를 불러오지 못했어요.")
            FCButton(title = "다시 시도", style = FCButtonStyle.Secondary, size = FCButtonSize.Medium, onClick = callbacks.onRetryTapped)
        }

        BearerTransferEligibility.Loading, BearerTransferEligibility.Eligible -> Unit
    }
    BearerFestivalPicker(
        title = "옮길 페스티벌",
        festivals = state.festivals,
        selectedFestivalId = state.selectedFestivalId,
        failedMessage = "옮길 수 있는 페스티벌을 불러오지 못했어요.",
        helper = { hasReceived ->
            if (hasReceived) "받은 팩·핀한 페스티벌이 먼저 보여요. 지금 페스티벌은 빼고 보여요." else "앞으로 열리는 페스티벌이에요. 지금 페스티벌은 빼고 보여요."
        },
        onFestivalTapped = callbacks.onFestivalTapped,
        onRetryTapped = callbacks.onRetryTapped,
        onShowMoreTapped = callbacks.onShowMoreTapped,
    )
}

/** 옮길 수 없는 이유. 등록 화면의 저장 실패 문구와 같은 모양이다. */
@Composable
private fun Notice(text: String) {
    BasicText(
        text = text,
        style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.statusNegative.color),
    )
}
