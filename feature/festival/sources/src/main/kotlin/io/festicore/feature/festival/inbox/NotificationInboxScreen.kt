package io.festicore.feature.festival.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.festicore.domain.usernotification.UserNotificationItem
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.list.FCNotificationRow
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** 시각 문구를 다시 계산하는 간격. iOS `TimelineView(.periodic(by: 60))`. */
private val ClockRefreshInterval = 60.seconds

/** 선택 모드 · 선택 집합 변화 애니메이션. iOS `easeInOut(duration: 0.2)`. */
private const val SELECTION_ANIMATION_MILLIS = 200

/**
 * 알림함(iOS `NotificationInboxView`). ViewModel을 잇는다.
 * [onFestivalOpened]는 보드에 그 축제가 있어 열었으면 `true`를 돌려준다. `false`면 "아직 열 수 없는 알림이에요"를 띄운다(iOS Coordinator `destinationUnavailable`).
 * 뒤로 버튼 · 시스템 뒤로는 [onBack](pop). 화면은 자기 `BackHandler`를 두지 않는다. 선택 모드에서도 뒤로는 pop이다(iOS와 같음).
 */
@Composable
fun NotificationInboxScreen(
    viewModel: NotificationInboxViewModel,
    onBack: () -> Unit,
    onFestivalOpened: (String) -> Boolean,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemUTC(),
) {
    val state by viewModel.collectAsState()
    val currentOnFestivalOpened by rememberUpdatedState(onFestivalOpened)
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is NotificationInboxSideEffect.FestivalOpened ->
                if (!currentOnFestivalOpened(sideEffect.festivalId)) viewModel.onDestinationUnavailable()
        }
    }
    LaunchedEffect(viewModel) { viewModel.onAppear() }
    var now by remember { mutableStateOf(clock.instant()) }
    LaunchedEffect(clock) {
        while (true) {
            now = clock.instant()
            delay(ClockRefreshInterval)
        }
    }
    NotificationInboxContent(
        state = state,
        now = now,
        modifier = modifier,
        onBack = onBack,
        onRetryTapped = viewModel::onRetryTapped,
        onEditTapped = viewModel::onEditTapped,
        onCancelSelectionTapped = viewModel::onCancelSelectionTapped,
        onSelectAllTapped = viewModel::onSelectAllTapped,
        onDeleteTapped = viewModel::onDeleteTapped,
        onRowTapped = viewModel::onRowTapped,
        onLoadMoreTapped = viewModel::onLoadMoreTapped,
        onDialogDismissed = viewModel::onDialogDismissed,
    )
}

/** 알림함(순수 UI). [now]는 기기 시각이다. */
@Composable
fun NotificationInboxContent(
    state: NotificationInboxState,
    now: Instant,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onRetryTapped: () -> Unit = {},
    onEditTapped: () -> Unit = {},
    onCancelSelectionTapped: () -> Unit = {},
    onSelectAllTapped: () -> Unit = {},
    onDeleteTapped: () -> Unit = {},
    onRowTapped: (String) -> Unit = {},
    onLoadMoreTapped: () -> Unit = {},
    onDialogDismissed: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundElevated.color)) {
        TopBar(state, onBack, onEditTapped, onCancelSelectionTapped)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.load == NotificationInboxLoad.Idle || state.load == NotificationInboxLoad.Loading -> LoadingContent()
                state.load == NotificationInboxLoad.Failed -> MessageContent("알림을 불러오지 못했어요", retry = true, onRetryTapped)
                state.items.isEmpty() -> MessageContent("새로운 알림이 없어요", retry = false, onRetryTapped)
                else -> NotificationList(state, now, onRowTapped, onLoadMoreTapped)
            }
        }
        AnimatedVisibility(
            visible = state.isEditing,
            enter =
                fadeIn(tween(SELECTION_ANIMATION_MILLIS, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(SELECTION_ANIMATION_MILLIS)),
            exit =
                fadeOut(tween(SELECTION_ANIMATION_MILLIS, easing = FastOutSlowInEasing)) +
                    shrinkVertically(tween(SELECTION_ANIMATION_MILLIS)),
        ) {
            SelectionBar(state, onSelectAllTapped, onDeleteTapped)
        }
    }
    state.dialog?.let { dialog ->
        FCDialog(
            item =
                FCDialogItem(
                    title = dialog.title,
                    message = dialog.message,
                    cancelTitle = "닫기",
                    onConfirm = {},
                ),
            onDismiss = onDialogDismissed,
        )
    }
}

@Composable
private fun TopBar(
    state: NotificationInboxState,
    onBack: () -> Unit,
    onEditTapped: () -> Unit,
    onCancelSelectionTapped: () -> Unit,
) {
    when {
        state.isEditing ->
            FCTopBar(
                style = FCTopBarStyle.Back,
                title = "${state.selectedIds.size}개 선택",
                backgroundColor = FestiCoreSemanticColor.backgroundNormal,
                trailingIcon = R.drawable.ic_xmark_16,
                trailingAccessibilityLabel = "선택 취소",
                onBack = onBack,
                onTrailingAction = onCancelSelectionTapped,
            )

        state.load == NotificationInboxLoad.Loaded && state.items.isNotEmpty() ->
            FCTopBar(
                style = FCTopBarStyle.Back,
                title = "알림",
                backgroundColor = FestiCoreSemanticColor.backgroundNormal,
                trailingIcon = R.drawable.ic_trash_can_20,
                trailingAccessibilityLabel = "알림 선택 삭제",
                onBack = onBack,
                onTrailingAction = onEditTapped,
            )

        else -> FCTopBar(
            style = FCTopBarStyle.Back,
            title = "알림",
            backgroundColor = FestiCoreSemanticColor.backgroundNormal,
            onBack = onBack,
        )
    }
}

