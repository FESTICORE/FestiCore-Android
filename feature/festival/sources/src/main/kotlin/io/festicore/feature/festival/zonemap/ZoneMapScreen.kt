package io.festicore.feature.festival.zonemap

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.LifecycleOwner
import io.festicore.domain.festival.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.FestivalZoneMap
import io.festicore.feature.festival.liveboard.ForegroundEffect
import io.festicore.feature.festival.liveboard.LiveBoardPresentation
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.label.FCOfflineBadge
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.overlay.FCBanner
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import java.time.Clock
import java.time.Instant
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/** iOS `FestivalZoneMapView.Metric.mapVertical`. */
private val MapVerticalPadding = FestiCoreSpacing.paddingP16

/**
 * S06 존 지도(iOS `FestivalZoneMapView`). ViewModel을 잇고 수명 주기를 처리한다.
 * - 화면에 들어오고 나갈 때 [ZoneMapViewModel.onAppear] · [ZoneMapViewModel.onDisappear]를 보낸다.
 * - 포그라운드는 [foregroundLifecycleOwner](흐름 바깥 수명 주기)로 본다(보드와 같음).
 * - 시스템 뒤로는 `NavDisplay`가 맡는다. 자기 `BackHandler`를 두지 않는다.
 * - 상대 시각 · 오래됨은 State가 바뀔 때 다시 계산한다(iOS는 렌더 때 `date.now`를 읽는다. 라이브 감시 35초 주기로 갱신된다).
 */
@Composable
fun ZoneMapScreen(
    viewModel: ZoneMapViewModel,
    foregroundLifecycleOwner: LifecycleOwner,
    onBack: () -> Unit,
    onFlagDirectoryRequested: () -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemUTC(),
) {
    val state by viewModel.collectAsState()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnFlagDirectoryRequested by rememberUpdatedState(onFlagDirectoryRequested)
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            ZoneMapSideEffect.BackRequested -> currentOnBack()
            ZoneMapSideEffect.FlagDirectoryRequested -> currentOnFlagDirectoryRequested()
        }
    }
    DisposableEffect(viewModel) {
        viewModel.onAppear()
        onDispose { viewModel.onDisappear() }
    }
    ForegroundEffect(foregroundLifecycleOwner, viewModel::onForegroundChanged)

    val now = remember(state) { clock.instant() }
    ZoneMapContent(
        state = state,
        now = now,
        modifier = modifier,
        onBackTapped = viewModel::onBackTapped,
        onZoneTapped = viewModel::onZoneTapped,
        onMapBackgroundTapped = viewModel::onMapBackgroundTapped,
        onUpdatePackTapped = viewModel::onUpdatePackTapped,
        onFollowGuideTapped = viewModel::onFollowGuideTapped,
        onRetryTapped = viewModel::onRetryTapped,
    )
}

/** 존 지도 순수 UI. [now]는 오프라인 띠 · 마커 오래됨 · 카드 상대 시각의 기준이다. */
@Composable
internal fun ZoneMapContent(
    state: ZoneMapState,
    now: Instant,
    modifier: Modifier = Modifier,
    onBackTapped: () -> Unit = {},
    onZoneTapped: (String) -> Unit = {},
    onMapBackgroundTapped: () -> Unit = {},
    onUpdatePackTapped: () -> Unit = {},
    onFollowGuideTapped: () -> Unit = {},
    onRetryTapped: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        FCTopBar(style = FCTopBarStyle.Back, title = "존 지도", onBack = onBackTapped)
        if (state.isOffline) FCOfflineBadge(LiveBoardPresentation.offlineText(state.receivedAt, now))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val load = state.map) {
                ZoneMapLoad.Loading -> ZoneMapSkeleton()

                ZoneMapLoad.Missing, ZoneMapLoad.Failed -> Unavailable(
                    isMissing = load == ZoneMapLoad.Missing,
                    onRetryTapped = onRetryTapped,
                )

                is ZoneMapLoad.Loaded -> {
                    val model = ZoneMapModel.of(load.map, state.snapshot, now)
                    if (load.map.hasLayout) {
                        MapContent(
                            map = load.map,
                            model = model,
                            state = state,
                            now = now,
                            onZoneTapped = onZoneTapped,
                            onMapBackgroundTapped = onMapBackgroundTapped,
                            onUpdatePackTapped = onUpdatePackTapped,
                            onFollowGuideTapped = onFollowGuideTapped,
                        )
                    } else {
                        ZoneList(map = load.map, model = model, onZoneTapped = onZoneTapped)
                    }
                }
            }
        }
    }
}

