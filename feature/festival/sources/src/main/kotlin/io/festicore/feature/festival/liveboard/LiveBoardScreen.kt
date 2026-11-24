package io.festicore.feature.festival.liveboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import io.festicore.domain.festival.entity.FestivalLiveBoardSchedule
import io.festicore.domain.festival.entity.FestivalOverview
import io.festicore.domain.festival.liveboard.FestivalLiveBoardUseCase
import io.festicore.feature.festival.liveboard.crewgather.CrewGatherSheet
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.card.FCCardAction
import io.festicore.ui.designkit.component.card.FCCountdownCard
import io.festicore.ui.designkit.component.card.FCDoneCard
import io.festicore.ui.designkit.component.card.FCDoneCardStat
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.card.FCHomeCard
import io.festicore.ui.designkit.component.card.FCNextSetCard
import io.festicore.ui.designkit.component.label.FCOfflineBadge
import io.festicore.ui.designkit.component.label.FCPackDownloadProgress
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.component.popup.FCDialogTextAlign
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import io.festicore.ui.sharedui.externallink.openExternalUrl
import java.time.Clock
import java.time.Instant
import kotlin.math.ceil
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

private val ChevronSize = 20.dp
private val CardShape = RoundedCornerShape(FestiCoreRadius.r16)
private val DoneSkeletonHeight = 164.dp

/**
 * S01 라이브 보드(iOS `FestivalLiveBoardRouteView` + `FestivalLiveBoardScreen`). ViewModel을 잇고 외부 링크 · 수명 주기를 처리한다.
 *
 * - 화면에 들어오고 나갈 때 [LiveBoardViewModel.onAppear] · [LiveBoardViewModel.onDisappear]를 보낸다(iOS `task` · `onDisappear`).
 * - 포그라운드는 [foregroundLifecycleOwner](스택 항목이 아닌 흐름 바깥 수명 주기)로 본다. 스택 항목 수명 주기는 위에 화면이 쌓여도 내려간다.
 * - 뒤로 버튼은 없다. 시스템 뒤로 · 예측 뒤로는 `NavDisplay`가 맡는다(설계 D7). 그래서 자기 `BackHandler`를 두지 않는다.
 * - [onPresentingModalChanged]로 시트 · 다이얼로그가 떠 있는지 알린다(탭 바 숨김, 설계 D9).
 * - [onFestivalsChanged]로 바꿀 수 있는 축제 ID를 알리고, [pendingFestivalId](알림함에서 연 축제)를 받으면 그 축제로 바꾼 뒤
 *   [onPendingFestivalConsumed]를 부른다(설계 E8).
 */
