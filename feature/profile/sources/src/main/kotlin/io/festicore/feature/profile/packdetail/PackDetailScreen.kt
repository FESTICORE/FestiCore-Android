package io.festicore.feature.profile.packdetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.card.FCLastRideCard
import io.festicore.ui.designkit.component.label.FCChecklistProgress
import io.festicore.ui.designkit.component.label.FCSourceLine
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.component.label.FCTagTone
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.list.FCListRow
import io.festicore.ui.designkit.component.list.FCSectionHeader
import io.festicore.ui.designkit.component.media.FCThumbnail
import io.festicore.ui.designkit.component.navigation.FCTab
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.popup.FCDialog
import io.festicore.ui.designkit.component.popup.FCDialogItem
import io.festicore.ui.designkit.component.selection.FCAddItemRow
import io.festicore.ui.designkit.component.selection.FCChecklistEditRow
import io.festicore.ui.designkit.component.selection.FCChecklistRow
import io.festicore.ui.designkit.component.selection.FCFlowLayout
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.sharedui.externallink.openExternalUrl
import io.festicore.ui.sharedui.remoteimage.FCRemoteImage
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

private val SkeletonHeights = listOf(96.dp, 160.dp, 120.dp)
private val RowMinHeight = 56.dp
private val ThumbnailSize = 44.dp
private val DividerHeight = 1.dp

// Figma Add Item Row(default): plus 20, 간격 10
private val AddItemSpacing = 10.dp
private val AddItemIconSize = 20.dp

private const val AUTO_DELETE_NOTICE = "페스티벌이 끝나고 30일 뒤에 자동으로 삭제돼요. 내 일정과 체크한 준비물은 남아 있어요."

/**
 * 받은 팩 상세(iOS `PackDetailView`, Figma Pack A~F). 이동은 콜백으로만 알리고 Coordinator가 정한다(T1).
 * 링크는 Custom Tabs로, 설치된 지도 앱 링크는 그 앱으로 연다(설계 쟁점 M10 · M11).
 * [onPresentingModalChanged]로 삭제 확인 팝업이 떠 있는지 알린다(탭 바 숨김, iOS `isProfileOverlayPresented`).
 */
@Composable
fun PackDetailScreen(
    viewModel: PackDetailViewModel,
    onBackRequested: () -> Unit,
    onPackChanged: () -> Unit,
    onPackDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    onPresentingModalChanged: (Boolean) -> Unit = {},
) {
    val state by viewModel.collectAsState()
    val context = LocalContext.current
    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            PackDetailSideEffect.BackRequested -> onBackRequested()
            PackDetailSideEffect.PackChanged -> onPackChanged()
            PackDetailSideEffect.PackDeleted -> onPackDeleted()
            is PackDetailSideEffect.OpenLink -> openExternalUrl(context, sideEffect.url, sideEffect.mapApp?.packageName)
        }
    }
    LaunchedEffect(viewModel) { viewModel.onAppear() }
    val currentOnPresentingModalChanged by rememberUpdatedState(onPresentingModalChanged)
    LaunchedEffect(state.isDeleteConfirmationPresented) { currentOnPresentingModalChanged(state.isDeleteConfirmationPresented) }
    DisposableEffect(Unit) { onDispose { currentOnPresentingModalChanged(false) } }
    PackDetailContent(
        state = state,
        modifier = modifier,
        actions =
            PackDetailActions(
                onBackTapped = viewModel::onBackTapped,
                onTabSelected = viewModel::onTabSelected,
                onRetryTapped = viewModel::onRetryTapped,
                onDirectionsAppeared = viewModel::onDirectionsAppeared,
                onLinkTapped = viewModel::onLinkTapped,
                onItemToggled = viewModel::onItemToggled,
                onEditTapped = viewModel::onEditTapped,
                onEditDoneTapped = viewModel::onEditDoneTapped,
                onItemRemoved = viewModel::onItemRemoved,
                onNewItemTextChanged = viewModel::onNewItemTextChanged,
                onAddItemTapped = viewModel::onAddItemTapped,
                onUpdateTapped = viewModel::onUpdateTapped,
                onCheckUpdateTapped = viewModel::onCheckUpdateTapped,
                onAutoDownloadToggled = viewModel::onAutoDownloadToggled,
                onDeleteTapped = viewModel::onDeleteTapped,
            ),
    )
    if (state.isDeleteConfirmationPresented) {
        FCDialog(
            item =
                FCDialogItem(
                    title = "${state.title} 팩을 삭제할까요?",
                    message = "시간표·가는 길·준비물을 오프라인으로 볼 수 없게 돼요. 내 일정과 체크한 준비물은 남아 있어요.",
                    confirmTitle = "삭제",
                    onConfirm = viewModel::onDeleteConfirmed,
                ),
            onDismiss = viewModel::onDeleteDismissed,
        )
    }
}

