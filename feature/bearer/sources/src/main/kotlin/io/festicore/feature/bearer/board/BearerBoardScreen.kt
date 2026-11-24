package io.festicore.feature.bearer.board

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.festicore.domain.bearer.entity.BearerActivity
import io.festicore.domain.bearer.entity.BearerCoBearer
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.feature.bearer.BearerRoute
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCBackPill
import io.festicore.ui.designkit.component.action.FCBearerCTA
import io.festicore.ui.designkit.component.action.FCBearerCTACopy
import io.festicore.ui.designkit.component.action.FCBearerCTAState
import io.festicore.ui.designkit.component.action.FCBigButton
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.label.FCBearerFooter
import io.festicore.ui.designkit.component.label.FCOfflineBadge
import io.festicore.ui.designkit.component.label.FCStatusChip
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.component.modal.FCSheet
import io.festicore.ui.designkit.component.popup.FCCheckOverlay
import io.festicore.ui.designkit.component.popup.FCCheckOverlayItem
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.component.popup.FCDialogTextAlign
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.theme.FestiCoreDarkMode
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

// iOS `BearerBoardView.Metric.coBearerAvatarSize`(`BBV:421`)
private val CoBearerAvatarSize = 20.dp

// iOS `BearerBoardSkeletonView.Metric`(`BSk:38-41`)
private val SkeletonTitleWidth = 160.dp
private val SkeletonTitleHeight = 32.dp
private val SkeletonSummaryWidth = 240.dp
private val SkeletonSummaryHeight = 20.dp

// iOS `TimelineView(.periodic(by: 30))`(`BBV:24-27`) · 확인 오버레이 `.easeOut(duration: 0.2)`(`BBV:61-64`)
private val RelativeTimeTick = 30.seconds
private const val CHECK_FADE_MILLIS = 200

/**
 * 기수 보드(iOS `BearerBoardView`, Figma S09 A~J). 경로 인자로 ViewModel을 만들어 잇는다. 화면 전체를 다크로 고정한다(설계 B6).
 * 시스템 뒤로는 상단 "관객 화면으로"와 같다(못 보낸 변경이 있으면 확인). 닫기 결과는 [onClose]로만 알린다.
 *
 * @param viewerUserId 로그인한 사용자 id(경로에 싣지 않고 Coordinator가 넘긴다).
 */
@Composable
fun BearerBoardScreen(
    route: BearerRoute.Board,
    viewerUserId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BearerBoardViewModel = koinViewModel { parametersOf(route.festivalId, route.flagId, viewerUserId) },
) {
    val state by viewModel.collectAsState()
    val haptic = LocalHapticFeedback.current
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            BearerBoardSideEffect.CloseRequested -> onClose()

            // iOS `.sensoryFeedback(.success)`(`BBV:65-68`) → Android 확인 햅틱
            BearerBoardSideEffect.CheckHaptic -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    BackHandler(onBack = viewModel::onCloseTapped)
    // iOS `.onAppear` · `.onDisappear`(`BBV:104-105`)
    DisposableEffect(viewModel) {
        viewModel.onAppear()
        onDispose { viewModel.onDisappear() }
    }
    // Android 추가(설계 B-3): 앱이 포그라운드로 돌아오면 지금 보드를 유지한 채 다시 읽는다.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    BearerBoardContent(
        state = state,
        modifier = modifier,
        callbacks =
            BearerBoardCallbacks(
                onCloseTapped = viewModel::onCloseTapped,
                onRetryTapped = viewModel::onRetryTapped,
                onRetrySendTapped = viewModel::onRetrySendTapped,
                onZoneTapped = viewModel::onZoneTapped,
                onActivityTapped = viewModel::onActivityTapped,
                onRestTapped = viewModel::onRestTapped,
                onResumeTapped = viewModel::onResumeTapped,
                onEndTapped = viewModel::onEndTapped,
                onRaiseTapped = viewModel::onRaiseTapped,
                onRestartTapped = viewModel::onRestartTapped,
                onZonePicked = viewModel::onZonePicked,
                onActivityPicked = viewModel::onActivityPicked,
                onEndConfirmed = viewModel::onEndConfirmed,
                onCloseConfirmed = viewModel::onCloseConfirmed,
                onDialogDismissed = viewModel::onDialogDismissed,
                onSheetDismissed = viewModel::onSheetDismissed,
            ),
    )
}