@Composable
fun LiveBoardScreen(
    viewModel: LiveBoardViewModel,
    foregroundLifecycleOwner: LifecycleOwner,
    onBack: () -> Unit,
    onBoundary: (LiveBoardBoundary) -> Unit,
    onPresentingModalChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onFestivalsChanged: (Set<String>) -> Unit = {},
    pendingFestivalId: String? = null,
    onPendingFestivalConsumed: () -> Unit = {},
    clock: Clock = Clock.systemUTC(),
) {
    val state by viewModel.collectAsState()
    val context = LocalContext.current

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            LiveBoardSideEffect.BackRequested -> onBack()
            is LiveBoardSideEffect.OpenUrl -> openExternalUrl(context, sideEffect.url)
            is LiveBoardSideEffect.Boundary -> onBoundary(sideEffect.boundary)
        }
    }

    DisposableEffect(viewModel) {
        viewModel.onAppear()
        onDispose { viewModel.onDisappear() }
    }
    ForegroundEffect(foregroundLifecycleOwner, viewModel, viewModel::onForegroundChanged)

    val currentOnPresentingModalChanged by rememberUpdatedState(onPresentingModalChanged)
    LaunchedEffect(state.isPresentingModal) { currentOnPresentingModalChanged(state.isPresentingModal) }
    DisposableEffect(Unit) { onDispose { currentOnPresentingModalChanged(false) } }

    val festivalIds = remember(state.festivals) { state.festivals.map { it.id }.toSet() }
    val currentOnFestivalsChanged by rememberUpdatedState(onFestivalsChanged)
    LaunchedEffect(festivalIds) { currentOnFestivalsChanged(festivalIds) }
    val currentOnPendingFestivalConsumed by rememberUpdatedState(onPendingFestivalConsumed)
    LaunchedEffect(pendingFestivalId) {
        val festivalId = pendingFestivalId ?: return@LaunchedEffect
        viewModel.onNotificationFestivalRequested(festivalId)
        currentOnPendingFestivalConsumed()
    }

    // iOS `TimelineView(.periodic(by: 30))`: 화면 시각을 30초마다 다시 계산한다.
    var deviceNow by remember { mutableStateOf(clock.instant()) }
    LaunchedEffect(clock) {
        while (true) {
            deviceNow = clock.instant()
            delay(FestivalLiveBoardUseCase.CLOCK_REFRESH_INTERVAL)
        }
    }

    LiveBoardContent(
        state = state,
        deviceNow = deviceNow,
        modifier = modifier,
        onBack = onBack,
        onNotificationTapped = viewModel::onNotificationTapped,
        onBearerTapped = viewModel::onBearerTapped,
        onSwitchTapped = viewModel::onSwitchTapped,
        onSwitcherDismissed = viewModel::onSwitcherDismissed,
        onSwitcherFestivalChosen = viewModel::onSwitcherFestivalChosen,
        onSeeAllFestivals = viewModel::onSeeAllFestivals,
        onRetryLiveBoard = viewModel::onRetryLiveBoard,
        onRetryMySchedule = viewModel::onRetryMySchedule,
        onRetryOnlineDetail = viewModel::onRetryOnlineDetail,
        onOfficialNewsPostTapped = viewModel::onOfficialNewsPostTapped,
        onCrewLoginTapped = viewModel::onCrewLoginTapped,
        onGatherCrewTapped = viewModel::onGatherCrewTapped,
        onZoneChipTapped = viewModel::onZoneChipTapped,
        onCrewTapped = viewModel::onCrewTapped,
        onCreateCrewTapped = viewModel::onCreateCrewTapped,
        onFollowLoginTapped = viewModel::onFollowLoginTapped,
        onFindFlagTapped = viewModel::onFindFlagTapped,
        onHideFollowGuideTapped = viewModel::onHideFollowGuideTapped,
        onFollowGuideDialogDismissed = viewModel::onFollowGuideDialogDismissed,
        onHideFollowGuideConfirmed = viewModel::onHideFollowGuideConfirmed,
        onHomeDirectionsTapped = viewModel::onHomeDirectionsTapped,
        onHomeTogetherTapped = viewModel::onHomeTogetherTapped,
    )
    CrewGatherSheet(
        input = state.crewGather,
        onDismiss = viewModel::onCrewGatherDismissed,
        onGathered = viewModel::onCrewGathered,
    )
}

/**
 * 앱이 배경으로 갔다 돌아올 때만 알린다(iOS `scenePhase`). 처음 붙을 때 받는 시작 알림은 넘긴다.
 * 탭을 바꿔 화면이 빠져도 [viewModel]이 남아 있는 동안은 계속 본다(iOS `TabView`의 숨은 탭도 `scenePhase`를 받는다).
 * 그래서 숨은 탭에서 앱이 배경으로 가도 팩 감시 · 폴링이 멈춘다. [viewModel]이 정리되거나 [lifecycleOwner]가 끝나면 관찰을 뗀다.
 */
