package io.festicore.feature.festival.flag

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.zIndex
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.feature.festival.flag.FlagPresentation.title
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCChip
import io.festicore.ui.designkit.component.action.FCSortButton
import io.festicore.ui.designkit.component.action.FCSortMenu
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.card.FCFlagCard
import io.festicore.ui.designkit.component.card.FCFlagCardContext
import io.festicore.ui.designkit.component.label.FCOfflineBadge
import io.festicore.ui.designkit.component.label.FCStatusChipStatus
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.media.FCThumbnail
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
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** iOS `FestivalFlagView.Metric`. 토큰에 없는 고유 크기만 둔다. */
private val ThumbnailSize = 56.dp
private val PlaceholderIconSize = 20.dp

/** Figma Sort Button 높이. */
private val SortButtonHeight = 26.dp
private val SkeletonTitleWidth = 140.dp
private val SkeletonTitleHeight = 20.dp
private val SkeletonSubtitleWidth = 100.dp
private val SkeletonSubtitleHeight = 16.dp
private const val SKELETON_ROWS = 3
private const val TOAST_MILLIS = 250
private val OfflineRefresh = 60.seconds

/**
 * S04 깃발 목록(iOS `FestivalFlagView`). 화면에 들어오고 나갈 때 [FlagViewModel.onAppeared] · [FlagViewModel.onDisappeared]를 보낸다.
 * 상세 · 로그인 · 약관 재동의는 경계 콜백으로만 알린다.
 */
@Composable
fun FlagScreen(
    onDetailRequested: (String) -> Unit,
    onLoginRequested: () -> Unit,
    onTermsAcceptanceRequested: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlagViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsState()
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is FlagSideEffect.DetailRequested -> onDetailRequested(sideEffect.flagId)
            FlagSideEffect.LoginRequested -> onLoginRequested()
            FlagSideEffect.TermsAcceptanceRequested -> onTermsAcceptanceRequested()
        }
    }
    DisposableEffect(viewModel) {
        viewModel.onAppeared()
        onDispose { viewModel.onDisappeared() }
    }
    FlagContent(
        state = state,
        modifier = modifier,
        onRefresh = viewModel::refresh,
        onTagSelected = viewModel::onTagSelected,
        onSortTapped = viewModel::onSortTapped,
        onSortSelected = viewModel::onSortSelected,
        onSortMenuDismissed = viewModel::onSortMenuDismissed,
        onFollowTapped = viewModel::onFollowTapped,
        onCardTapped = viewModel::onCardTapped,
    )
}

/** State만 받는 깃발 목록 UI. Preview가 쓴다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlagContent(
    state: FlagState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onTagSelected: (FlagTag?) -> Unit = {},
    onSortTapped: () -> Unit = {},
    onSortSelected: (FlagSort) -> Unit = {},
    onSortMenuDismissed: () -> Unit = {},
    onFollowTapped: (id: String, follow: Boolean, baseCount: Int?) -> Unit = { _, _, _ -> },
    onCardTapped: (String) -> Unit = {},
) {
    val isSortMenuPresented = state.directory.isSortMenuPresented
    Box(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        Column(modifier = Modifier.fillMaxSize()) {
            FCTopBar(FCTopBarStyle.Large, title = "깃발")
            val snapshot = state.snapshot
            if (state.isOffline && snapshot != null) OfflineBadge(snapshot.receivedAt)
            // iOS `.refreshable { send(.refresh) }`는 기다리지 않아 표시가 바로 끝난다. 상태는 목록 · 스켈레톤이 보인다.
            PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .dismissOnTap(isSortMenuPresented, onSortMenuDismissed)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = FestiCoreSpacing.paddingP16,
                                end = FestiCoreSpacing.paddingP16,
                                top = FestiCoreSpacing.paddingP16,
                            )
                            .fcScrollBottomInset(),
                    verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
                ) {
                    Head()
                    if (state.festivalId == null) {
                        FCEmptyCard(title = "축제를 선택해 주세요", message = "축제를 선택하면 해당 축제의 깃발을 볼 수 있습니다.")
                    } else {
                        FilterChips(selected = state.directory.tag, onTagSelected = onTagSelected)
                        Content(
                            state = state,
                            // 메뉴가 열려 있으면 바깥 누르기가 이미 닫는다. 다시 열지 않는다.
                            onSortTapped = { if (!isSortMenuPresented) onSortTapped() },
                            onSortSelected = onSortSelected,
                            onFollowTapped = onFollowTapped,
                            onCardTapped = onCardTapped,
                        )
                    }
                }
            }
        }
        Toast(message = state.directory.toast, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** 경과 시간 문구가 분 단위로 바뀌도록 1분마다 다시 그린다(iOS `TimelineView(.periodic 60)`). */
@Composable
private fun OfflineBadge(receivedAt: Instant?) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(OfflineRefresh)
            now = Instant.now()
        }
    }
    FCOfflineBadge(FlagPresentation.offlineText(receivedAt, now))
}