/** 화면 동작 묶음. 프리뷰는 기본값(아무것도 안 함)을 쓴다. */
class PackDetailActions(
    val onBackTapped: () -> Unit = {},
    val onTabSelected: (PackDetailTab) -> Unit = {},
    val onRetryTapped: () -> Unit = {},
    val onDirectionsAppeared: () -> Unit = {},
    val onLinkTapped: (String, MapApp?) -> Unit = { _, _ -> },
    val onItemToggled: (String) -> Unit = {},
    val onEditTapped: () -> Unit = {},
    val onEditDoneTapped: () -> Unit = {},
    val onItemRemoved: (String) -> Unit = {},
    val onNewItemTextChanged: (String) -> Unit = {},
    val onAddItemTapped: () -> Unit = {},
    val onUpdateTapped: () -> Unit = {},
    val onCheckUpdateTapped: () -> Unit = {},
    val onAutoDownloadToggled: (Boolean) -> Unit = {},
    val onDeleteTapped: () -> Unit = {},
)

/** 팩 상세(순수 UI). 탭 바와 본문 페이지가 같은 [PackDetailState.tab]을 쓴다. */
@Composable
fun PackDetailContent(
    state: PackDetailState,
    modifier: Modifier = Modifier,
    actions: PackDetailActions = PackDetailActions(),
) {
    Column(modifier = modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
        FCTopBar(style = FCTopBarStyle.Back, title = state.title, onBack = actions.onBackTapped)
        FCTab(
            selection = state.tab.ordinal,
            onSelectionChange = { actions.onTabSelected(PackDetailTab.at(it)) },
            items = PackDetailTab.entries.map { it.title },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.load) {
                PackDetailLoad.Idle, PackDetailLoad.Loading -> LoadingContent()
                PackDetailLoad.Failed -> MessageContent("팩을 불러오지 못했어요", actionTitle = "다시 시도", onAction = actions.onRetryTapped)
                PackDetailLoad.Missing -> MessageContent("기기에 받은 팩이 없어요", actionTitle = null, onAction = null)
                PackDetailLoad.Loaded -> state.detail?.let { Pages(state, it, actions) }
            }
        }
    }
}

/** 탭 바와 같은 선택 값을 써서 본문을 좌우로 넘기면 탭도 함께 바뀐다. */
@Composable
private fun Pages(
    state: PackDetailState,
    detail: FestivalPackDetail,
    actions: PackDetailActions,
) {
    val pagerState = rememberPagerState(initialPage = state.tab.ordinal) { PackDetailTab.entries.size }
    LaunchedEffect(state.tab) {
        if (pagerState.currentPage != state.tab.ordinal) pagerState.animateScrollToPage(state.tab.ordinal)
    }
    val currentTab by rememberUpdatedState(state.tab)
    val onTabSelected by rememberUpdatedState(actions.onTabSelected)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = PackDetailTab.at(page)
            if (tab != currentTab) onTabSelected(tab)
        }
    }
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        // 탭마다 스크롤 위치를 따로 둔다.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(top = FestiCoreSpacing.paddingP16)
                    .fcScrollBottomInset(),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
        ) {
            when (PackDetailTab.at(page)) {
                PackDetailTab.Directions -> Directions(state, detail, actions)
                PackDetailTab.Checklist -> Checklist(state, detail, actions)
                PackDetailTab.Manage -> Manage(state, detail.pack, actions)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) {
        SkeletonHeights.forEach { height ->
            FCSkeletonView(
                shape = FCSkeletonShape.Round(cornerRadius = FestiCoreRadius.r16),
                modifier = Modifier.fillMaxWidth().height(height),
            )
        }
    }
}

