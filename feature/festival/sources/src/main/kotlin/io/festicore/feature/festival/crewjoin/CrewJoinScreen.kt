package io.festicore.feature.festival.crewjoin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.domain.festival.entity.FestivalCrewInvite
import io.festicore.feature.festival.crew.CrewPresentation
import io.festicore.feature.festival.liveboard.LiveBoardPresentation
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.card.FCCardAction
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.sharedui.remoteimage.FCRemoteImage
import java.time.Instant
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** iOS `FestivalCrewJoinView.Metric`. */
private val HorizontalPadding = 20.dp
private val TopPadding = 40.dp
private val BadgeSize = 72.dp
private val IconSize = 24.dp
private val RowHeight = 55.dp
private val LastRowHeight = 54.dp
private val InlineSpacing = 6.dp
private val InlineIconTop = 1.dp
private val CardBorder = 1.dp
private val NoticeIconSize = 16.dp
private val SkeletonHeight = 154.dp
private val FooterTopPadding = 13.dp
private val FooterBottomPadding = 1.dp
private val AvatarSize = 52.dp
private val AvatarOverlap = 14.dp
private val AvatarBorder = 3.dp

/** Figma S08 A Avatar Stack 색 순서(pink · blue · orange · green). (배경, 글자) */
private val AvatarTints =
    listOf(
        FestiCoreSemanticColor.accentBackgroundPink to FestiCoreSemanticColor.accentForegroundPink,
        FestiCoreSemanticColor.accentBackgroundBlue to FestiCoreSemanticColor.accentForegroundBlue,
        FestiCoreSemanticColor.accentBackgroundOrange to FestiCoreSemanticColor.accentForegroundOrange,
        FestiCoreSemanticColor.accentBackgroundGreen to FestiCoreSemanticColor.accentForegroundGreen,
    )

/**
 * S08 크루 참여 확인(iOS `FestivalCrewJoinView`, Figma 65-3705). 라이브 탭 스택에 push로 열고 탭 바 없이 아래 고정 버튼을 둔다.
 * 닫기 · 로그인 · 참여는 경계 콜백으로 알린다. 시스템 뒤로는 `NavDisplay`가 맡는다.
 */
@Composable
fun CrewJoinScreen(
    viewModel: CrewJoinViewModel,
    onClose: () -> Unit,
    onLoginRequested: (crewName: String?) -> Unit,
    onJoined: (crewId: String, festivalId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            CrewJoinSideEffect.CloseRequested -> onClose()
            is CrewJoinSideEffect.LoginRequested -> onLoginRequested(sideEffect.crewName)
            is CrewJoinSideEffect.Joined -> onJoined(sideEffect.crewId, sideEffect.festivalId)
        }
    }
    LaunchedEffect(viewModel) { viewModel.onAppear() }

    CrewJoinContent(
        state = state,
        now = Instant.now(),
        modifier = modifier,
        onRetryTapped = viewModel::onRetryTapped,
        onCancelTapped = viewModel::onCancelTapped,
        onJoinTapped = viewModel::onJoinTapped,
    )
    state.alert?.let { alert ->
        // FCDialog는 버튼 콜백 뒤 onDismiss도 부른다. 확인으로 닫힐 때는 닫기 처리(나갈 크루 지우기)를 건너뛴다.
        var confirmed = false
        FCDialog(
            item =
                FCDialogItem(
                    title = alert.title,
                    message = alert.message,
                    confirmTitle = alert.confirmTitle,
                    cancelTitle = alert.cancelTitle,
                    onConfirm = {
                        confirmed = true
                        viewModel.onAlertConfirmed()
                    },
                ),
            onDismiss = { if (!confirmed) viewModel.onAlertDismissed() },
        )
    }
}

/** 크루 참여 확인(순수 UI). [now]로 만료 · 정원 차단을 정한다(iOS `blocker(now: .now)`). */
@Composable
fun CrewJoinContent(
    state: CrewJoinState,
    now: Instant,
    modifier: Modifier = Modifier,
    onRetryTapped: () -> Unit = {},
    onCancelTapped: () -> Unit = {},
    onJoinTapped: () -> Unit = {},
) {
    val blocker = if (state.load == CrewJoinLoad.Loaded) state.blocker(now) else null
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = HorizontalPadding, top = TopPadding, end = HorizontalPadding),
        ) {
            when (state.load) {
                CrewJoinLoad.Loading ->
                    FCSkeletonView(
                        FCSkeletonShape.Round(FestiCoreRadius.r16),
                        Modifier.fillMaxWidth().height(SkeletonHeight).semantics { contentDescription = "초대를 불러오는 중" },
                    )

                CrewJoinLoad.Unavailable ->
                    FCEmptyCard(
                        title = "초대가 만료됐어요",
                        message = "크루장에게 새 초대 링크를 받아 주세요.",
                        icon = R.drawable.ic_users_24,
                    )

                CrewJoinLoad.Failed ->
                    FCEmptyCard(
                        title = "초대를 불러오지 못했어요",
                        message = "연결을 확인한 뒤 다시 시도해 주세요.",
                        action = FCCardAction(title = "다시 시도", onClick = onRetryTapped),
                    )

                CrewJoinLoad.Loaded -> state.invite?.let { Invitation(it, blocker) }
            }
        }
        Footer(state, blocker, onCancelTapped, onJoinTapped)
    }
}