/** Figma S04 Head: 제목 Title 3/Bold · 부제 Footnote/Regular, 간격 4. */
@Composable
private fun Head() {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
        Text(
            text = "만나보고 싶은 깃발이 있나요?",
            style = FestiCoreTextStyle.baseTitle3Bold.textStyle,
            color = FestiCoreSemanticColor.labelNormal.color,
        )
        Text(
            text = "팔로우하지 않아도 괜찮아요. 깃발이 움직이면 조용히 알려 드려요.",
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
            color = FestiCoreSemanticColor.labelNeutral.color,
        )
    }
}

/** 칩 줄은 화면 끝까지 스크롤된다(Figma Filter Chips). 부모 좌우 여백 P16을 상쇄한다. */
@Composable
private fun FilterChips(selected: FlagTag?, onTagSelected: (FlagTag?) -> Unit) {
    Row(
        modifier =
            Modifier
                .layout { measurable, constraints ->
                    val inset = FestiCoreSpacing.paddingP16.roundToPx()
                    val placeable = measurable.measure(constraints.offset(horizontal = inset * 2))
                    layout(placeable.width - inset * 2, placeable.height) { placeable.place(-inset, 0) }
                }.horizontalScroll(rememberScrollState())
                .padding(horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
    ) {
        FCChip(label = "전체", isSelected = selected == null, onClick = { onTagSelected(null) })
        FlagTag.entries.forEach { tag ->
            FCChip(label = tag.title, isSelected = selected == tag, onClick = { onTagSelected(tag) })
        }
    }
}

@Composable
private fun Content(
    state: FlagState,
    onSortTapped: () -> Unit,
    onSortSelected: (FlagSort) -> Unit,
    onFollowTapped: (id: String, follow: Boolean, baseCount: Int?) -> Unit,
    onCardTapped: (String) -> Unit,
) {
    val snapshot = state.snapshot
    if (snapshot != null) {
        when (state.viewState) {
            FlagViewState.DeviceNotRegistered ->
                FCEmptyCard(title = "기기 등록이 필요합니다", message = "앱을 다시 실행해 기기를 등록한 뒤 새로고침해 주세요. 이전 깃발을 표시합니다.")

            FlagViewState.InvalidFestivalId ->
                FCEmptyCard(title = "축제 정보가 올바르지 않습니다", message = "앱을 업데이트한 뒤 다시 확인해 주세요. 이전 깃발을 표시합니다.")

            FlagViewState.InvalidResponse ->
                FCEmptyCard(title = "깃발 정보 형식이 올바르지 않습니다", message = "문제가 계속되면 문의해 주세요. 이전 깃발을 표시합니다.")

            FlagViewState.Idle, FlagViewState.Loading, FlagViewState.Loaded, FlagViewState.Failed -> Unit
        }
        FlagList(state, snapshot, onSortTapped, onSortSelected, onFollowTapped, onCardTapped)
        return
    }
    when (state.viewState) {
        FlagViewState.Idle, FlagViewState.Loading -> LoadingContent()
        FlagViewState.Failed -> FCEmptyCard(title = "깃발을 불러오지 못했습니다", message = "네트워크 연결을 확인하고 아래로 당겨 다시 시도해 주세요.")
        FlagViewState.DeviceNotRegistered -> FCEmptyCard(title = "기기 등록이 필요합니다", message = "앱을 다시 실행해 기기를 등록한 뒤 새로고침해 주세요.")
        FlagViewState.InvalidFestivalId -> FCEmptyCard(title = "축제 정보가 올바르지 않습니다", message = "앱을 업데이트한 뒤 다시 확인해 주세요.")
        FlagViewState.InvalidResponse -> FCEmptyCard(title = "깃발 정보 형식이 올바르지 않습니다", message = "문제가 계속되면 문의해 주세요.")
        FlagViewState.Loaded -> FCEmptyCard(title = "깃발이 없습니다", message = "이 축제에 등록된 깃발이 없습니다.")
    }
}

@Composable
private fun FlagList(
    state: FlagState,
    snapshot: FestivalLiveBoardSnapshot,
    onSortTapped: () -> Unit,
    onSortSelected: (FlagSort) -> Unit,
    onFollowTapped: (id: String, follow: Boolean, baseCount: Int?) -> Unit,
    onCardTapped: (String) -> Unit,
) {
    val rows =
        remember(state.directory, snapshot, state.isRefreshFailed) {
            FlagPresentation.rows(state.directory, snapshot, state.isRefreshFailed)
        }
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16)) {
        // Figma A-1: 정렬 버튼 아래 8에 오른쪽 끝을 맞춰 연다. 메뉴는 목록 위에 그린다.
        Box(modifier = Modifier.fillMaxWidth().zIndex(1f)) {
            FCSortButton(label = state.directory.sort.title, modifier = Modifier.align(Alignment.TopEnd), onClick = onSortTapped)
            if (state.directory.isSortMenuPresented) {
                FCSortMenu(
                    selection = FlagSort.entries.indexOf(state.directory.sort),
                    onSelectionChange = { onSortSelected(FlagSort.entries[it]) },
                    items = FlagSort.entries.map { it.title },
                    modifier = Modifier.align(Alignment.TopEnd).offset(y = SortButtonHeight + FestiCoreSpacing.paddingP8),
                )
            }
        }
        if (rows.isEmpty()) {
            if (snapshot.directoryFlags.isEmpty()) {
                FCEmptyCard(title = "깃발이 없습니다", message = "이 축제에 등록된 깃발이 없습니다.")
            } else {
                FCEmptyCard(title = "이 성향의 깃발이 아직 없어요", message = "다른 성향을 골라 보세요.")
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
                rows.forEach { row -> FlagCard(row, state.isOffline, onFollowTapped, onCardTapped) }
            }
        }
    }
}