/** 순수 UI가 부르는 동작. */
data class BearerBoardCallbacks(
    val onCloseTapped: () -> Unit = {},
    val onRetryTapped: () -> Unit = {},
    val onRetrySendTapped: () -> Unit = {},
    val onZoneTapped: () -> Unit = {},
    val onActivityTapped: () -> Unit = {},
    val onRestTapped: () -> Unit = {},
    val onResumeTapped: () -> Unit = {},
    val onEndTapped: () -> Unit = {},
    val onRaiseTapped: () -> Unit = {},
    val onRestartTapped: () -> Unit = {},
    val onZonePicked: (String) -> Unit = {},
    val onActivityPicked: (BearerActivity) -> Unit = {},
    val onEndConfirmed: () -> Unit = {},
    val onCloseConfirmed: () -> Unit = {},
    val onDialogDismissed: () -> Unit = {},
    /** 사용자가 시트를 직접 내렸을 때만. 존 → 활동으로 이어질 때는 [FCSheet]가 부르지 않는다. */
    val onSheetDismissed: () -> Unit = {},
)

/** 기수 보드(순수 UI). 다크 고정 안에서 상단 줄 · 머리글 · 본문 · 아래 안내와 시트 · 팝업 · 확인 오버레이를 그린다. */
@Composable
fun BearerBoardContent(
    state: BearerBoardState,
    modifier: Modifier = Modifier,
    callbacks: BearerBoardCallbacks = BearerBoardCallbacks(),
) {
    FestiCoreDarkMode {
        // 30초마다 상대 시각을 다시 그린다(iOS `TimelineView`).
        val now by produceState(Instant.now()) {
            while (true) {
                delay(RelativeTimeTick)
                value = Instant.now()
            }
        }
        // Figma S09 프레임 배경은 순수 검정(#000000)이다. 배경 · 확인 오버레이는 안전 영역까지 채운다.
        Box(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.staticBlack.color)) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                BoardTopRow(onCloseTapped = callbacks.onCloseTapped)
                if (state.isOffline) {
                    FCOfflineBadge("오프라인 · ${relativeText(state.lastSyncedAt, now)} 정보")
                }
                val current = state.current
                val board = state.board
                if (current != null && board != null) {
                    BoardContent(state, current, board.profile.name, board.followerCount, now, callbacks)
                } else if (state.loadFailed) {
                    BoardFailedContent(state.errorMessage, callbacks.onRetryTapped)
                } else {
                    BearerBoardSkeleton(Modifier.weight(1f))
                }
            }
            AnimatedContent(
                targetState = state.check,
                modifier = Modifier.matchParentSize(),
                transitionSpec = {
                    fadeIn(tween(CHECK_FADE_MILLIS, easing = EaseOut)) togetherWith fadeOut(tween(CHECK_FADE_MILLIS, easing = EaseOut))
                },
                label = "BearerBoardCheck",
            ) { check ->
                if (check != null) {
                    val item = remember(check) { FCCheckOverlayItem(title = check.title, message = check.message) }
                    FCCheckOverlay(item)
                }
            }
        }
        BoardSheets(state, callbacks)
        BoardDialogs(state.destination, callbacks)
    }
}

// Figma Top Row: 50pt, 위아래 8pt 여백(`BBV:110-128`)
@Composable
private fun BoardTopRow(onCloseTapped: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FestiCoreSpacing.paddingP16, vertical = FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FCBackPill("관객 화면으로", onClick = onCloseTapped)
        Spacer(Modifier.weight(1f))
        BasicText(
            text = "기수 모드",
            style = FestiCoreTextStyle.baseFootnoteMedium.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
        )
    }
}

