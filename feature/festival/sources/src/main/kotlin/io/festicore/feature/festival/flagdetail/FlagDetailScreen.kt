package io.festicore.feature.festival.flagdetail

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.festicore.domain.festival.FestivalFlagDetail
import io.festicore.domain.festival.FestivalFlagReportReason
import io.festicore.feature.festival.FestivalAccessibilityId
import io.festicore.feature.festival.flag.FlagPresentation
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.card.FCFlagHero
import io.festicore.ui.designkit.component.card.FCFlagNowCard
import io.festicore.ui.designkit.component.card.FCFlagNowCardState
import io.festicore.ui.designkit.component.card.FCPlanPrivate
import io.festicore.ui.designkit.component.label.FCOfflineBadge
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.list.FCPlanRow
import io.festicore.ui.designkit.component.list.FCPlanRowState
import io.festicore.ui.designkit.component.list.FCSectionHeader
import io.festicore.ui.designkit.component.modal.FCSheet
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.component.toast.FCToast
import io.festicore.ui.designkit.component.toast.FCToastItem
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.sharedui.remoteimage.FCRemoteImage
import io.festicore.ui.sharedui.sheet.FCReportSheet
import io.festicore.ui.sharedui.toast.FCToastItems
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** iOS `FestivalFlagDetailView` · `FestivalFlagDetailSkeletonView.Metric`. 토큰에 없는 고유 크기만 둔다. */
private val PlaceholderIconSize = 36.dp
private val SkeletonHeroHeight = 360.dp
private val SkeletonNowCardHeight = 106.dp
private val SkeletonPlanListHeight = 286.dp
private const val TOAST_MILLIS = 250

/** 신고 사유 순서(iOS `FestivalFlagReportReason.allCases`). */
private val ReportReasons = FestivalFlagReportReason.entries

/**
 * S05 깃발 상세(iOS `FestivalFlagDetailView`). 화면에 들어오고 나갈 때 [FlagDetailViewModel.onAppeared] · [FlagDetailViewModel.onDisappeared]를 보낸다.
 * 뒤로 · 로그인 · 약관 재동의 · 팔로우 변경은 경계 콜백으로만 알리고, 공유는 시스템 공유 시트로 연다.
 */
@Composable
fun FlagDetailScreen(
    viewModel: FlagDetailViewModel,
    onBack: () -> Unit,
    onLoginRequested: () -> Unit,
    onReportLoginRequested: () -> Unit,
    onTermsAcceptanceRequested: () -> Unit,
    onFollowChanged: (FlagDetailSideEffect.FollowChanged) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.collectAsState()
    val context = LocalContext.current
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            FlagDetailSideEffect.LoginRequested -> onLoginRequested()

            FlagDetailSideEffect.ReportLoginRequested -> onReportLoginRequested()

            FlagDetailSideEffect.TermsAcceptanceRequested -> onTermsAcceptanceRequested()

            is FlagDetailSideEffect.FollowChanged -> onFollowChanged(sideEffect)

            is FlagDetailSideEffect.ShareRequested -> {
                val send =
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, sideEffect.url)
                        .putExtra(Intent.EXTRA_TITLE, sideEffect.title)
                context.startActivity(Intent.createChooser(send, sideEffect.title))
            }
        }
    }
    DisposableEffect(viewModel) {
        viewModel.onAppeared()
        onDispose { viewModel.onDisappeared() }
    }
    FlagDetailContent(
        state = state,
        modifier = modifier,
        onBack = onBack,
        onShareTapped = viewModel::onShareTapped,
        onRetryTapped = viewModel::onRetryTapped,
        onFollowTapped = viewModel::onFollowTapped,
        onReportTapped = viewModel::onReportTapped,
        onReportReasonSelected = viewModel::onReportReasonSelected,
        onReportSubmitTapped = viewModel::onReportSubmitTapped,
        onReportSheetDismissed = viewModel::onReportSheetDismissed,
    )
}