@Composable
private fun NotificationList(
    state: NotificationInboxState,
    now: Instant,
    onRowTapped: (String) -> Unit,
    onLoadMoreTapped: () -> Unit,
) {
    val lastId = state.items.lastOrNull()?.id
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // iOS `fcScrollBottomInset()`. Lazy 목록은 contentPadding으로 같은 값을 준다.
        contentPadding = PaddingValues(bottom = FestiCoreSpacing.paddingP20),
    ) {
        NotificationInboxPresentation.sections(state.items, now).forEach { section ->
            item(key = "section-${section.id}") {
                Text(
                    text = section.title,
                    style = FestiCoreTextStyle.baseCaption1Bold.textStyle,
                    color = FestiCoreSemanticColor.labelNeutral.color,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = FestiCoreSpacing.paddingP16,
                                end = FestiCoreSpacing.paddingP16,
                                top = FestiCoreSpacing.paddingP20,
                                bottom = FestiCoreSpacing.paddingP8,
                            ).semantics { heading() },
                )
            }
            items(section.items, key = { it.id }) { item ->
                NotificationRow(item, state, now, onRowTapped)
                // iOS 행 `onAppear`: 마지막 행이 보이면 다음 쪽.
                if (item.id == lastId) LaunchedEffect(item.id) { onLoadMoreTapped() }
            }
        }
        item(key = "footer") {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.isLoadingMore) {
                    CircularProgressIndicator(
                        color = FestiCoreSemanticColor.labelNeutral.color,
                        modifier =
                            Modifier
                                .padding(FestiCoreSpacing.paddingP16)
                                .size(LoadMoreIndicatorSize)
                                .semantics { contentDescription = "알림을 더 불러오는 중" },
                        strokeWidth = 2.dp,
                    )
                } else if (state.nextCursor != null && !state.isEditing) {
                    FCButton(
                        title = "알림 더 보기",
                        modifier = Modifier.padding(FestiCoreSpacing.paddingP16),
                        style = FCButtonStyle.Ghost,
                        size = FCButtonSize.Medium,
                        onClick = onLoadMoreTapped,
                    )
                }
            }
        }
    }
}

private val LoadMoreIndicatorSize = 20.dp

@Composable
private fun NotificationRow(
    item: UserNotificationItem,
    state: NotificationInboxState,
    now: Instant,
    onRowTapped: (String) -> Unit,
) {
    val isRead = state.isRead(item)
    val isSelected = item.id in state.selectedIds
    val time = NotificationInboxPresentation.time(item, now)
    val hint = if (state.isEditing) "선택 상태를 바꿉니다" else "알림을 엽니다"
    FCNotificationRow(
        title = item.title,
        message = item.body,
        time = time,
        isRead = isRead,
        icon = NotificationInboxPresentation.icon(item.type),
        isEditing = state.isEditing,
        isSelected = isSelected,
        modifier =
            Modifier
                .clickable(onClickLabel = hint) { onRowTapped(item.id) }
                .clearAndSetSemantics {
                    contentDescription = "${item.title}, ${item.body}, $time"
                    stateDescription = if (isRead) "읽음" else "안 읽음"
                    if (state.isEditing && isSelected) selected = true
                    onClick(label = hint) {
                        onRowTapped(item.id)
                        true
                    }
                },
    )
}

@Composable
private fun LoadingContent() {
    Column(modifier = Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = "알림을 불러오는 중" }) {
        repeat(SKELETON_ROW_COUNT) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(FestiCoreSpacing.paddingP16),
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FCSkeletonView(FCSkeletonShape.Circle, Modifier.size(SkeletonAvatarSize))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                    FCSkeletonView(FCSkeletonShape.Round(FestiCoreRadius.r4), Modifier.fillMaxWidth().height(SkeletonTitleHeight))
                    FCSkeletonView(FCSkeletonShape.Round(FestiCoreRadius.r4), Modifier.width(SkeletonBodyWidth).height(SkeletonBodyHeight))
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

private const val SKELETON_ROW_COUNT = 5
private val SkeletonAvatarSize = 40.dp
private val SkeletonTitleHeight = 18.dp
private val SkeletonBodyWidth = 180.dp
private val SkeletonBodyHeight = 14.dp

@Composable
private fun MessageContent(
    title: String,
    retry: Boolean,
    onRetryTapped: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FCEmptyCard(
            title = title,
            message = if (retry) "연결을 확인하고 다시 시도해 주세요." else "새 소식이 오면 이곳에서 확인할 수 있어요.",
            icon = R.drawable.ic_bell_16,
        )
        if (retry) FCButton(title = "다시 시도", style = FCButtonStyle.Secondary, size = FCButtonSize.Medium, onClick = onRetryTapped)
    }
}

@Composable
private fun SelectionBar(
    state: NotificationInboxState,
    onSelectAllTapped: () -> Unit,
    onDeleteTapped: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(FestiCoreSemanticColor.backgroundElevated.color)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FestiCoreSemanticColor.lineNormal.color))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(FestiCoreSpacing.paddingP16)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        ) {
            FCButton(
                title = if (state.allLoadedSelected) "선택 해제" else "전체 선택",
                modifier = Modifier.weight(1f),
                style = FCButtonStyle.Secondary,
                isFullWidth = true,
                onClick = onSelectAllTapped,
            )
            FCButton(
                title = "삭제 (${state.selectedIds.size})",
                modifier = Modifier.weight(1f),
                style = FCButtonStyle.Accent,
                isEnabled = state.selectedIds.isNotEmpty() && !state.isDeleting,
                isFullWidth = true,
                onClick = onDeleteTapped,
            )
        }
    }
}
