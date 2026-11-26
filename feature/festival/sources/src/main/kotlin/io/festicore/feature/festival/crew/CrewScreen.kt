package io.festicore.feature.festival.crew

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewGatherResponse
import io.festicore.domain.festival.entity.FestivalCrewState
import io.festicore.feature.festival.liveboard.LiveBoardPresentation
import io.festicore.feature.festival.liveboard.crewcreate.shareInvite
import io.festicore.feature.festival.liveboard.crewgather.CrewGatherSheet
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCBigButton
import io.festicore.ui.designkit.component.action.FCBigButtonSize
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.card.FCCardAction
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCZoneChip
import io.festicore.ui.designkit.component.media.FCAvatar
import io.festicore.ui.designkit.component.modal.FCSheet
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.overlay.FCBanner
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.component.selection.FCTextField
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.component.toast.FCToast
import io.festicore.ui.designkit.component.toast.FCToastItem
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** iOS `FestivalCrewView.Metric` · `FestivalCrewMenuView.Metric`. 토큰에 없는 화면 고유 값만 둔다. */
private val SummaryHeight = 40.dp
private val StatusSpacing = 10.dp
private val EmptyBadgeSize = 48.dp
private val EmptyIconSize = 24.dp
private val SkeletonHeight = 322.dp
private val BorderWidth = 1.dp
private const val INACTIVE_OPACITY = 0.6f
private val ToastTop = 30.dp
private const val TOAST_MILLIS = 250
private val GatherIconSize = 20.dp

/** Figma S07 F 응답 버튼 모서리 10. 같은 값의 Radius 토큰이 없어 화면 값으로 둔다(iOS와 같음). */
private val GatherActionRadius = 10.dp
private val GatherActionVertical = 10.dp
internal val MenuTitleSpacing = 4.dp
internal val MenuRowSpacing = 4.dp
private val MenuRowHeight = 59.dp
private val MenuLastRowHeight = 58.dp
private val MenuIconSize = 20.dp

/**
 * S07 크루(iOS `FestivalCrewView`). 들어오고 나갈 때 [CrewViewModel.onAppear] · [CrewViewModel.onDisappear]를 보낸다.
 * 뒤로 · 크루 닫힘은 경계 콜백으로 알리고, 초대 링크는 시스템 공유 시트로 보낸다. 시스템 뒤로는 `NavDisplay`가 맡는다.
 */
@Composable
fun CrewScreen(
    viewModel: CrewViewModel,
    onBack: () -> Unit,
    onCrewClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    val context = LocalContext.current
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            CrewSideEffect.BackRequested -> onBack()
            CrewSideEffect.CrewClosed -> onCrewClosed()
        }
    }
    DisposableEffect(viewModel) {
        viewModel.onAppear()
        onDispose { viewModel.onDisappear() }
    }

    CrewContent(
        state = state,
        modifier = modifier,
        onBackTapped = viewModel::onBackTapped,
        onMenuTapped = viewModel::onMenuTapped,
        onGatherTapped = viewModel::onGatherTapped,
        onStateTapped = viewModel::onStateTapped,
        onGatherResponseTapped = viewModel::onGatherResponseTapped,
        onRetryTapped = viewModel::onRetryTapped,
        onShareTapped = { url -> shareInvite(context, url) },
    )

    CrewGatherSheet(
        input = (state.destination as? CrewDestination.Gather)?.input,
        onDismiss = viewModel::onDestinationDismissed,
        onGathered = viewModel::onGathered,
    )
    val menu = (state.destination as? CrewDestination.Menu)?.menu
    FCSheet(item = menu, onDismissRequest = viewModel::onDestinationDismissed) { item ->
        CrewMenuContent(
            menu = item,
            onRenameTapped = viewModel::onMenuRenameTapped,
            onRotateInviteTapped = viewModel::onMenuRotateInviteTapped,
            onTransferTapped = viewModel::onMenuTransferTapped,
            onRemoveTapped = viewModel::onMenuRemoveTapped,
            onDissolveTapped = viewModel::onMenuDissolveTapped,
            onNameDraftChanged = viewModel::onMenuNameDraftChanged,
            onSaveNameTapped = viewModel::onMenuSaveNameTapped,
            onMemberTapped = viewModel::onMenuMemberTapped,
            onBackTapped = viewModel::onMenuBackTapped,
        )
    }
    when (val destination = state.destination) {
        is CrewDestination.Alert ->
            FCDialog(
                item = FCDialogItem(title = destination.title, message = destination.message, cancelTitle = "닫기", onConfirm = {}),
                onDismiss = viewModel::onDestinationDismissed,
            )

        is CrewDestination.Confirm ->
            FCDialog(
                item =
                    FCDialogItem(
                        title = destination.title,
                        message = destination.message,
                        confirmTitle = destination.confirmTitle,
                        onConfirm = viewModel::onConfirmTapped,
                    ),
                onDismiss = viewModel::onDestinationDismissed,
            )

        else -> Unit
    }
}

