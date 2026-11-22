package io.festicore.feature.festival.liveboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.festicore.domain.festival.FestivalLiveBoardSchedule
import io.festicore.domain.festival.FestivalLiveBoardUseCase
import io.festicore.domain.festival.FestivalOverview
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
import java.time.Clock
import java.time.Instant
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
 */
@Composable
fun LiveBoardScreen(
    viewModel: LiveBoardViewModel,
    foregroundLifecycleOwner: LifecycleOwner,
    onBack: () -> Unit,
    onBoundary: (LiveBoardBoundary) -> Unit,
    onPresentingModalChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
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
    ForegroundEffect(foregroundLifecycleOwner, viewModel::onForegroundChanged)

    val currentOnPresentingModalChanged by rememberUpdatedState(onPresentingModalChanged)
    LaunchedEffect(state.isPresentingModal) { currentOnPresentingModalChanged(state.isPresentingModal) }
    DisposableEffect(Unit) { onDispose { currentOnPresentingModalChanged(false) } }

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
}

/** 앱이 배경으로 갔다 돌아올 때만 알린다(iOS `scenePhase`). 처음 붙을 때 받는 시작 알림은 넘긴다. */
@Composable
private fun ForegroundEffect(lifecycleOwner: LifecycleOwner, onForegroundChanged: (Boolean) -> Unit) {
    val current by rememberUpdatedState(onForegroundChanged)
    DisposableEffect(lifecycleOwner) {
        var stopped = false
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        stopped = true
                        current(false)
                    }

                    Lifecycle.Event.ON_START -> if (stopped) current(true)

                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** 인스타그램 게시물을 Custom Tabs로 연다(설계 D10). 열 수 있는 앱이 없으면 일반 보기 요청으로 한 번 더 연다. */
private fun openExternalUrl(context: Context, url: String) {
    val uri = url.toUri()
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            // 링크를 열 앱이 없다.
        }
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

    // Figma S01 A2: 기수 계정이면 로고 옆에 기수 버튼을 둔다. 알림 배지는 알림함 문서 전까지 0(설계 D11).
    FCTopBar(
        style = if (state.bearerFlagId != null) FCTopBarStyle.LogoBearer else FCTopBarStyle.Logo,
        backgroundColor = FestiCoreSemanticColor.backgroundNormal,
        badgeCount = 0,
        onTrailingAction = onNotificationTapped,
        onBearer = if (state.bearerFlagId != null) onBearerTapped else null,
    )
    if (model.showsOfflineStatus) {
        FCOfflineBadge(
            message =
                if (state.receivedAt == null) "오프라인 · 저장된 정보" else LiveBoardPresentation.offlineText(state.receivedAt, deviceNow),
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
            Text(
                text = festival.name,
                style = FestiCoreTextStyle.baseTitle2Bold.textStyle,
                color = FestiCoreSemanticColor.labelNormal.color,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false),
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
            style = FestiCoreTextStyle.baseCaption1Regular.textStyle,
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
        style = FestiCoreTextStyle.baseCaption1Bold.textStyle,
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