@Composable
private fun ColumnScope.BoardContent(
    state: BearerBoardState,
    current: BearerBoardCurrent,
    name: String,
    followerCount: Int,
    now: Instant,
    callbacks: BearerBoardCallbacks,
) {
    Column(modifier = Modifier.weight(1f)) {
        BoardHeader(state, current, name, followerCount, now)
        BoardBody(state, current, callbacks, Modifier.weight(1f))
        FCBearerFooter(queuedCount = state.queuedCount)
    }
}

// Figma Bearer Header: 위 16pt · 아래 8pt 여백, 줄 사이 8pt(`BBV:149-199`)
@Composable
private fun BoardHeader(
    state: BearerBoardState,
    current: BearerBoardCurrent,
    name: String,
    followerCount: Int,
    now: Instant,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FestiCoreSpacing.paddingP16)
                .padding(top = FestiCoreSpacing.paddingP16, bottom = FestiCoreSpacing.paddingP8),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8),
    ) {
        BasicText(
            text = name,
            style = FestiCoreTextStyle.baseTitle1Bold.textStyle.copy(color = FestiCoreSemanticColor.labelStrong.color),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8), verticalAlignment = Alignment.CenterVertically) {
            FCStatusChip(statusChip(current.lifecycle))
            BasicText(
                text = summary(state, current, now),
                style = FestiCoreTextStyle.baseCalloutRegular.textStyle.copy(color = FestiCoreSemanticColor.labelNeutral.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.changedByOtherBearer?.let { other -> CoBearerRow(other, current, now) }
        BasicText(
            text = "팔로워 ${NumberFormat.getIntegerInstance(Locale.KOREA).format(followerCount)}명이 보고 있어요",
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
        )
    }
}

// `BBV:201-225`
@Composable
private fun CoBearerRow(
    other: BearerCoBearer,
    current: BearerBoardCurrent,
    now: Instant,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM6), verticalAlignment = Alignment.CenterVertically) {
        // Figma Co-bearer 아바타는 다크에서도 연보라(#F6F2FF) 바탕이다. 읽기 도구에서는 숨긴다.
        Box(
            modifier =
                Modifier
                    .size(CoBearerAvatarSize)
                    .background(FestiCoreSemanticColor.staticPurpleTint.color, CircleShape)
                    .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = other.nickname.firstCharacter(),
                style = FestiCoreTextStyle.baseCaption2Bold.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.accentForegroundPurple.color,
                ),
            )
        }
        val nickname = other.nickname.ifEmpty { "공동 기수" }
        BasicText(
            text = nickname.withIga() + " ${changedTimeText(current.updatedAt, now)} 바꿨어요",
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
        )
    }
}

// Figma Body: 위 8pt · 아래 4pt 여백, 남은 높이를 타일 · CTA가 채운다(`BBV:227-259`).
@Composable
private fun BoardBody(
    state: BearerBoardState,
    current: BearerBoardCurrent,
    callbacks: BearerBoardCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FestiCoreSpacing.paddingP16)
                .padding(top = FestiCoreSpacing.paddingP8, bottom = FestiCoreSpacing.paddingP4),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (current.lifecycle) {
            BearerFlagLifecycle.Scheduled ->
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FCBearerCTA(FCBearerCTAState.Start, onClick = callbacks.onRaiseTapped)
                }

            BearerFlagLifecycle.Ended ->
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RestartCTA(state.restartUsedToday, callbacks.onRestartTapped)
                }

            BearerFlagLifecycle.Active, BearerFlagLifecycle.Resting ->
                BoardTiles(state, current, callbacks, Modifier.weight(1f))
        }
        state.errorMessage?.let { message -> BoardErrorRow(message, state.isSendPaused, callbacks.onRetrySendTapped) }
    }
}

// `BBV:261-276`
@Composable
private fun BoardErrorRow(
    message: String,
    isSendPaused: Boolean,
    onRetrySendTapped: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = message,
            modifier = Modifier.weight(1f),
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                color = FestiCoreSemanticColor.statusNegative.color,
            ),
        )
        if (isSendPaused) {
            FCButton("다시 보내기", style = FCButtonStyle.Secondary, size = FCButtonSize.Medium, onClick = onRetrySendTapped)
        }
    }
}