/** 크루 메인(순수 UI). 문구는 [CrewPresentation]이 [CrewState.now]로 만든다. */
@Composable
fun CrewContent(
    state: CrewState,
    modifier: Modifier = Modifier,
    onBackTapped: () -> Unit = {},
    onMenuTapped: () -> Unit = {},
    onGatherTapped: () -> Unit = {},
    onStateTapped: (FestivalCrewState) -> Unit = {},
    onGatherResponseTapped: (FestivalCrewGatherResponse) -> Unit = {},
    onRetryTapped: () -> Unit = {},
    onShareTapped: (String) -> Unit = {},
) {
    val presentation = CrewPresentation.of(state, state.now)
    Box(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        Column(modifier = Modifier.fillMaxSize()) {
            FCTopBar(
                style = FCTopBarStyle.Back,
                title = state.title,
                backgroundColor = FestiCoreSemanticColor.backgroundNavigation,
                // Figma S07 A: Top Bar 오른쪽 Action은 gear 24(Label/Neutral)다.
                trailingIcon = if (state.isLeader) R.drawable.ic_gear_24 else null,
                trailingAccessibilityLabel = if (state.isLeader) "크루장 메뉴" else null,
                onBack = onBackTapped,
                onTrailingAction = if (state.isLeader) onMenuTapped else null,
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(FestiCoreSpacing.paddingP16),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
            ) {
                if (presentation.isArchived) {
                    // Figma S07 I: 끝난 크루는 안내 띠와 기록만 둔다.
                    val festival = state.festivalName?.let { "${it.withIGa()} " } ?: "페스티벌이 "
                    FCBanner(text = "${festival}끝났어요 · 기록만 볼 수 있어요", icon = R.drawable.ic_circle_info_16, showsChevron = false, onClick = {
                    })
                    SummaryText(presentation.summary, Modifier.fillMaxWidth())
                } else {
                    presentation.gather?.let { GatherBanner(it, onGatherResponseTapped) }
                    SummaryRow(presentation.summary, state.inviteUrl, onShareTapped)
                    FCButton(
                        title = presentation.gatherTitle,
                        isEnabled = presentation.canGather,
                        isFullWidth = true,
                        onClick = onGatherTapped,
                    )
                }
                Members(state, presentation, onRetryTapped, onShareTapped)
            }
            if (!presentation.isArchived) StatusPanel(state, onStateTapped)
        }
        CrewToastView(state.toast, Modifier.align(Alignment.TopCenter))
    }
}