@Composable
private fun MessageContent(
    message: String,
    actionTitle: String?,
    onAction: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = FestiCoreTextStyle.baseBodyRegular.textStyle, color = FestiCoreSemanticColor.labelNeutral.color)
        if (actionTitle != null && onAction != null) {
            FCButton(title = actionTitle, style = FCButtonStyle.Secondary, size = FCButtonSize.Medium, onClick = onAction)
        }
    }
}

// A. 가는 길

@Composable
private fun Directions(
    state: PackDetailState,
    detail: FestivalPackDetail,
    actions: PackDetailActions,
) {
    val directions = detail.directions
    if (directions.isEmpty) {
        Card {
            Text(
                text = "가는 길 정보가 아직 없어요",
                modifier = Modifier.fillMaxWidth().heightIn(min = RowMinHeight).wrapContentHeight(Alignment.CenterVertically),
                style = FestiCoreTextStyle.baseBodyRegular.textStyle,
                color = FestiCoreSemanticColor.labelNeutral.color,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    if (directions.lastShuttleAt != null || directions.lastTrainAt != null) {
        FCLastRideCard(
            shuttle = PackDetailPresentation.timeText(directions.lastShuttleAt),
            train = PackDetailPresentation.timeText(directions.lastTrainAt),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    directions.shuttle?.let { shuttle ->
        DirectionSection("셔틀", shuttle) {
            shuttle.links.forEach { link -> LinkButton(link.label, Modifier.fillMaxWidth()) { actions.onLinkTapped(link.url, null) } }
        }
    }
    val transit = directions.transit
    if (transit != null) {
        DirectionSection("대중교통", transit) { MapButtons(state, directions, actions) }
    } else if (directions.naverMapUrl != null || directions.kakaoMapUrl != null) {
        Card { MapButtons(state, directions, actions) }
    }
    directions.parking?.let { DirectionSection("주차", it) {} }
    directions.memo?.let { memo ->
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                CardTitle("팀 추천 메모")
                CardBody(memo)
            }
        }
    }
    PackDetailPresentation.sourceText(directions.source ?: detail.source)?.let {
        // 시안 Pack A의 출처 줄은 본문 폭 그대로 띠를 깐다.
        FCSourceLine(source = it, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DirectionSection(
    title: String,
    section: FestivalPackDetail.Section,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
            Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                CardTitle(title)
                PackDetailPresentation.sectionBody(section)?.let { CardBody(it) }
            }
            actions()
        }
    }
}

@Composable
private fun MapButtons(
    state: PackDetailState,
    directions: FestivalPackDetail.Directions,
    actions: PackDetailActions,
) {
    val buttons = listOfNotNull(directions.naverMapUrl?.let { MapApp.Naver to it }, directions.kakaoMapUrl?.let { MapApp.Kakao to it })
    if (buttons.isEmpty()) return
    LaunchedEffect(Unit) { actions.onDirectionsAppeared() }
    Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        buttons.forEach { (app, url) ->
            LinkButton(mapTitle(app, state.installedMapApps), Modifier.weight(1f)) { actions.onLinkTapped(url, app) }
        }
    }
}

/** Figma S11 G: 지도 앱이 없으면 같은 https 링크를 웹으로 연다고 알린다. 확인 전에는 앱 이름만 쓴다. */
private fun mapTitle(app: MapApp, installed: Set<MapApp>?): String {
    val title =
        when (app) {
            MapApp.Naver -> "네이버 지도"
            MapApp.Kakao -> "카카오맵"
        }
    return if (installed == null || app in installed) title else "$title 웹 ↗"
}

@Composable
private fun LinkButton(
    title: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FCButton(
        title = title,
        modifier = modifier,
        style = FCButtonStyle.Secondary,
        size = FCButtonSize.Medium,
        isFullWidth = true,
        onClick = onClick,
    )
}

// B · C. 준비물

@Composable
private fun Checklist(
    state: PackDetailState,
    detail: FestivalPackDetail,
    actions: PackDetailActions,
) {
    val rows = state.checklistRows
    // iOS처럼 진행도(자체 배경 · 여백)를 카드로 한 번 더 감싼다.
    Card { FCChecklistProgress(checked = state.checkedCount, total = rows.size, modifier = Modifier.fillMaxWidth()) }
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        FCSectionHeader(
            title = "챙길 것",
            actionTitle = if (state.isEditingChecklist) "완료" else "편집",
            onAction = if (state.isEditingChecklist) actions.onEditDoneTapped else actions.onEditTapped,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FestiCoreRadius.r16))
                    .background(FestiCoreSemanticColor.backgroundElevated.color),
        ) {
            rows.forEachIndexed { index, row ->
                if (state.isEditingChecklist) {
                    FCChecklistEditRow(
                        label = row.label,
                        onDelete = if (row.isRequired) null else ({ actions.onItemRemoved(row.id) }),
                    )
                } else {
                    FCChecklistRow(label = row.label, isChecked = row.isChecked, isRequired = row.isRequired) {
                        actions.onItemToggled(row.id)
                    }
                }
                if (index != rows.lastIndex) RowDivider()
            }
            if (state.isEditingChecklist || rows.isEmpty()) {
                if (rows.isNotEmpty()) RowDivider()
                FCAddItemRow(text = state.newItemText, onTextChange = actions.onNewItemTextChanged, onAdd = actions.onAddItemTapped)
            } else {
                RowDivider()
                AddItemPrompt(onClick = actions.onEditTapped)
            }
        }
        if (state.isEditingChecklist && rows.any { it.isRequired }) {
            Footnote("필수 항목은 운영팀이 정한 거라 지울 수 없어요.")
        }
    }
    if (detail.rules.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCSectionHeader(title = "반입 금지")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
                    // Figma Pack B Tags 간격 6
                    FCFlowLayout(spacing = FestiCoreSpacing.marginM6) {
                        detail.rules.forEach { FCTag(label = it) }
                    }
                    PackDetailPresentation.sourceText(detail.source)?.let {
                        // 카드 안에서는 출처 줄의 띠와 안쪽 여백을 빼고 카드 여백을 쓴다.
                        FCSourceLine(source = it, isInset = false)
                    }
                }
            }
        }
    }
}