@Composable
internal fun ForegroundEffect(
    lifecycleOwner: LifecycleOwner,
    viewModel: ViewModel,
    onForegroundChanged: (Boolean) -> Unit,
) {
    val current by rememberUpdatedState(onForegroundChanged)
    DisposableEffect(lifecycleOwner, viewModel) {
        val binding =
            viewModel.getCloseable<ForegroundBinding>(ForegroundBinding.KEY)
                ?: ForegroundBinding().also { viewModel.addCloseable(ForegroundBinding.KEY, it) }
        binding.bind(lifecycleOwner) { current(it) }
        // 화면에서 빠질 때는 떼지 않는다. ViewModel 수명(정리 · 수명 주기 끝)에 맞춰 뗀다.
        onDispose {}
    }
}

/** [ForegroundEffect]가 ViewModel 수명 동안 붙여 두는 관찰. 수명 주기가 바뀌면(설정 변경) 새 것으로 옮긴다. */
internal class ForegroundBinding : AutoCloseable {
    private var owner: LifecycleOwner? = null
    private var stopped = false
    private var onForegroundChanged: (Boolean) -> Unit = {}
    private val observer =
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    stopped = true
                    onForegroundChanged(false)
                }

                Lifecycle.Event.ON_START -> if (stopped) onForegroundChanged(true)

                Lifecycle.Event.ON_DESTROY -> unbind()

                else -> Unit
            }
        }

    fun bind(lifecycleOwner: LifecycleOwner, onForegroundChanged: (Boolean) -> Unit) {
        this.onForegroundChanged = onForegroundChanged
        if (owner === lifecycleOwner) return
        unbind()
        owner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    private fun unbind() {
        owner?.lifecycle?.removeObserver(observer)
        owner = null
    }

    override fun close() = unbind()

    companion object {
        const val KEY = "io.festicore.feature.festival.ForegroundBinding"
    }
}

/** 라이브 보드(순수 UI). [deviceNow]는 기기 시각이고, 일정 · 단계는 서버 시각으로 보정해 계산한다. */
@Composable
fun LiveBoardContent(
    state: LiveBoardState,
    deviceNow: Instant,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNotificationTapped: () -> Unit = {},
    onBearerTapped: () -> Unit = {},
    onSwitchTapped: () -> Unit = {},
    onSwitcherDismissed: () -> Unit = {},
    onSwitcherFestivalChosen: (String) -> Unit = {},
    onSeeAllFestivals: () -> Unit = {},
    onRetryLiveBoard: () -> Unit = {},
    onRetryMySchedule: () -> Unit = {},
    onRetryOnlineDetail: () -> Unit = {},
    onOfficialNewsPostTapped: (String) -> Unit = {},
    onCrewLoginTapped: () -> Unit = {},
    onGatherCrewTapped: () -> Unit = {},
    onZoneChipTapped: (String) -> Unit = {},
    onCrewTapped: () -> Unit = {},
    onCreateCrewTapped: () -> Unit = {},
    onFollowLoginTapped: () -> Unit = {},
    onFindFlagTapped: () -> Unit = {},
    onHideFollowGuideTapped: () -> Unit = {},
    onFollowGuideDialogDismissed: () -> Unit = {},
    onHideFollowGuideConfirmed: () -> Unit = {},
    onHomeDirectionsTapped: () -> Unit = {},
    onHomeTogetherTapped: () -> Unit = {},
) {
    val festival = state.selectedFestival
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundNormal.color)
                .semantics { testTagsAsResourceId = true },
    ) {
        if (festival == null) {
            // 선택 축제를 찾지 못했다(iOS `FestivalLiveBoardRouteView`).
            FCTopBar(style = FCTopBarStyle.Back, title = "라이브 보드", onBack = onBack)
            Column(modifier = Modifier.padding(FestiCoreSpacing.paddingP16)) {
                if (state.isCatalogLoading) {
                    LiveBoardSkeleton()
                } else {
                    FCEmptyCard(
                        title = "페스티벌 정보를 찾을 수 없어요",
                        message = "목록으로 돌아가 다시 선택해 주세요.",
                        icon = R.drawable.ic_music_24,
                    )
                }
            }
        } else {
            Board(
                state = state,
                festival = festival,
                deviceNow = deviceNow,
                onNotificationTapped = onNotificationTapped,
                onBearerTapped = onBearerTapped,
                onSwitchTapped = onSwitchTapped,
                onRetryLiveBoard = onRetryLiveBoard,
                onRetryMySchedule = onRetryMySchedule,
                onRetryOnlineDetail = onRetryOnlineDetail,
                onOfficialNewsPostTapped = onOfficialNewsPostTapped,
                onCrewLoginTapped = onCrewLoginTapped,
                onGatherCrewTapped = onGatherCrewTapped,
                onZoneChipTapped = onZoneChipTapped,
                onCrewTapped = onCrewTapped,
                onCreateCrewTapped = onCreateCrewTapped,
                onFollowLoginTapped = onFollowLoginTapped,
                onFindFlagTapped = onFindFlagTapped,
                onHideFollowGuideTapped = onHideFollowGuideTapped,
                onHomeDirectionsTapped = onHomeDirectionsTapped,
                onHomeTogetherTapped = onHomeTogetherTapped,
            )
        }
    }

    FestivalSwitcherSheet(
        items = state.switcher,
        selectedFestivalId = state.festivalId,
        onDismissRequest = onSwitcherDismissed,
        onFestivalChosen = onSwitcherFestivalChosen,
        onSeeAllFestivals = onSeeAllFestivals,
    )
    if (state.isFollowGuideDialogPresented) {
        FCDialog(
            item =
                FCDialogItem(
                    title = "이 안내를 숨길까요?",
                    message = "이번 페스티벌 동안 라이브 보드에서 보이지 않아요.\n다시 보고 싶으면 마이 > 설정 > 라이브 보드에서 켜거나, 깃발 탭에서 언제든 깃발을 찾을 수 있어요.",
                    confirmTitle = "숨기기",
                    onConfirm = onHideFollowGuideConfirmed,
                ),
            onDismiss = onFollowGuideDialogDismissed,
            textAlign = FCDialogTextAlign.Center,
        )
    }
}