// 2×2 큰 버튼(`BBV:278-330`)
@Composable
private fun BoardTiles(
    state: BearerBoardState,
    current: BearerBoardCurrent,
    callbacks: BearerBoardCallbacks,
    modifier: Modifier = Modifier,
) {
    val isResting = current.lifecycle == BearerFlagLifecycle.Resting
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12)) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12)) {
            FCBigButton(
                label = "존",
                sub = state.zoneName(current.zoneId) ?: "존 고르기",
                icon = R.drawable.ic_location_dot_20,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = callbacks.onZoneTapped,
            )
            FCBigButton(
                label = "활동",
                sub = (current.activity ?: state.lastActivity)?.title ?: "활동 고르기",
                icon = R.drawable.ic_music_24,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                isSelected = !isResting,
                onClick = callbacks.onActivityTapped,
            )
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12)) {
            if (isResting) {
                FCBigButton(
                    label = "다시 시작",
                    sub = "활동 중으로 돌아가요",
                    icon = R.drawable.ic_clock_20,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    isSelected = true,
                    onClick = callbacks.onResumeTapped,
                )
            } else {
                FCBigButton(
                    label = "쉬는 중",
                    sub = "탭 한 번",
                    icon = R.drawable.ic_clock_20,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = callbacks.onRestTapped,
                )
            }
            FCBigButton(
                label = "종료",
                sub = "오늘 깃발 내리기",
                icon = R.drawable.ic_circle_xmark,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = callbacks.onEndTapped,
            )
        }
    }
}

// `BBV:332-347`. 오늘 이미 다시 시작했으면 문구를 바꾸고 비활성 투명도로 막는다.
@Composable
private fun RestartCTA(restartUsedToday: Boolean, onRestartTapped: () -> Unit) {
    if (restartUsedToday) {
        FCBearerCTA(
            state = FCBearerCTAState.Restart,
            modifier = Modifier.alpha(FestiCoreState.disabledOpacity),
            copy =
                FCBearerCTACopy(
                    title = "오늘 깃발을 내렸어요",
                    buttonTitle = "다시 시작하기",
                    caption = "오늘은 이미 다시 시작했어요",
                    note = "내일 다시 올릴 수 있어요",
                ),
            onClick = {},
        )
    } else {
        FCBearerCTA(FCBearerCTAState.Restart, onClick = onRestartTapped)
    }
}

// `BBV:349-364`
@Composable
private fun ColumnScope.BoardFailedContent(errorMessage: String?, onRetryTapped: () -> Unit) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = errorMessage ?: "기수 보드를 불러오지 못했어요",
            style =
                FestiCoreTextStyle.baseBodyMedium.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.labelNeutral.color,
                    textAlign = TextAlign.Center,
                ),
        )
        FCButton("다시 시도", style = FCButtonStyle.Secondary, size = FCButtonSize.Medium, onClick = onRetryTapped)
    }
}

/** 기수 보드 상태를 읽는 동안 머리글과 큰 버튼 자리를 보여 준다(iOS `BearerBoardSkeletonView`). */
@Composable
internal fun BearerBoardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(FestiCoreSpacing.paddingP16)
                .clearAndSetSemantics { contentDescription = "기수 보드를 불러오는 중" },
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(SkeletonTitleWidth, SkeletonTitleHeight))
        FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(SkeletonSummaryWidth, SkeletonSummaryHeight))
        FCSkeletonView(FCSkeletonShape.Round(cornerRadius = FestiCoreRadius.r24), Modifier.fillMaxWidth().weight(1f))
    }
}

// 존 · 활동 시트(iOS `fcModal`, `BBV:69-88`). 바깥 다크 고정을 시트 창이 이어받는다.
@Composable
private fun BoardSheets(state: BearerBoardState, callbacks: BearerBoardCallbacks) {
    FCSheet(
        item = state.destination as? BearerBoardDestination.ZonePicker,
        onDismissRequest = callbacks.onSheetDismissed,
    ) { picker ->
        BearerZonePicker(zones = picker.zones, selectedZoneId = picker.selectedZoneId, onZoneTapped = callbacks.onZonePicked)
    }
    FCSheet(
        item = state.destination as? BearerBoardDestination.ActivityPicker,
        onDismissRequest = callbacks.onSheetDismissed,
    ) { picker ->
        BearerActivityPicker(selected = picker.selected, onActivityTapped = callbacks.onActivityPicked)
    }
}