/** Figma Add Item Row(default): plus 20, 간격 10, Callout/Medium · Label/Neutral. 누르면 편집을 켠다. */
@Composable
private fun AddItemPrompt(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(RowMinHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(AddItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_plus_20),
            contentDescription = null,
            modifier = Modifier.size(AddItemIconSize),
            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNeutral.color),
        )
        Text(text = "내 항목 추가", style = FestiCoreTextStyle.baseCalloutMedium.textStyle, color = FestiCoreSemanticColor.labelNeutral.color)
        Spacer(modifier = Modifier.weight(1f))
    }
}

// D · E. 팩 관리

@Composable
private fun Manage(
    state: PackDetailState,
    pack: FestivalReceivedPack,
    actions: PackDetailActions,
) {
    val isBusy = state.isUpdating || state.isDeleting
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12), verticalAlignment = Alignment.CenterVertically) {
            PackThumbnail(pack)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM2)) {
                Text(
                    text = pack.name,
                    style = FestiCoreTextStyle.baseCalloutMedium.textStyle,
                    color = FestiCoreSemanticColor.labelNormal.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = PackDetailPresentation.packStatus(pack),
                    style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                    color = FestiCoreSemanticColor.labelAssistive.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pack.hasUpdate) FCTag(label = "업데이트", tone = FCTagTone.Warning)
        }
    }
    if (pack.hasUpdate) {
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCButton(
                title = PackDetailPresentation.updateTitle(state.isUpdating, state.changes),
                modifier = Modifier.fillMaxWidth(),
                style = FCButtonStyle.Primary,
                isEnabled = !isBusy,
                isFullWidth = true,
                onClick = actions.onUpdateTapped,
            )
            PackDetailPresentation.changeSummary(state.changes)?.let { Footnote("바뀐 내용: $it") }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
        FCSectionHeader(title = "팩 정보")
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FestiCoreRadius.r16))
                    .background(FestiCoreSemanticColor.backgroundElevated.color),
        ) {
            InfoRow("버전", PackDetailPresentation.versionText(pack))
            RowDivider()
            InfoRow("마지막 확인", pack.checkedAt?.let(PackDetailPresentation::dateText) ?: "-")
            RowDivider()
            InfoRow("용량", PackDetailPresentation.sizeText(pack.sizeBytes))
            RowDivider()
            FCListRow(label = "와이파이에서 자동으로 받기", isOn = state.autoDownloadOnWiFi, onIsOnChange = actions.onAutoDownloadToggled)
        }
    }
    if (!pack.hasUpdate) {
        FCButton(
            title = "지금 업데이트 확인",
            modifier = Modifier.fillMaxWidth(),
            style = FCButtonStyle.Secondary,
            isEnabled = !isBusy,
            isFullWidth = true,
            onClick = actions.onCheckUpdateTapped,
        )
    }
    state.manageMessage?.let {
        Footnote(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    Text(
        text = "팩 삭제",
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(FestiCoreRadius.r16))
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .clickable(enabled = !isBusy, role = Role.Button, onClick = actions.onDeleteTapped)
                .heightIn(min = RowMinHeight)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = FestiCoreSpacing.paddingP16),
        style = FestiCoreTextStyle.baseBodyRegular.textStyle,
        color = FestiCoreSemanticColor.statusNegative.color,
    )
    // Figma Pack D·E·F: 자동 삭제 안내는 Caption1/Regular 12pt(설계 쟁점 M8)
    Text(
        text = AUTO_DELETE_NOTICE,
        modifier = Modifier.padding(horizontal = FestiCoreSpacing.paddingP4),
        style = FestiCoreTextStyle.baseCaption1Regular.textStyle,
        color = FestiCoreSemanticColor.labelAssistive.color,
    )
}