/** State만 받는 깃발 상세 UI. Preview가 쓴다. */
@Composable
fun FlagDetailContent(
    state: FlagDetailState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onShareTapped: () -> Unit = {},
    onRetryTapped: () -> Unit = {},
    onFollowTapped: () -> Unit = {},
    onReportTapped: () -> Unit = {},
    onReportReasonSelected: (FestivalFlagReportReason) -> Unit = {},
    onReportSubmitTapped: () -> Unit = {},
    onReportSheetDismissed: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    var topBarHeight by remember { mutableIntStateOf(0) }
    var heroHeight by remember { mutableIntStateOf(0) }
    val offlineText = FlagDetailPresentation.offlineText(state)
    // 스크롤이 상단 바 높이 이상 · 히어로 높이 이하일 때만 사진 위 스타일. 오프라인 배지가 보이면 기본 스타일로 둔다.
    val offset = scrollState.value
    val isOnImage = state.detail != null && topBarHeight > 0 && offset >= topBarHeight && offset <= heroHeight && offlineText == null
    Box(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.onSizeChanged { topBarHeight = it.height }) {
                val detail = state.detail
                FCTopBar(
                    style = if (isOnImage) FCTopBarStyle.BackOnImage else FCTopBarStyle.Back,
                    title = detail?.name.orEmpty(),
                    trailingIcon = R.drawable.ic_share_nodes,
                    trailingAccessibilityLabel = "공유",
                    onBack = onBack,
                    onTrailingAction = if (detail != null) onShareTapped else null,
                )
                if (offlineText != null) FCOfflineBadge(offlineText)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val content = state.content) {
                    FlagDetailContent.Loading -> Skeleton()

                    is FlagDetailContent.Failed ->
                        when (content.failure) {
                            FlagDetailFailure.NotFound -> NotFound()
                            FlagDetailFailure.Unavailable -> Retry(onRetryTapped)
                        }

                    is FlagDetailContent.Loaded ->
                        Loaded(
                            state = state,
                            detail = content.detail,
                            scrollState = scrollState,
                            onHeroHeightChanged = { heroHeight = it },
                            onFollowTapped = onFollowTapped,
                            onReportTapped = onReportTapped,
                        )
                }
            }
        }
        Toast(toast = state.toast, modifier = Modifier.align(Alignment.BottomCenter))
    }
    ReportSheet(
        sheet = state.reportSheet,
        onReasonSelected = onReportReasonSelected,
        onSubmit = onReportSubmitTapped,
        onDismiss = onReportSheetDismissed,
    )
}

@Composable
private fun Loaded(
    state: FlagDetailState,
    detail: FestivalFlagDetail,
    scrollState: ScrollState,
    onHeroHeightChanged: (Int) -> Unit,
    onFollowTapped: () -> Unit,
    onReportTapped: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).fcScrollBottomInset()) {
        FCFlagHero(
            name = detail.name,
            followers = FlagPresentation.followersText(state.followerCount),
            tags = FlagDetailPresentation.tagTitles(state),
            status = chipStatus(detail.status),
            intro = detail.bio?.let { "“$it”" }.orEmpty(),
            isFollowing = state.isFollowing,
            modifier = Modifier.onSizeChanged { onHeroHeightChanged(it.height) },
            isFollowEnabled = !state.isOffline && !state.isFollowPending,
            onFollow = onFollowTapped,
        ) {
            HeroBackground(detail)
        }
        Column(
            modifier =
                Modifier.padding(
                    start = FestiCoreSpacing.paddingP16,
                    end = FestiCoreSpacing.paddingP16,
                    top = FestiCoreSpacing.paddingP20,
                    bottom = FestiCoreSpacing.paddingP4,
                ),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP20),
        ) {
            // 잠금화면 행은 라이브 액티비티 단계에서 지금 카드 아래(간격 P20)에 넣는다.
            NowCard(FlagDetailPresentation.nowCard(state))
            PlanSection(detail, FlagDetailPresentation.planRows(state))
            Text(
                text = "신고하기",
                modifier =
                    Modifier
                        .padding(top = FestiCoreSpacing.paddingP8)
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onReportTapped),
                style = FestiCoreTextStyle.baseFootnoteMedium.textStyle,
                color = FestiCoreSemanticColor.labelAssistive.color,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HeroBackground(detail: FestivalFlagDetail) {
    val url = detail.photoUrl ?: detail.thumbnailUrl
    if (url != null) {
        FCRemoteImage(
            url = url,
            modifier = Modifier.fillMaxSize(),
            placeholderIcon = R.drawable.ic_flag,
            placeholderIconSize = PlaceholderIconSize,
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundElevated.color))
    }
}

@Composable
private fun NowCard(now: FlagDetailNow?) {
    when (now) {
        is FlagDetailNow.Located ->
            FCFlagNowCard(
                if (now.isMuted) {
                    FCFlagNowCardState.Muted(zone = now.zone, activity = now.activity, source = now.source)
                } else {
                    FCFlagNowCardState.Active(zone = now.zone, activity = now.activity, source = now.source)
                },
            )

        FlagDetailNow.Ended -> FCFlagNowCard(FCFlagNowCardState.Ended())

        null -> Unit
    }
}

@Composable
private fun PlanSection(detail: FestivalFlagDetail, rows: List<FlagDetailPlanRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        FCSectionHeader(title = "오늘 계획", actionTitle = if (detail.isPlanVisible) "기수가 정한 순서예요" else null)
        when {
            !detail.isPlanVisible -> FCPlanPrivate()

            detail.planSetIds.isEmpty() -> FCPlanPrivate(title = "오늘 계획이 아직 없어요", message = "기수가 계획을 올리면 여기에서 볼 수 있어요")

            rows.isEmpty() -> FCPlanPrivate(title = "공연 정보를 아직 받지 못했어요", message = "축제 팩을 받으면 계획을 볼 수 있어요")

            else ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(FestiCoreSemanticColor.backgroundElevated.color, RoundedCornerShape(FestiCoreRadius.r16))
                            .padding(vertical = FestiCoreSpacing.paddingP4),
                ) {
                    rows.forEach { row ->
                        FCPlanRow(
                            time = row.time,
                            artist = row.artist,
                            stage = row.stage,
                            state = planState(row.timing),
                            isMine = row.isMine,
                        )
                    }
                }
        }
    }
}