// 종료 · 닫기 확인(iOS `FCAlertState.bearerEndToday` · `bearerClosePending`, `BBV:89-99`)
@Composable
private fun BoardDialogs(destination: BearerBoardDestination?, callbacks: BearerBoardCallbacks) {
    when (destination) {
        BearerBoardDestination.EndConfirm ->
            FCDialog(
                item =
                    remember(callbacks) {
                        FCDialogItem(
                            title = "오늘 깃발을 내릴까요?",
                            message = "팔로워 화면에 '오늘 종료'로 보여요. 오늘 한 번 더 시작할 수 있어요.",
                            confirmTitle = "깃발 내리기",
                            cancelTitle = "닫기",
                            onConfirm = callbacks.onEndConfirmed,
                        )
                    },
                onDismiss = callbacks.onDialogDismissed,
                textAlign = FCDialogTextAlign.Center,
                confirmStyle = FCButtonStyle.Inverse,
            )

        is BearerBoardDestination.CloseConfirm ->
            FCDialog(
                item =
                    remember(destination.count, callbacks) {
                        FCDialogItem(
                            title = "아직 보내지 못한 변경이 ${destination.count}건 있어요",
                            message = "닫아도 이 기기에 남겨 두고 연결되면 자동으로 보내요.",
                            confirmTitle = "닫기",
                            cancelTitle = "계속 보기",
                            onConfirm = callbacks.onCloseConfirmed,
                        )
                    },
                onDismiss = callbacks.onDialogDismissed,
            )

        else -> Unit
    }
}

private fun statusChip(lifecycle: BearerFlagLifecycle): FCStatusChipStatus = when (lifecycle) {
    BearerFlagLifecycle.Scheduled -> FCStatusChipStatus.Scheduled
    BearerFlagLifecycle.Active -> FCStatusChipStatus.Active
    BearerFlagLifecycle.Resting -> FCStatusChipStatus.Resting
    BearerFlagLifecycle.Ended -> FCStatusChipStatus.EndedToday
}

/** 머리글 요약(`BBV:375-393`). 없는 값은 뺀다. */
internal fun summary(
    state: BearerBoardState,
    current: BearerBoardCurrent,
    now: Instant,
): String {
    val time = relativeText(current.updatedAt, now)
    return when (current.lifecycle) {
        BearerFlagLifecycle.Scheduled -> "아직 깃발을 올리지 않았어요"
        BearerFlagLifecycle.Ended -> "오늘 활동을 마쳤어요 · $time"
        BearerFlagLifecycle.Resting -> listOfNotNull(state.zoneName(current.zoneId), "쉬는 중", time).joinToString(" · ")
        BearerFlagLifecycle.Active -> listOfNotNull(state.zoneName(current.zoneId), current.activity?.title, time).joinToString(" · ")
    }
}

/** 상대 시각(`BBV:395-407`). 60초 미만 "방금", 1시간 미만 "N분 전", 그 외 "N시간 전". 시각이 없으면 "방금". */
internal fun relativeText(date: Instant?, now: Instant): String {
    if (date == null) return "방금"
    val seconds = Duration.between(date, now).seconds.coerceAtLeast(0)
    return when {
        seconds < SECONDS_PER_MINUTE -> "방금"
        seconds < SECONDS_PER_HOUR -> "${seconds / SECONDS_PER_MINUTE}분 전"
        else -> "${seconds / SECONDS_PER_HOUR}시간 전"
    }
}

/** "방금" 또는 "N분 전에"(`BBV:409-418`) */
internal fun changedTimeText(date: Instant, now: Instant): String {
    val text = relativeText(date, now)
    return if (text == "방금") text else "${text}에"
}

/** 첫 글자(코드포인트 하나, iOS `prefix(1)`) */
private fun String.firstCharacter(): String = if (isEmpty()) "" else substring(0, offsetByCodePoints(0, 1))

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