@Composable
private fun MapContent(
    map: FestivalZoneMap,
    model: ZoneMapModel,
    state: ZoneMapState,
    now: Instant,
    onZoneTapped: (String) -> Unit,
    onMapBackgroundTapped: () -> Unit,
    onUpdatePackTapped: () -> Unit,
    onFollowGuideTapped: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth
        val density = LocalDensity.current
        val scrollState = rememberScrollState()
        // 카드 · 범례가 차지한 높이(바깥 여백 포함). iOS `safeAreaInset(edge: .bottom)`처럼 그만큼 지도를 더 올려 볼 수 있게 한다.
        var overlayHeightPx by remember { mutableIntStateOf(0) }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            ZoneMapCanvas(
                map = map,
                model = model,
                selectedZoneId = state.selectedZoneId,
                width = width,
                onZoneTap = onZoneTapped,
                onBackgroundTap = onMapBackgroundTapped,
                modifier = Modifier.padding(vertical = MapVerticalPadding),
            )
            Spacer(Modifier.height(with(density) { overlayHeightPx.toDp() }))
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .onSizeChanged { overlayHeightPx = it.height }
                    .padding(FestiCoreSpacing.paddingP16),
        ) {
            BottomOverlay(map, model, state.selectedZoneId, state.snapshot, state.viewerUserId, now, onFollowGuideTapped)
        }
        if (state.isMapChanged) {
            FCBanner(
                text = "지도가 바뀌었어요 · 팩을 업데이트해 주세요",
                modifier = Modifier.align(Alignment.TopCenter).padding(FestiCoreSpacing.paddingP16),
                onClick = onUpdatePackTapped,
            )
        }
        RevealSelectedZone(map, state.selectedZoneId, width, maxHeight, scrollState) { overlayHeightPx }
    }
}

/**
 * S06 E. 고른 존이 카드에 가리면 카드 위로 보이도록 지도를 올린다(iOS `scrollTo(revealID, anchor: .bottom)`).
 * 기준 = 지도 위 여백 + (존 아래) × 지도 높이 + 34. 그 끝이 보이는 영역(뷰 높이 − 아래 겹침) 바닥에 오게 스크롤한다.
 * 처음에는 바로, 선택이 바뀔 때는 애니메이션으로 옮긴다. 카드 높이가 반영되도록 두 프레임 기다린 뒤 계산한다.
 */
@Composable
private fun RevealSelectedZone(
    map: FestivalZoneMap,
    selectedZoneId: String?,
    width: Dp,
    viewportHeight: Dp,
    scrollState: ScrollState,
    overlayHeightPx: () -> Int,
) {
    val density = LocalDensity.current
    var hasRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(selectedZoneId) {
        val animate = hasRevealed
        hasRevealed = true
        val area = map.zone(selectedZoneId)?.area ?: return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        val target =
            with(density) {
                val bottom = MapVerticalPadding + zoneMapHeight(map, width) * (area.y + area.height).toFloat() + RevealMargin
                bottom.toPx() - (viewportHeight.toPx() - overlayHeightPx())
            }.toInt().coerceIn(0, scrollState.maxValue)
        if (animate) scrollState.animateScrollTo(target) else scrollState.scrollTo(target)
    }
}

/** 고른 존 카드 > 안내 띠 > 범례. */
@Composable
private fun BottomOverlay(
    map: FestivalZoneMap,
    model: ZoneMapModel,
    selectedZoneId: String?,
    snapshot: FestivalLiveBoardSnapshot?,
    viewerUserId: String?,
    now: Instant,
    onFollowGuideTapped: () -> Unit,
) {
    val zone = map.zone(selectedZoneId)
    when {
        zone != null -> ZoneMapCard(ZoneMapModel.card(zone, map, snapshot, viewerUserId, now))

        model.bottomBanner == ZoneMapModel.BottomBanner.FollowGuide ->
            FCBanner(text = "깃발을 팔로우하면 지도에 크게 보여요", onClick = onFollowGuideTapped)

        model.bottomBanner == ZoneMapModel.BottomBanner.FlagsDisabled ->
            FCBanner(text = "지금은 깃발 위치를 볼 수 없어요 · 크루만 보여요", showsChevron = false, onClick = {})

        else -> ZoneMapLegend(showsFollowed = model.hasFollowedFlags, showsCrew = model.hasCrew, showsOther = model.flagsEnabled)
    }
}

@Composable
private fun ZoneList(
    map: FestivalZoneMap,
    model: ZoneMapModel,
    onZoneTapped: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        ZoneMapInfoLine("이 페스티벌은 지도가 없어서 존 목록으로 보여 드려요.")
        ZoneMapList(rows = map.zones.map { ZoneMapListRow(it.id, it.name, model.countText(it.id)) }, onTap = onZoneTapped)
    }
}

@Composable
private fun Unavailable(isMissing: Boolean, onRetryTapped: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(FestiCoreSpacing.paddingP16),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = if (isMissing) "이 페스티벌 팩을 받으면 존 지도를 볼 수 있어요" else "존 지도를 불러오지 못했어요",
                style =
                    FestiCoreTextStyle.baseBodyMedium.textStyleWithoutLineBox.copy(
                        color = FestiCoreSemanticColor.labelNeutral.color,
                        textAlign = TextAlign.Center,
                    ),
            )
            FCButton(title = "다시 시도", style = FCButtonStyle.Secondary, size = FCButtonSize.Medium, onClick = onRetryTapped)
        }
    }
}