@Composable
private fun Board(
    state: LiveBoardState,
    festival: FestivalOverview,
    deviceNow: Instant,
    onNotificationTapped: () -> Unit,
    onBearerTapped: () -> Unit,
    onSwitchTapped: () -> Unit,
    onRetryLiveBoard: () -> Unit,
    onRetryMySchedule: () -> Unit,
    onRetryOnlineDetail: () -> Unit,
    onOfficialNewsPostTapped: (String) -> Unit,
    onCrewLoginTapped: () -> Unit,
    onGatherCrewTapped: () -> Unit,
    onZoneChipTapped: (String) -> Unit,
    onCrewTapped: () -> Unit,
    onCreateCrewTapped: () -> Unit,
    onFollowLoginTapped: () -> Unit,
    onFindFlagTapped: () -> Unit,
    onHideFollowGuideTapped: () -> Unit,
    onHomeDirectionsTapped: () -> Unit,
    onHomeTogetherTapped: () -> Unit,
) {
    val model = LiveBoardScreenModel(state)
    val pack = model.pack
    val now = LiveBoardPresentation.serverNow(state.snapshot?.serverAt, state.clockAnchorAt, deviceNow)
    val phase = LiveBoardPresentation.phase(pack, festival, now)

    // Figma S01 A2: 기수 계정이면 로고 옆에 기수 버튼을 둔다. 배지는 안 읽은 알림 수(iOS `badgeCount(notificationUnreadCount)`).
    FCTopBar(
        style = if (state.bearerFlagId != null) FCTopBarStyle.LogoBearer else FCTopBarStyle.Logo,
        backgroundColor = FestiCoreSemanticColor.backgroundNormal,
        badgeCount = state.notificationUnreadCount,
        onTrailingAction = onNotificationTapped,
        onBearer = if (state.bearerFlagId != null) onBearerTapped else null,
    )
    if (model.showsOfflineStatus) {
        FCOfflineBadge(
            message =
                if (state.receivedAt == null) "오프라인 · 저장된 정보" else LiveBoardPresentation.offlineText(state.receivedAt, deviceNow),
        )
    }
    state.notificationBanner?.let { notification ->
        LiveBoardNotificationBanner(
            notification = notification,
            now = deviceNow,
            onClick = onNotificationTapped,
            modifier = Modifier.padding(horizontal = FestiCoreSpacing.paddingP16, vertical = FestiCoreSpacing.paddingP8),
        )
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = FestiCoreSpacing.paddingP16, end = FestiCoreSpacing.paddingP16, top = FestiCoreSpacing.paddingP16)
                .fcScrollBottomInset(),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
    ) {
        Header(festival, LiveBoardPresentation.dayLine(pack, festival, now), onSwitchTapped)
        model.packDownload?.let { download ->
            FCPackDownloadProgress(
                text = download.text,
                progress = download.progress,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(FestiCoreSemanticColor.backgroundElevated.color, CardShape)
                        .padding(FestiCoreSpacing.paddingP16),
            )
        }
        val officialNews = @Composable {
            LiveBoardOfficialNewsSection(news = state.visibleOfficialNews, now = now, onPostTapped = onOfficialNewsPostTapped)
        }
        val crew = @Composable {
            LiveBoardCrewSection(
                snapshot = state.snapshot,
                isUnavailable = state.isUnavailable,
                pack = pack,
                hasSession = state.hasSession,
                sessionUserId = state.sessionUserId,
                showsGather = phase != LiveBoardPresentation.Phase.Ended,
                showsOfflineStatus = model.showsOfflineStatus,
                now = now,
                onCrewLoginTapped = onCrewLoginTapped,
                onGatherCrewTapped = onGatherCrewTapped,
                onZoneChipTapped = onZoneChipTapped,
                onCrewTapped = onCrewTapped,
                onCreateCrewTapped = onCreateCrewTapped,
                onRetryTapped = onRetryLiveBoard,
            )
        }
        val followed = @Composable {
            LiveBoardFollowedFlagSection(
                snapshot = state.snapshot,
                isUnavailable = state.isUnavailable,
                pack = pack,
                hasSession = state.hasSession,
                showsFollowGuide = state.showsFollowGuide,
                now = now,
                festivalEnded = phase == LiveBoardPresentation.Phase.Ended,
                onFollowLoginTapped = onFollowLoginTapped,
                onFindFlagTapped = onFindFlagTapped,
                onHideFollowGuideTapped = onHideFollowGuideTapped,
                onRetryTapped = onRetryLiveBoard,
            )
        }
        val home = @Composable { HomeCard(model, now, onHomeDirectionsTapped, onHomeTogetherTapped) }

        when {
            state.isUnpublished ->
                FCEmptyCard(
                    title = "라이브 보드 공개 전이에요",
                    message = "축제 라이브 보드가 아직 공개되지 않았어요. 공개 후 공연과 깃발 현황을 확인해 주세요.",
                    icon = R.drawable.ic_music_24,
                )

            phase == LiveBoardPresentation.Phase.Ended -> {
                DoneCard(state, model, festival, now, onRetryMySchedule, onRetryLiveBoard)
                home()
                officialNews()
                crew()
                if (pack?.flagsEnabled != false) followed()
            }

            else -> {
                if (phase is LiveBoardPresentation.Phase.FinalHour) home()
                if (state.isPackLoading) {
                    LiveBoardSkeleton()
                } else {
                    ScheduleCards(state, festival, now, deviceNow, onRetryOnlineDetail)
                }
                if (state.myScheduleUnavailable) MyScheduleFailedLine(onRetryMySchedule)
                if (!state.isPackLoading && LiveBoardPresentation.countdown(pack, festival, now) == null) {
                    LiveBoardStageSection(
                        rows =
                            pack?.let {
                                LiveBoardPresentation.stageRows(FestivalLiveBoardSchedule.stages(it, now), state.mySetIds)
                            }.orEmpty(),
                        updated = LiveBoardPresentation.relativeTime(state.receivedAt, deviceNow),
                        source = LiveBoardPresentation.sourceLine(pack?.source),
                    )
                }
                officialNews()
                crew()
                if (pack?.flagsEnabled != false) {
                    followed()
                    LiveBoardActiveFlagSection(
                        snapshot = state.snapshot,
                        isUnavailable = state.isUnavailable,
                        pack = pack,
                        now = now,
                        isBeforeOpening = phase.isBeforeOpening,
                        onRetryTapped = onRetryLiveBoard,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    festival: FestivalOverview,
    dayLine: String?,
    onSwitchTapped: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4)) {
        Row(
            modifier =
                Modifier
                    .clickable(onClick = onSwitchTapped)
                    .clearAndSetSemantics {
                        contentDescription = "페스티벌 바꾸기, ${festival.name}"
                        onClick {
                            onSwitchTapped()
                            true
                        }
                    },
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // iOS SwiftUI Text는 줄바꿈된 글자의 가장 긴 줄 너비만 차지하고(FestivalLiveBoardScreen.swift:316-322), 공백에서 줄바꿈된 줄은
            // 줄 끝 공백 폭까지 넣는다. 화살표는 그 뒤 8에 붙는다. Compose Text는 줄바꿈되면 가능한 폭을 다 쓰고 줄 끝 공백 폭을 빼므로
            // 가장 긴 줄 너비로 줄이되, 공백에서 끝난 줄에는 같은 글자 모양으로 잰 공백 한 칸 폭을 더한다.
            val nameStyle = FestiCoreTextStyle.baseTitle2Bold.textStyle
            val textMeasurer = rememberTextMeasurer()
            val spaceWidth =
                remember(textMeasurer, nameStyle) {
                    textMeasurer.measure("x x", nameStyle).size.width - textMeasurer.measure("xx", nameStyle).size.width
                }
            val nameLayout = remember { arrayOfNulls<TextLayoutResult>(1) }
            Text(
                text = festival.name,
                style = nameStyle,
                color = FestiCoreSemanticColor.labelNormal.color,
                maxLines = 2,
                onTextLayout = { nameLayout[0] = it },
                modifier =
                    Modifier.weight(1f, fill = false).layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val widest = nameLayout[0]?.let { layout ->
                            (0 until layout.lineCount).maxOf { line ->
                                val endsWithBreakSpace =
                                    line < layout.lineCount - 1 &&
                                        festival.name.getOrNull(layout.getLineEnd(line) - 1)?.isWhitespace() == true
                                layout.getLineRight(line) + if (endsWithBreakSpace) spaceWidth else 0
                            }
                        }
                        val width = widest?.let { ceil(it).toInt().coerceAtMost(placeable.width) } ?: placeable.width
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    },
            )
            // Figma S01 Festival Title: Chevron Down은 chevron-right 20을 90도 돌린 것이다.
            Image(
                painter = painterResource(R.drawable.ic_chevron_right_20),
                contentDescription = null,
                modifier = Modifier.size(ChevronSize).rotate(90f),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNormal.color),
            )
        }
        dayLine?.let {
            Text(text = it, style = FestiCoreTextStyle.baseFootnoteRegular.textStyle, color = FestiCoreSemanticColor.labelNeutral.color)
        }
    }
}

