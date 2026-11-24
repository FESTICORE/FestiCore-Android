package io.festicore.feature.bearer.claim

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.ui.designkit.component.button.FCPrimaryButton
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

// iOS `BearerClaimView.Metric`: Figma S13 F~I 제목과 코드 사이 32, 안내 줄 사이 6
private val SectionSpacing = 32.dp
private val NoticeSpacing = 6.dp

// iOS `.tint(.clear)`: 투명 입력창의 커서 · 선택 손잡이를 감춘다.
private val HiddenSelectionColors = TextSelectionColors(handleColor = Color.Transparent, backgroundColor = Color.Transparent)

/**
 * 기수 인증 코드(iOS `BearerClaimView`, Figma S13 F~I 입력 전 · 입력 중 · 틀림 · 잠김). ViewModel을 잇는다.
 * 결과는 콜백으로만 알린다. 경로 연결은 BC1(`BearerEntryCoordinator`)이다. 시스템 뒤로는 상단 뒤로와 같다.
 */
@Composable
fun BearerClaimScreen(
    viewModel: BearerClaimViewModel,
    onClaimed: (festivalId: String, claim: BearerClaim) -> Unit,
    onCloseRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is BearerClaimSideEffect.Claimed -> onClaimed(sideEffect.festivalId, sideEffect.claim)
            BearerClaimSideEffect.CloseRequested -> onCloseRequested()
        }
    }
    BackHandler(onBack = viewModel::onBackTapped)
    BearerClaimContent(
        state = state,
        modifier = modifier,
        onCodeChanged = viewModel::onCodeChanged,
        onSubmitTapped = viewModel::onSubmitTapped,
        onBackTapped = viewModel::onBackTapped,
    )
}

/** 기수 인증 코드(순수 UI). 제목 · 코드 칸 · 안내 줄을 스크롤하고 아래에 확인 버튼을 고정한다. */
@Composable
fun BearerClaimContent(
    state: BearerClaimState,
    modifier: Modifier = Modifier,
    onCodeChanged: (String) -> Unit = {},
    onSubmitTapped: () -> Unit = {},
    onBackTapped: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundNormal.color)
                .navigationBarsPadding()
                .imePadding(),
    ) {
        FCTopBar(style = FCTopBarStyle.Back, title = "기수 인증", onBack = onBackTapped)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(vertical = FestiCoreSpacing.paddingP24),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Header()
            Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12)) {
                CodeInput(state = state, onCodeChanged = onCodeChanged, onSubmitTapped = onSubmitTapped)
                Column(verticalArrangement = Arrangement.spacedBy(NoticeSpacing)) {
                    Notices(state)
                }
            }
        }
        FCPrimaryButton(
            title = if (state.isSubmitting) "확인하는 중" else "확인",
            isEnabled = state.canSubmit,
            onClick = onSubmitTapped,
            modifier =
                Modifier
                    .padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(top = FestiCoreSpacing.paddingP12, bottom = FestiCoreSpacing.paddingP8),
        )
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8)) {
        BasicText(
            text = "운영팀에게 받은\n코드 6자리를 입력해 주세요",
            style = FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
        )
        BasicText(
            text = "영문과 숫자로 된 코드예요. 페스티벌마다 달라요.",
            style = FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
        )
    }
}

@Composable
private fun CodeInput(
    state: BearerClaimState,
    onCodeChanged: (String) -> Unit,
    onSubmitTapped: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by rememberSaveable { mutableStateOf(false) }
    // iOS `.onAppear { isCodeFocused = !store.isLocked }`
    LaunchedEffect(Unit) { if (!state.isLocked) focusRequester.requestFocus() }
    Box {
        BearerCodeBoxes(
            code = state.code,
            length = BearerClaimState.CODE_LENGTH,
            isFocused = isFocused,
            isError = state.failure is BearerClaimFailure.Invalid,
            isDisabled = state.isLocked,
        )
        CompositionLocalProvider(LocalTextSelectionColors provides HiddenSelectionColors) {
            BasicTextField(
                value = state.code,
                onValueChange = onCodeChanged,
                modifier =
                    Modifier
                        .matchParentSize()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                        .semantics { contentDescription = "기수 인증 코드" },
                enabled = !state.isLocked && !state.isSubmitting,
                textStyle = TextStyle(color = Color.Transparent),
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { onSubmitTapped() }),
                singleLine = true,
                cursorBrush = SolidColor(Color.Transparent),
            )
        }
    }
}

@Composable
private fun Notices(state: BearerClaimState) {
    when (val failure = state.failure) {
        is BearerClaimFailure.Invalid -> {
            val remaining = failure.remainingAttempts
            BearerNoticeLine(text = if (remaining > 0) "코드가 맞지 않아요 · ${remaining}번 남았어요" else "코드가 맞지 않아요", isWarning = true)
            BearerNoticeLine(text = "3번 틀리면 10분 동안 입력할 수 없어요.", isWarning = false)
        }

        BearerClaimFailure.Locked -> {
            BearerNoticeLine(text = "3번 틀려서 입력이 잠겼어요", isWarning = true)
            BearerNoticeLine(text = "${lockRemainingText(state.lockRemainingSeconds)} 뒤에 다시 입력할 수 있어요.", isWarning = false)
        }

        is BearerClaimFailure.Message -> BearerNoticeLine(text = failure.text, isWarning = true)

        null -> BearerNoticeLine(text = "코드는 운영팀이 기수에게 직접 알려 드려요.", isWarning = false)
    }
}

/** iOS `lockRemainingText`: `m:ss` */
internal fun lockRemainingText(seconds: Int): String = "${seconds / 60}:" + "%02d".format(seconds % 60)