@Composable
private fun PackThumbnail(pack: FestivalReceivedPack) {
    if (pack.thumbnailUrl != null) {
        FCRemoteImage(
            url = pack.thumbnailUrl,
            modifier = Modifier.size(ThumbnailSize).clip(RoundedCornerShape(FestiCoreRadius.r8)),
        )
    } else {
        FCThumbnail(image = null, side = ThumbnailSize)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .padding(horizontal = FestiCoreSpacing.paddingP16)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = FestiCoreTextStyle.baseBodyRegular.textStyle, color = FestiCoreSemanticColor.labelNormal.color)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = FestiCoreTextStyle.baseBodyRegular.textStyle.copy(fontFeatureSettings = "tnum"),
            color = FestiCoreSemanticColor.labelNeutral.color,
        )
    }
}

// 공통

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(FestiCoreRadius.r16))
                .background(FestiCoreSemanticColor.backgroundElevated.color)
                .padding(FestiCoreSpacing.paddingP16),
    ) { content() }
}

@Composable
private fun CardTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        style = FestiCoreTextStyle.baseBodyBold.textStyle,
        color = FestiCoreSemanticColor.labelNormal.color,
    )
}

@Composable
private fun CardBody(text: String) {
    Text(text = text, style = FestiCoreTextStyle.baseCalloutRegular.textStyle, color = FestiCoreSemanticColor.labelNeutral.color)
}

@Composable
private fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = FestiCoreSpacing.paddingP4),
        style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
        color = FestiCoreSemanticColor.labelAssistive.color,
    )
}

@Composable
private fun RowDivider() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = FestiCoreSpacing.paddingP16)
                .height(DividerHeight)
                .background(FestiCoreSemanticColor.lineNormal.color),
    )
}