@Composable
private fun ScheduleCards(
    state: LiveBoardState,
    festival: FestivalOverview,
    now: Instant,
    deviceNow: Instant,
    onRetryOnlineDetail: () -> Unit,
) {
    val pack = state.displayDocument
    val countdown = LiveBoardPresentation.countdown(pack, festival, now)
    countdown?.let { FCCountdownCard(label = "개장까지", time = it.time, sub = it.sub) }
    val card = LiveBoardPresentation.setCard(pack, state.mySetIds, isBeforeOpening = countdown != null, now = now)
    when {
        card != null ->
            FCNextSetCard(
                artist = card.artist,
                meta = card.meta,
                remain = card.remain,
                updated = LiveBoardPresentation.updatedText(state.receivedAt, deviceNow),
                progress = card.progress,
                label = card.label,
            )

        pack == null && state.onlineDetailFailed ->
            Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                FCEmptyCard(
                    title = "시간표를 불러오지 못했어요",
                    message = "네트워크를 확인하고 다시 시도해 주세요.",
                    icon = R.drawable.ic_music_24,
                )
                RetryText(onClick = onRetryOnlineDetail)
            }

        else ->
            FCEmptyCard(
                title = "다음 공연이 없습니다",
                message = if (pack == null) "시간표를 불러오면 다음 공연을 볼 수 있어요." else "공식 시간표에 예정된 공연이 없습니다.",
                icon = R.drawable.ic_music_24,
            )
    }
}