/** Figma S07 F Gather Banner: Static/Lime, 사방 16, 간격 12, 모서리 16. */
@Composable
private fun GatherBanner(gather: CrewPresentation.Gather, onGatherResponseTapped: (FestivalCrewGatherResponse) -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.staticLime.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_users_24),
                contentDescription = null,
                modifier = Modifier.size(GatherIconSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.staticInk.color),
            )
            Text(
                text = gather.title,
                style = FestiCoreTextStyle.baseHeadlineBold.textStyle,
                color = FestiCoreSemanticColor.staticInk.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = gather.detail,
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
            color = FestiCoreSemanticColor.staticalphaBlack60.color,
        )
        val actions = gather.actions
        if (actions != null) {
            // Figma S07 F Actions: 가는 중(남은 너비) · 확인, 간격 8. 고른 응답을 Static/Ink로 칠한다.
            val goingSelected = actions.selected != FestivalCrewGatherResponse.ACK
            Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                GatherPill(
                    title = actions.goingTitle,
                    isSelected = goingSelected,
                    modifier = Modifier.weight(1f),
                    isChosen = actions.selected == FestivalCrewGatherResponse.GOING,
                    isEnabled = !actions.isResponding,
                    onClick = { onGatherResponseTapped(FestivalCrewGatherResponse.GOING) },
                )
                GatherPill(
                    title = "확인",
                    isSelected = !goingSelected,
                    isChosen = actions.selected == FestivalCrewGatherResponse.ACK,
                    isEnabled = !actions.isResponding,
                    onClick = { onGatherResponseTapped(FestivalCrewGatherResponse.ACK) },
                )
            }
        } else {
            gather.summary?.let { summary ->
                GatherPill(
                    title = summary,
                    isSelected = false,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "응답 $summary" },
                )
            }
        }
    }
}

/** Figma S07 F 응답 버튼: 위아래 10 · 좌우 16, 모서리 10, Subheadline/Bold. */
@Composable
private fun GatherPill(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    isChosen: Boolean = false,
    isEnabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val base =
        modifier
            .background(
                if (isSelected) FestiCoreSemanticColor.staticInk.color else FestiCoreSemanticColor.staticalphaBlack20.color,
                RoundedCornerShape(GatherActionRadius),
            )
    val clickable =
        if (onClick !=
            null
        ) {
            base.semantics { selected = isChosen }.clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
        } else {
            base
        }
    Text(
        text = title,
        modifier = clickable.padding(vertical = GatherActionVertical, horizontal = FestiCoreSpacing.paddingP16),
        style = FestiCoreTextStyle.baseSubheadlineBold.textStyle,
        color = if (isSelected) FestiCoreSemanticColor.staticWhite.color else FestiCoreSemanticColor.staticInk.color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SummaryRow(
    summary: String,
    inviteUrl: String?,
    onShareTapped: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(SummaryHeight),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryText(summary, Modifier.weight(1f))
        if (inviteUrl != null) InviteButton("초대 링크", inviteUrl, onShareTapped)
    }
}

/** Figma S07 Summary 글자: Footnote/Regular · Label/Neutral. */
@Composable
private fun SummaryText(summary: String, modifier: Modifier) {
    Text(
        text = summary,
        modifier = modifier,
        style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
        color = FestiCoreSemanticColor.labelNeutral.color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 초대 링크는 시스템 공유 시트로 보낸다. 모양은 보조 버튼과 같다. */
@Composable
private fun InviteButton(
    title: String,
    url: String,
    onShareTapped: (String) -> Unit,
) {
    FCButton(
        title = title,
        modifier = Modifier.semantics { contentDescription = "초대 링크 공유" },
        style = FCButtonStyle.Secondary,
        size = FCButtonSize.Medium,
        onClick = { onShareTapped(url) },
    )
}

@Composable
private fun Members(
    state: CrewState,
    presentation: CrewPresentation,
    onRetryTapped: () -> Unit,
    onShareTapped: (String) -> Unit,
) {
    when {
        presentation.members.isEmpty() && state.load == CrewLoad.Loading ->
            FCSkeletonView(
                FCSkeletonShape.Round(FestiCoreRadius.r16),
                Modifier.fillMaxWidth().height(SkeletonHeight).semantics { contentDescription = "크루원을 불러오는 중" },
            )

        presentation.members.isEmpty() && state.load == CrewLoad.Failed ->
            FCEmptyCard(
                title = "크루를 불러오지 못했어요",
                message = "연결을 확인한 뒤 다시 시도해 주세요.",
                action = FCCardAction(title = "다시 시도", onClick = onRetryTapped),
            )

        else ->
            // Figma S07 Crew Card: 머리(크루원 · 4 / 12) 아래 크루원 줄. 테두리 없이 Background/Elevated
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16)),
            ) {
                CardHeader(presentation.memberCount)
                presentation.members.forEach { MemberRow(it) }
                if (presentation.members.size == 1 && !presentation.isArchived) AloneEmpty(state.inviteUrl, onShareTapped)
            }
    }
}