/** Figma S08 Contents: 간격 24, Hero 간격 12. */
@Composable
private fun Invitation(invite: FestivalCrewInvite, blocker: CrewJoinBlocker?) {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24)) {
        Hero(invite, isBlocked = blocker != null)
        if (blocker != null) InlineMessage(blocker, invite.inviterNickname)
        DetailCard(invite, isExpired = blocker == CrewJoinBlocker.Expired)
        if (blocker == null) Notice()
    }
}

@Composable
private fun Hero(invite: FestivalCrewInvite, isBlocked: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
    ) {
        if (invite.memberPreview.isEmpty()) {
            // 크루원 미리보기가 없던 이전 서버 응답은 아이콘 원으로 둔다.
            Box(
                modifier = Modifier.size(BadgeSize).background(FestiCoreSemanticColor.fillNormal.color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_users_24),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNormal.color),
                )
            }
        } else {
            AvatarStack(invite)
        }
        Text(
            text = invite.inviterNickname?.let { "${it}님이 크루에 초대했어요" } ?: "크루에 초대받았어요",
            style = FestiCoreTextStyle.baseSubheadlineRegular.textStyle,
            color = FestiCoreSemanticColor.labelNeutral.color,
        )
        // Figma S08 크루 이름: Point/Title. 참여할 수 없으면 Label/Assistive로 흐리게 둔다.
        Text(
            text = invite.crewName,
            style = FestiCoreTextStyle.pointTitle.textStyle,
            color = if (isBlocked) FestiCoreSemanticColor.labelAssistive.color else FestiCoreSemanticColor.labelNormal.color,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Figma S08 A Avatar Stack: 52 원을 14씩 겹치고 Background/Normal 3 테두리로 나눈다.
 * 서버는 먼저 들어온 3명만 주므로 나머지 인원은 "+N" 원으로 둔다.
 */
@Composable
private fun AvatarStack(invite: FestivalCrewInvite) {
    val extra = invite.memberCount - invite.memberPreview.size
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "크루원 ${invite.memberCount}명" },
        horizontalArrangement = Arrangement.spacedBy(-AvatarOverlap),
    ) {
        invite.memberPreview.forEachIndexed { index, member ->
            Avatar(member, AvatarTints[index % AvatarTints.size])
        }
        if (extra > 0) {
            AvatarCircle("+$extra", FestiCoreSemanticColor.fillStrong to FestiCoreSemanticColor.labelNeutral)
        }
    }
}

@Composable
private fun Avatar(member: FestivalCrewInvite.Member, tint: Pair<FestiCoreSemanticColor, FestiCoreSemanticColor>) {
    val url = member.avatarUrl
    if (url != null) {
        FCRemoteImage(
            url = url,
            modifier =
                Modifier
                    .size(AvatarSize)
                    .clip(CircleShape)
                    .border(AvatarBorder, FestiCoreSemanticColor.backgroundNormal.color, CircleShape),
            placeholderIcon = R.drawable.ic_users_24,
            placeholderIconSize = IconSize,
        )
    } else {
        AvatarCircle(member.nickname.take(1), tint)
    }
}

/** Figma Avatar(40 원 Subheadline/Bold)를 52로 키운 S08 인스턴스. */
@Composable
private fun AvatarCircle(text: String, colors: Pair<FestiCoreSemanticColor, FestiCoreSemanticColor>) {
    Box(
        modifier =
            Modifier
                .size(AvatarSize)
                .background(colors.first.color, CircleShape)
                .border(AvatarBorder, FestiCoreSemanticColor.backgroundNormal.color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = FestiCoreTextStyle.baseSubheadlineBold.textStyle, color = colors.second.color)
    }
}