@Composable
private fun MyScheduleFailedLine(onRetry: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "내 일정을 불러오지 못했습니다.",
            style = FestiCoreTextStyle.baseCaption1Regular.textStyleWithoutLineBox,
            color = FestiCoreSemanticColor.labelNeutral.color,
            modifier = Modifier.weight(1f),
        )
        RetryText(onClick = onRetry)
    }
}

/** iOS 기본 `Button("다시 시도")` + `baseCaption1Bold`. */
@Composable
private fun RetryText(onClick: () -> Unit) {
    Text(
        text = "다시 시도",
        style = FestiCoreTextStyle.baseCaption1Bold.textStyleWithoutLineBox,
        color = FestiCoreSemanticColor.labelNeutral.color,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun HomeCard(
    model: LiveBoardScreenModel,
    now: Instant,
    onHomeDirectionsTapped: () -> Unit,
    onHomeTogetherTapped: () -> Unit,
) {
    val ride = model.homeRide(now) ?: return
    FCHomeCard(
        remain = ride.remain,
        shuttle = ride.shuttle,
        train = ride.train,
        actions =
            listOf(
                FCCardAction("가는 길", style = FCButtonStyle.Ink, onClick = onHomeDirectionsTapped),
                FCCardAction("같이 귀가", style = FCButtonStyle.InkOutline, onClick = onHomeTogetherTapped),
            ),
    )
}

@Composable
private fun DoneCard(
    state: LiveBoardState,
    model: LiveBoardScreenModel,
    festival: FestivalOverview,
    now: Instant,
    onRetryMySchedule: () -> Unit,
    onRetryLiveBoard: () -> Unit,
) {
    val summary = model.doneSummary(now)
    when {
        summary != null ->
            FCDoneCard(
                title = "오늘 재미있게 즐겼나요?",
                sub = LiveBoardPresentation.doneSubtitle(model.pack, festival, now),
                stats =
                    listOf(
                        FCDoneCardStat(value = "${summary.seenSetCount}", label = "본 공연"),
                        FCDoneCardStat(value = "${summary.crewCount}명", label = "함께한 크루"),
                        FCDoneCardStat(value = "${summary.followedFlagCount}", label = "팔로우한 깃발"),
                    ),
            )

        state.hasSession && state.myScheduleUnavailable ->
            FCEmptyCard(
                title = "오늘의 기록을 불러오지 못했어요",
                message = "연결을 확인한 뒤 다시 시도해 주세요.",
                action = FCCardAction("다시 시도", onClick = onRetryMySchedule),
            )

        state.hasSession && state.isUnavailable ->
            FCEmptyCard(
                title = "오늘의 기록을 불러오지 못했어요",
                message = "연결을 확인한 뒤 다시 시도해 주세요.",
                action = FCCardAction("다시 시도", onClick = onRetryLiveBoard),
            )

        state.hasSession ->
            FCSkeletonView(
                shape = FCSkeletonShape.Round(FestiCoreRadius.r16),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(DoneSkeletonHeight)
                        .clearAndSetSemantics { contentDescription = "오늘의 공연 요약을 불러오는 중" },
            )
    }
}