/** Figma S07 Crew Card Header: 위 16 · 아래 4 · 좌우 16. */
@Composable
private fun CardHeader(memberCount: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = FestiCoreSpacing.paddingP16,
                    top = FestiCoreSpacing.paddingP16,
                    end = FestiCoreSpacing.paddingP16,
                    bottom = FestiCoreSpacing.paddingP4,
                ),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "크루원",
            modifier = Modifier.semantics { heading() },
            style = FestiCoreTextStyle.baseHeadlineBold.textStyle,
            color = FestiCoreSemanticColor.labelNormal.color,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = memberCount,
            style = FestiCoreTextStyle.baseFootnoteMedium.textStyle,
            color = FestiCoreSemanticColor.labelAssistive.color,
        )
    }
}

/** Figma S07 C 혼자만 있음: 아이콘 원 48, 제목, 안내, 초대 링크 보내기. */
@Composable
private fun AloneEmpty(inviteUrl: String?, onShareTapped: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = FestiCoreSpacing.paddingP24, horizontal = FestiCoreSpacing.paddingP16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
    ) {
        Box(
            modifier = Modifier.size(EmptyBadgeSize).background(FestiCoreSemanticColor.fillNormal.color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_users_24),
                contentDescription = null,
                modifier = Modifier.size(EmptyIconSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNeutral.color),
            )
        }
        Text(
            text = "아직 아무도 안 들어왔어요",
            style = FestiCoreTextStyle.baseHeadlineBold.textStyle,
            color = FestiCoreSemanticColor.labelNormal.color,
        )
        Text(
            text = "초대 링크를 단톡방에 보내 보세요 · 48시간 유효",
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
            color = FestiCoreSemanticColor.labelNeutral.color,
            textAlign = TextAlign.Center,
        )
        if (inviteUrl != null) InviteButton("초대 링크 보내기", inviteUrl, onShareTapped)
    }
}

/** `FCCrewRow`와 같은 구성. 존이 없으면 칩을 그리지 않는다(Figma S07 Crew Row). */
@Composable
private fun MemberRow(member: CrewPresentation.Member) {
    val primary = if (member.isInactive) FestiCoreSemanticColor.labelAssistive else FestiCoreSemanticColor.labelNormal
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (member.isInactive) INACTIVE_OPACITY else 1f)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCAvatar(initial = member.nickname)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.nickname,
                    style = FestiCoreTextStyle.baseCalloutBold.textStyle,
                    color = primary.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.isMe) FCTag("나")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8), verticalAlignment = Alignment.CenterVertically) {
                member.zone?.let { FCZoneChip(zone = it, onClick = {}) }
                member.status?.let {
                    Text(text = it, style = FestiCoreTextStyle.baseFootnoteBold.textStyle, color = primary.color, maxLines = 1)
                }
            }
        }
        member.time?.let {
            Text(
                text = it,
                style = FestiCoreTextStyle.baseCaption1Regular.textStyle,
                color = if (member.isInactive) FestiCoreSemanticColor.labelDisable.color else FestiCoreSemanticColor.labelAssistive.color,
            )
        }
    }
}

