package io.festicore.feature.festival.liveboard.crewcreate

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.feature.festival.crew.CrewPresentation
import io.festicore.feature.festival.liveboard.LiveBoardPresentation
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.component.selection.FCTextField
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** iOS `FestivalCrewCreateView.Metric`. 하단 고정 영역은 크루 참여 확인(S08)과 같은 여백을 쓴다. */
private val FooterTopPadding = 13.dp
private val FooterBottomPadding = 1.dp
private val DividerHeight = 1.dp

/** 만들기 화면 초대 공유 문구(iOS `ShareLink(message:)`, 설계 F5). */
private const val INVITE_MESSAGE = "FestiCore 크루에 초대해요"

/**
 * S07 B/D 크루 만들기(iOS `FestivalCrewCreateView`). 라이브 보드에서 push로 열고 탭 바 없이 아래 고정 버튼을 둔다.
 * 닫기 · 크루 보기 · 로그인은 경계 콜백으로 알린다. 제출 중에는 시스템 뒤로를 막는다(iOS `closeTapped` 가드).
 */
@Composable
fun CrewCreateScreen(
    viewModel: CrewCreateViewModel,
    onClose: () -> Unit,
    onOpenCrew: (String) -> Unit,
    onLoginRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    val context = LocalContext.current
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            CrewCreateSideEffect.CloseRequested -> onClose()
            is CrewCreateSideEffect.OpenCrewRequested -> onOpenCrew(sideEffect.crewId)
            CrewCreateSideEffect.LoginRequested -> onLoginRequested()
        }
    }
    BackHandler(enabled = state.isSubmitting) {}

    CrewCreateContent(
        state = state,
        modifier = modifier,
        onNameChanged = viewModel::onNameChanged,
        onSubmitTapped = viewModel::onSubmitTapped,
        onCloseTapped = viewModel::onCloseTapped,
        onOpenCrewTapped = viewModel::onOpenCrewTapped,
        onShareTapped = { url -> shareInvite(context, "$INVITE_MESSAGE\n$url") },
    )
    if (state.isRetryDialogPresented) {
        FCDialog(
            item =
                FCDialogItem(
                    title = "크루를 만들지 못했어요",
                    message = "연결을 확인하고 다시 시도해 주세요.",
                    confirmTitle = "다시 시도",
                    cancelTitle = "닫기",
                    onConfirm = viewModel::onRetryConfirmed,
                ),
            onDismiss = viewModel::onRetryDismissed,
        )
    }
}

/** 초대 링크를 시스템 공유 시트로 보낸다(iOS `ShareLink`). */
internal fun shareInvite(context: Context, text: String) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

/** 크루 만들기(순수 UI). */
@Composable
fun CrewCreateContent(
    state: CrewCreateState,
    modifier: Modifier = Modifier,
    onNameChanged: (String) -> Unit = {},
    onSubmitTapped: () -> Unit = {},
    onCloseTapped: () -> Unit = {},
    onOpenCrewTapped: () -> Unit = {},
    onShareTapped: (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color).imePadding()) {
        FCTopBar(
            style = FCTopBarStyle.Back,
            title = "크루 만들기",
            backgroundColor = FestiCoreSemanticColor.backgroundNavigation,
            onBack = onCloseTapped,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FestiCoreSpacing.paddingP16, vertical = FestiCoreSpacing.paddingP24),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
        ) {
            when (val step = state.step) {
                CrewCreateStep.Naming -> Naming(state, onNameChanged)
                is CrewCreateStep.Created -> Created(step.crew)
            }
        }
        Footer(state, onSubmitTapped, onOpenCrewTapped, onShareTapped)
    }
}

@Composable
private fun Naming(state: CrewCreateState, onNameChanged: (String) -> Unit) {
    Header(title = "크루 이름을 정해 주세요", message = "같이 온 친구들끼리만 서로 어느 존에 있는지 볼 수 있어요")
    FCTextField(
        text = state.name,
        onTextChange = { if (!state.isSubmitting) onNameChanged(it) },
        placeholder = "예: 락린이들",
        label = "크루 이름",
        maxLength = FestivalCrew.MAX_NAME_LENGTH,
        counter = "${state.name.length}/${FestivalCrew.MAX_NAME_LENGTH}",
        helper = "단톡방 이름처럼 다들 알아보는 이름이 좋아요",
        error = state.errorMessage,
    )
    // Figma S07 B Info: Fill/Normal 상자 안 안내 세 줄(Footnote/Regular · Label/Neutral)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.fillNormal.color, RoundedCornerShape(FestiCoreRadius.r12))
                .padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        state.rules.forEach { rule ->
            Text(text = rule, style = FestiCoreTextStyle.baseFootnoteRegular.textStyle, color = FestiCoreSemanticColor.labelNeutral.color)
        }
    }
}

@Composable
private fun Created(crew: FestivalCrew) {
    Header(title = "${crew.name} 크루를 만들었어요", message = "초대 링크를 받은 사람은 바로 크루에 참여할 수 있어요.")
    val code = crew.inviteCode ?: return
    // 크루장 전용 초대 코드와 만료 시각(서버 기준 48시간)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.fillNormal.color, RoundedCornerShape(FestiCoreRadius.r12))
                .padding(vertical = FestiCoreSpacing.paddingP16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
    ) {
        Text(
            text = code,
            modifier = Modifier.semantics { contentDescription = "초대 코드 ${code.toList().joinToString(" ")}" },
            style = FestiCoreTextStyle.baseTitle1Bold.textStyle,
            color = FestiCoreSemanticColor.labelNormal.color,
        )
        crew.inviteExpiresAt?.let { expiresAt ->
            Text(
                text = "${CrewPresentation.festivalDay(expiresAt)} ${LiveBoardPresentation.time(expiresAt)}까지 쓸 수 있어요",
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                color = FestiCoreSemanticColor.labelAssistive.color,
            )
        }
    }
}

@Composable
private fun Header(title: String, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = FestiCoreTextStyle.baseTitle2Bold.textStyle,
            color = FestiCoreSemanticColor.labelNormal.color,
        )
        Text(text = message, style = FestiCoreTextStyle.baseSubheadlineRegular.textStyle, color = FestiCoreSemanticColor.labelNeutral.color)
    }
}

/** 아래 고정 버튼. 이름 단계는 만들기, 초대 단계는 링크 보내기 · 크루 보기다. */
@Composable
private fun Footer(
    state: CrewCreateState,
    onSubmitTapped: () -> Unit,
    onOpenCrewTapped: () -> Unit,
    onShareTapped: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().background(FestiCoreSemanticColor.backgroundElevated.color)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = FestiCoreSpacing.paddingP16,
                        top = FooterTopPadding,
                        end = FestiCoreSpacing.paddingP16,
                        bottom = FooterBottomPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        ) {
            when (state.step) {
                CrewCreateStep.Naming ->
                    FCButton(
                        title = if (state.isSubmitting) "만드는 중" else "만들고 초대 링크 받기",
                        isEnabled = state.canSubmit,
                        isFullWidth = true,
                        onClick = onSubmitTapped,
                    )

                is CrewCreateStep.Created -> {
                    val url = state.inviteUrl
                    if (url != null) FCButton(title = "초대 링크 보내기", isFullWidth = true, onClick = { onShareTapped(url) })
                    FCButton(
                        title = "크루 보기",
                        style = if (url == null) FCButtonStyle.Primary else FCButtonStyle.Secondary,
                        isFullWidth = true,
                        onClick = onOpenCrewTapped,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(DividerHeight).background(FestiCoreSemanticColor.lineNormal.color).align(Alignment.TopCenter))
    }
}