@Composable
private fun NotFound() {
    FCEmptyCard(
        title = "깃발을 찾을 수 없어요",
        message = "숨겨졌거나 더 이상 공개하지 않는 깃발이에요.",
        modifier = Modifier.fillMaxWidth().padding(FestiCoreSpacing.paddingP16),
    )
}

@Composable
private fun Retry(onRetryTapped: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "깃발 정보를 불러오지 못했어요",
            style = FestiCoreTextStyle.baseBodyMedium.textStyle,
            color = FestiCoreSemanticColor.labelNeutral.color,
        )
        FCButton(title = "다시 시도", style = FCButtonStyle.Secondary, size = FCButtonSize.Medium, onClick = onRetryTapped)
    }
}

/** Figma S05 실측: 히어로 360 → P20 → 지금 카드 106 → P24 → 계획 286. */
@Composable
private fun Skeleton() {
    Column(modifier = Modifier.fillMaxSize().testTag(FestivalAccessibilityId.FLAG_DETAIL_SKELETON)) {
        FCSkeletonView(FCSkeletonShape.Rect, Modifier.fillMaxWidth().height(SkeletonHeroHeight))
        Column(
            modifier = Modifier.padding(
                start = FestiCoreSpacing.paddingP16,
                end = FestiCoreSpacing.paddingP16,
                top = FestiCoreSpacing.paddingP20,
            ),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
        ) {
            FCSkeletonView(FCSkeletonShape.Round(), Modifier.fillMaxWidth().height(SkeletonNowCardHeight))
            FCSkeletonView(FCSkeletonShape.Round(), Modifier.fillMaxWidth().height(SkeletonPlanListHeight))
        }
    }
}

@Composable
private fun ReportSheet(
    sheet: FlagReportSheetState?,
    onReasonSelected: (FestivalFlagReportReason) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    FCSheet(isPresented = sheet != null, onDismissRequest = onDismiss) {
        FCReportSheet(
            selection = sheet?.selectedReason?.let(ReportReasons::indexOf),
            options = ReportReasons.map(::reportTitle),
            onSelect = { onReasonSelected(ReportReasons[it]) },
            onSubmit = { onSubmit() },
        )
    }
}

/** 아래에서 나타나는 토스트. 만료(2초)는 ViewModel이 맡는다. 사라지는 동안 마지막 문구를 둔다. */
@Composable
private fun Toast(toast: FlagDetailToast?, modifier: Modifier) {
    var last by remember { mutableStateOf(toast) }
    if (toast != null) last = toast
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier.padding(FestiCoreSpacing.paddingP16),
        enter = fadeIn(tween(TOAST_MILLIS, easing = EaseOut)),
        exit = fadeOut(tween(TOAST_MILLIS, easing = EaseOut)),
    ) {
        last?.let { item ->
            val iconColor = if (item is FlagDetailToast.Followed) FestiCoreSemanticColor.staticLime else FestiCoreSemanticColor.staticWhite
            FCToast(toastItem(item), iconColor = iconColor.color)
        }
    }
}

private fun toastItem(toast: FlagDetailToast): FCToastItem = when (toast) {
    is FlagDetailToast.Followed -> FCToastItems.followed(toast.name)
    FlagDetailToast.FollowLimit -> info("팔로우는 100개까지 할 수 있어요")
    FlagDetailToast.FollowFailed -> info("팔로우를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요")
    FlagDetailToast.Reported -> FCToastItem(icon = R.drawable.ic_check, message = "신고를 접수했어요")
    FlagDetailToast.AlreadyReported -> info("이미 신고한 깃발이에요")
    FlagDetailToast.ReportFailed -> info("신고하지 못했어요. 잠시 후 다시 시도해 주세요")
}

private fun info(message: String) = FCToastItem(icon = R.drawable.ic_circle_info_16, message = message)

private fun reportTitle(reason: FestivalFlagReportReason): String = when (reason) {
    FestivalFlagReportReason.Fake -> "가짜 깃발이에요"
    FestivalFlagReportReason.Inappropriate -> "부적절한 사진이나 소개예요"
    FestivalFlagReportReason.Other -> "기타"
}

private fun chipStatus(status: FestivalFlagDetail.Status): FCStatusChipStatus = when (status) {
    FestivalFlagDetail.Status.Active -> FCStatusChipStatus.Active
    FestivalFlagDetail.Status.Resting -> FCStatusChipStatus.Resting
    FestivalFlagDetail.Status.Scheduled -> FCStatusChipStatus.Scheduled
    FestivalFlagDetail.Status.Ended -> FCStatusChipStatus.EndedToday
}

private fun planState(timing: FlagDetailPlanRow.Timing): FCPlanRowState = when (timing) {
    FlagDetailPlanRow.Timing.Upcoming -> FCPlanRowState.Upcoming
    FlagDetailPlanRow.Timing.Now -> FCPlanRowState.Now
    FlagDetailPlanRow.Timing.Past -> FCPlanRowState.Past
}