/** Figma S07 My Status: 위 12 · 아래 8 · 좌우 16, 간격 10, 안내 Caption1/Medium. */
@Composable
private fun StatusPanel(state: CrewState, onStateTapped: (FestivalCrewState) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().background(FestiCoreSemanticColor.backgroundElevated.color)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = FestiCoreSpacing.paddingP16,
                        top = FestiCoreSpacing.paddingP12,
                        end = FestiCoreSpacing.paddingP16,
                        bottom = FestiCoreSpacing.paddingP8,
                    ),
            verticalArrangement = Arrangement.spacedBy(StatusSpacing),
        ) {
            Text(
                text = "내 상태 · 탭 한 번이면 크루원에게 공유돼요",
                style = FestiCoreTextStyle.baseCaption1Medium.textStyle,
                color = FestiCoreSemanticColor.labelAssistive.color,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                FestivalCrewState.entries.forEach { crewState ->
                    // Figma S07 Crew Status Button = Big Button small(80×64)
                    FCBigButton(
                        label = LiveBoardPresentation.crewStateText(crewState.rawValue).orEmpty(),
                        sub = "",
                        icon = stateIcon(crewState),
                        modifier = Modifier.weight(1f),
                        isSelected = state.myState == crewState,
                        size = FCBigButtonSize.Small,
                        onClick = { if (!state.isArchived) onStateTapped(crewState) },
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(BorderWidth).background(FestiCoreSemanticColor.lineNormal.color).align(Alignment.TopCenter))
    }
}

@DrawableRes
private fun stateIcon(crewState: FestivalCrewState): Int = when (crewState) {
    FestivalCrewState.HERE -> R.drawable.ic_location_dot_20
    FestivalCrewState.EATING -> R.drawable.ic_clock_20
    FestivalCrewState.TOILET -> R.drawable.ic_circle_info_20
    FestivalCrewState.LEAVING -> R.drawable.ic_route_24
}

/** 위에서 내려오는 토스트(위 30). 만료(2초)는 ViewModel이 맡는다. 사라지는 동안 마지막 문구를 둔다. */
@Composable
private fun CrewToastView(toast: CrewToast?, modifier: Modifier) {
    var last by remember { mutableStateOf(toast) }
    if (toast != null) last = toast
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier.padding(top = ToastTop),
        enter = slideInVertically(tween(TOAST_MILLIS, easing = EaseOut)) { -it } + fadeIn(tween(TOAST_MILLIS, easing = EaseOut)),
        exit = slideOutVertically(tween(TOAST_MILLIS, easing = EaseOut)) { -it } + fadeOut(tween(TOAST_MILLIS, easing = EaseOut)),
    ) {
        last?.let { FCToast(toastItem(it)) }
    }
}

private fun toastItem(toast: CrewToast): FCToastItem = when (toast) {
    CrewToast.InviteRotated -> FCToastItem(icon = R.drawable.ic_check, message = "새 초대 링크를 만들었어요")
    CrewToast.Renamed -> FCToastItem(icon = R.drawable.ic_check, message = "크루 이름을 바꿨어요")
    is CrewToast.Transferred -> FCToastItem(icon = R.drawable.ic_check, message = "${toast.nickname}님이 크루장이 됐어요")
    is CrewToast.Removed -> FCToastItem(icon = R.drawable.ic_check, message = "${toast.nickname}님을 내보냈어요")
    CrewToast.StatusFailed -> FCToastItem(icon = R.drawable.ic_circle_info_16, message = "상태를 보내지 못했어요. 다시 눌러 주세요")
    CrewToast.GatherExpired -> FCToastItem(icon = R.drawable.ic_circle_info_16, message = "모여 요청이 끝나서 응답하지 못했어요")
}