/** Figma S08 Inline Message: 아이콘 16 + 간격 6, Footnote/Regular · Status/Negative. */
@Composable
private fun InlineMessage(blocker: CrewJoinBlocker, inviter: String?) {
    val text =
        when (blocker) {
            CrewJoinBlocker.Expired -> "초대 링크가 만료됐어요 · ${inviter?.let { "${it}님" } ?: "크루장"}에게 새 링크를 받아 주세요"
            CrewJoinBlocker.Full -> "크루가 가득 찼어요 · 최대 ${FestivalCrew.MAX_MEMBERS}명까지 들어갈 수 있어요"
        }
    val color = FestiCoreSemanticColor.statusNegative.color
    Row(horizontalArrangement = Arrangement.spacedBy(InlineSpacing)) {
        Image(
            painter = painterResource(R.drawable.ic_triangle_exclamation_16),
            contentDescription = null,
            modifier = Modifier.padding(top = InlineIconTop).size(NoticeIconSize),
            colorFilter = ColorFilter.tint(color),
        )
        Text(text = text, modifier = Modifier.weight(1f), style = FestiCoreTextStyle.baseFootnoteRegular.textStyle, color = color)
    }
}

/** Figma S08 Info Card: 테두리 없이 Background/Elevated, 줄마다 위아래 16. */
@Composable
private fun DetailCard(invite: FestivalCrewInvite, isExpired: Boolean) {
    val expiry = "${CrewPresentation.festivalDay(invite.expiresAt)} ${LiveBoardPresentation.time(invite.expiresAt)}"
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))
                .padding(horizontal = FestiCoreSpacing.paddingP16),
    ) {
        DetailRow("페스티벌", invite.festivalName.orEmpty(), showsDivider = true)
        DetailRow("인원", "${invite.memberCount} / ${FestivalCrew.MAX_MEMBERS}명", showsDivider = true)
        DetailRow("초대 만료", if (isExpired) "$expiry · 만료" else expiry, showsDivider = false)
    }
}

@Composable
private fun DetailRow(
    title: String,
    value: String,
    showsDivider: Boolean,
) {
    Box(modifier = Modifier.fillMaxWidth().height(if (showsDivider) RowHeight else LastRowHeight)) {
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = FestiCoreTextStyle.baseSubheadlineRegular.textStyle,
                color = FestiCoreSemanticColor.labelNeutral.color,
            )
            Spacer(Modifier.weight(1f).widthIn(min = FestiCoreSpacing.paddingP8))
            Text(
                text = value,
                modifier = Modifier.padding(start = FestiCoreSpacing.paddingP8),
                style = FestiCoreTextStyle.baseSubheadlineBold.textStyle,
                color = FestiCoreSemanticColor.labelNormal.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showsDivider) {
            Box(
                Modifier.fillMaxWidth().height(
                    CardBorder,
                ).background(FestiCoreSemanticColor.lineNormal.color).align(Alignment.BottomCenter),
            )
        }
    }
}

/** Figma S08 Notice: 사방 12, Fill/Strong. */
@Composable
private fun Notice() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(FestiCoreSemanticColor.fillStrong.color, RoundedCornerShape(FestiCoreRadius.r12))
                .padding(FestiCoreSpacing.paddingP12),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_circle_info_16),
            contentDescription = null,
            modifier = Modifier.size(NoticeIconSize),
            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNeutral.color),
        )
        Text(
            text = "참여하면 크루원에게 내 존과 상태가 보여요. 정확한 위치(GPS)는 공유하지 않아요.",
            modifier = Modifier.weight(1f),
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
            color = FestiCoreSemanticColor.labelNeutral.color,
        )
    }
}

/** 참여할 수 없는 초대(S08 B · C)는 닫기 하나만 둔다. Figma S08 Footer는 화면 배경 위에 위 테두리만 둔다. */
@Composable
private fun Footer(
    state: CrewJoinState,
    blocker: CrewJoinBlocker?,
    onCancelTapped: () -> Unit,
    onJoinTapped: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        Row(
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
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        ) {
            if (blocker != null) {
                FCButton(title = "닫기", isFullWidth = true, onClick = onCancelTapped)
            } else {
                FCButton(title = "취소", style = FCButtonStyle.Ghost, onClick = onCancelTapped)
                FCButton(
                    title = if (state.isJoining) "참여하는 중" else "참여하기",
                    modifier = Modifier.weight(1f),
                    isEnabled = state.load == CrewJoinLoad.Loaded && !state.isJoining,
                    isFullWidth = true,
                    onClick = onJoinTapped,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(CardBorder).background(FestiCoreSemanticColor.lineNormal.color).align(Alignment.TopCenter))
    }
}