@Composable
private fun FlagCard(
    row: FlagRow,
    isOffline: Boolean,
    onFollowTapped: (id: String, follow: Boolean, baseCount: Int?) -> Unit,
    onCardTapped: (String) -> Unit,
) {
    FCFlagCard(
        name = row.name,
        // Figma S04-E 오프라인은 지난 상태 · 위치를 보이지 않는다.
        status = if (isOffline) null else row.status?.chipStatus(),
        zone = row.zone,
        context =
            FCFlagCardContext.Directory(
                followers = row.followersText,
                tags = row.tags,
                isFollowing = row.isFollowing,
                onFollow = { onFollowTapped(row.id, !row.isFollowing, row.serverFollowerCount) },
            ),
        isFollowEnabled = row.isFollowEnabled,
        showsZone = !isOffline,
        onTap = { onCardTapped(row.id) },
        thumbnail = {
            val url = row.thumbnailUrl
            if (url != null) {
                FCRemoteImage(
                    url = url,
                    modifier = Modifier.fillMaxSize(),
                    placeholderIcon = R.drawable.ic_flag_20,
                    placeholderIconSize = PlaceholderIconSize,
                )
            } else {
                FCThumbnail(null)
            }
        },
    )
}

private fun FlagStatus.chipStatus(): FCStatusChipStatus = when (this) {
    FlagStatus.Active -> FCStatusChipStatus.Active
    FlagStatus.Resting -> FCStatusChipStatus.Resting
    FlagStatus.Scheduled -> FCStatusChipStatus.Scheduled
    FlagStatus.EndedToday -> FCStatusChipStatus.EndedToday
}

/** 3행 스켈레톤: 56×56 r12 + 막대 140×20 · 100×16. */
@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "깃발을 불러오는 중입니다" },
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
    ) {
        repeat(SKELETON_ROWS) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(FestiCoreSpacing.paddingP16),
                horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
            ) {
                FCSkeletonView(FCSkeletonShape.Round(FestiCoreRadius.r12), Modifier.size(ThumbnailSize))
                Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                    FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(SkeletonTitleWidth, SkeletonTitleHeight))
                    FCSkeletonView(FCSkeletonShape.Round(), Modifier.size(SkeletonSubtitleWidth, SkeletonSubtitleHeight))
                }
            }
        }
    }
}

/** 아래에서 올라오는 토스트. 만료(2초)는 ViewModel이 맡는다. 사라지는 동안 마지막 문구를 둔다. */
@Composable
private fun Toast(message: String?, modifier: Modifier) {
    var lastMessage by remember { mutableStateOf(message) }
    if (message != null) lastMessage = message
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier.padding(bottom = FestiCoreSpacing.paddingP16),
        enter = slideInVertically(tween(TOAST_MILLIS, easing = EaseOut)) { it } + fadeIn(tween(TOAST_MILLIS, easing = EaseOut)),
        exit = slideOutVertically(tween(TOAST_MILLIS, easing = EaseOut)) { it } + fadeOut(tween(TOAST_MILLIS, easing = EaseOut)),
    ) {
        lastMessage?.let { FCToast(FCToastItem(R.drawable.ic_circle_info_16, it)) }
    }
}

/**
 * 메뉴가 열린 동안 아무 곳이나 누르면 닫는다(iOS `simultaneousGesture(TapGesture)`). 누르기를 소비하지 않아 아래 버튼도 동작한다.
 * 손을 뗄 때 닫아 메뉴 항목 선택이 먼저 사라지지 않게 한다.
 */
private fun Modifier.dismissOnTap(enabled: Boolean, onDismiss: () -> Unit): Modifier = if (!enabled) {
    this
} else {
    pointerInput(onDismiss) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (waitForUpOrCancellation(PointerEventPass.Initial) != null) onDismiss()
        }
    }
}