/** 크루장 메뉴 시트 내용(iOS `FestivalCrewMenuView`). 이름 바꾸기와 크루원 고르기는 같은 시트 안에서 이어진다. */
@Composable
fun CrewMenuContent(
    menu: CrewMenuState,
    modifier: Modifier = Modifier,
    onRenameTapped: () -> Unit = {},
    onRotateInviteTapped: () -> Unit = {},
    onTransferTapped: () -> Unit = {},
    onRemoveTapped: () -> Unit = {},
    onDissolveTapped: () -> Unit = {},
    onNameDraftChanged: (String) -> Unit = {},
    onSaveNameTapped: () -> Unit = {},
    onMemberTapped: (String) -> Unit = {},
    onBackTapped: () -> Unit = {},
) {
    val title =
        when (menu.step) {
            CrewMenuStep.Menu -> "크루 관리"
            CrewMenuStep.Rename -> "크루 이름 바꾸기"
            CrewMenuStep.Transfer -> "누구에게 크루장을 넘길까요?"
            CrewMenuStep.Remove -> "누구를 내보낼까요?"
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(MenuTitleSpacing),
    ) {
        Text(text = title, style = FestiCoreTextStyle.baseHeadlineBold.textStyle, color = FestiCoreSemanticColor.labelNormal.color)
        when (menu.step) {
            CrewMenuStep.Menu -> {
                Column(verticalArrangement = Arrangement.spacedBy(MenuRowSpacing)) {
                    MenuRow("크루 이름 바꾸기", R.drawable.ic_list_check_20, onClick = onRenameTapped)
                    MenuRow("초대 링크 다시 만들기", R.drawable.ic_share_nodes, onClick = onRotateInviteTapped)
                    MenuRow("크루장 넘기기", R.drawable.ic_users_24, isEnabled = menu.members.isNotEmpty(), onClick = onTransferTapped)
                    MenuRow("크루원 내보내기", R.drawable.ic_user_24, isEnabled = menu.members.isNotEmpty(), onClick = onRemoveTapped)
                    MenuRow(
                        "크루 해산하기",
                        R.drawable.ic_circle_xmark,
                        isLast = true,
                        tint = FestiCoreSemanticColor.labelNeutral,
                        iconTint = FestiCoreSemanticColor.labelAssistive,
                        onClick = onDissolveTapped,
                    )
                    Text(
                        text = "초대 링크를 다시 만들면 이전 링크는 바로 막혀요. 크루장은 넘기거나 해산해야 나갈 수 있어요.",
                        style = FestiCoreTextStyle.baseCaption1Regular.textStyle,
                        color = FestiCoreSemanticColor.labelAssistive.color,
                    )
                }
            }

            CrewMenuStep.Rename ->
                Column(
                    modifier = Modifier.padding(bottom = FestiCoreSpacing.paddingP16),
                    verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
                ) {
                    FCTextField(
                        text = menu.nameDraft,
                        onTextChange = onNameDraftChanged,
                        placeholder = "크루 이름",
                        maxLength = FestivalCrew.MAX_NAME_LENGTH,
                        counter = "${menu.nameDraft.length} / ${FestivalCrew.MAX_NAME_LENGTH}",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                        FCButton(title = "이전", style = FCButtonStyle.Secondary, onClick = onBackTapped)
                        FCButton(
                            title = "저장",
                            modifier = Modifier.weight(1f),
                            isEnabled = menu.canSaveName,
                            isFullWidth = true,
                            onClick = onSaveNameTapped,
                        )
                    }
                }

            CrewMenuStep.Transfer, CrewMenuStep.Remove ->
                Column {
                    menu.members.forEach { member ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(MenuRowHeight)
                                    .clickable(role = Role.Button) { onMemberTapped(member.id) },
                            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FCAvatar(initial = member.nickname)
                            Text(
                                text = member.nickname,
                                style = FestiCoreTextStyle.baseBodyRegular.textStyle,
                                color = FestiCoreSemanticColor.labelNormal.color,
                            )
                        }
                    }
                    FCButton(
                        title = "이전",
                        modifier = Modifier.padding(vertical = FestiCoreSpacing.paddingP16),
                        style = FCButtonStyle.Secondary,
                        isFullWidth = true,
                        onClick = onBackTapped,
                    )
                }
        }
    }
}

/** 메뉴 시트 한 줄(Figma S07 G). 깃발 상세 기수 메뉴도 같은 줄을 쓴다. */
@Composable
internal fun MenuRow(
    title: String,
    @DrawableRes icon: Int,
    isEnabled: Boolean = true,
    isLast: Boolean = false,
    tint: FestiCoreSemanticColor = FestiCoreSemanticColor.labelNormal,
    iconTint: FestiCoreSemanticColor? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (isLast) MenuLastRowHeight else MenuRowHeight)
                .alpha(if (isEnabled) 1f else FestiCoreState.disabledOpacity)
                .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = FestiCoreSpacing.paddingP4),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(MenuIconSize),
                colorFilter = ColorFilter.tint((iconTint ?: tint).color),
            )
            Text(text = title, style = FestiCoreTextStyle.baseBodyRegular.textStyle, color = tint.color, maxLines = 1)
        }
        if (!isLast) {
            Box(
                Modifier.fillMaxWidth().height(
                    BorderWidth,
                ).background(FestiCoreSemanticColor.lineNormal.color).align(Alignment.BottomCenter),
            )
        }
    }
}
