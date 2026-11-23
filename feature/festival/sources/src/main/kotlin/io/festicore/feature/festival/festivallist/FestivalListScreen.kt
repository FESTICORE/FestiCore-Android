package io.festicore.feature.festival.festivallist
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.festicore.domain.festival.entity.FestivalOverview
import io.festicore.feature.festival.accessibility.FestivalAccessibilityId
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.card.FCCardAction
import io.festicore.ui.designkit.component.card.FCEmptyCard
import io.festicore.ui.designkit.component.card.FCFestivalCard
import io.festicore.ui.designkit.component.card.FCFestivalCardPack
import io.festicore.ui.designkit.component.card.FCFestivalCardStatus
import io.festicore.ui.designkit.component.label.FCOfflineBadge
import io.festicore.ui.designkit.component.list.FCSearchResultRow
import io.festicore.ui.designkit.component.list.FCSearchResultRowBadgeStyle
import io.festicore.ui.designkit.component.list.FCSearchResultRowKind
import io.festicore.ui.designkit.component.list.FCSectionHeader
import io.festicore.ui.designkit.component.navigation.FCTab
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.overlay.FCSearchEmpty
import io.festicore.ui.designkit.component.selection.FCFlowLayout
import io.festicore.ui.designkit.component.selection.FCRecentKeyword
import io.festicore.ui.designkit.component.toast.FCToastHost
import io.festicore.ui.designkit.component.toast.FCToastItem
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import java.time.Clock
import java.time.Instant
import kotlin.math.abs
import kotlin.math.hypot
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * S00 페스티벌 목록(iOS `FestivalView` + `FestivalListView`). ViewModel을 잇고 예매처 · 시스템 뒤로 · 포그라운드를 처리한다.
 * 검색 중 시스템 뒤로는 검색 닫기다. 검색이 아니면 뒤로를 받지 않아 메인 탭 루트 동작(앱 종료)을 그대로 둔다.
 */
@Composable
fun FestivalListScreen(
    viewModel: FestivalListViewModel,
    onFestivalSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemDefaultZone(),
) {
    val state by viewModel.collectAsState()
    val context = LocalContext.current

    viewModel.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is FestivalListSideEffect.OpenTicket -> openTicket(context, sideEffect.url)
            is FestivalListSideEffect.FestivalSelected -> onFestivalSelected(sideEffect.festivalId)
        }
    }

    LaunchedEffect(Unit) { viewModel.onAppear() }
    BackHandler(enabled = state.isSearching, onBack = viewModel::onSearchClosed)

    FestivalListContent(
        state = state,
        now = clock.instant(),
        modifier = modifier,
        onSearchOpened = viewModel::onSearchOpened,
        onSearchClosed = viewModel::onSearchClosed,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onListFilterChanged = viewModel::onListFilterChanged,
        onRefreshRequested = viewModel::onRefreshRequested,
        onLoadMoreFestivals = viewModel::onLoadMoreFestivals,
        onSelectFestival = viewModel::onSelectFestival,
        onTicketLinkTapped = viewModel::onTicketLinkTapped,
        onDownloadPack = viewModel::onDownloadPack,
        onRecentSearchSubmitted = viewModel::onRecentSearchSubmitted,
        onRecentSearchRemoved = viewModel::onRecentSearchRemoved,
        onRecentSearchesCleared = viewModel::onRecentSearchesCleared,
        onToastExpired = viewModel::onToastExpired,
    )
}

/**
 * 앱이 배경으로 갔다 돌아올 때만 알린다(iOS `scenePhase` 변화). 화면이 처음 붙을 때 받는 시작 알림은 넘긴다.
 * 첫 진입은 [FestivalListViewModel.onAppear]가 맡는다. `ON_STOP`은 iOS `background`라 포그라운드 해제 뒤 [onBackgrounded]도 보낸다.
 * [lifecycleOwner]는 스택 바깥(라이브 흐름) 것을 받는다. 목록 항목 수명 주기는 보드를 쌓으면 내려가 받는 중인 팩을 취소하기 때문이다.
 */
@Composable
internal fun ListForegroundEffect(
    lifecycleOwner: LifecycleOwner,
    onForegroundChanged: (Boolean) -> Unit,
    onBackgrounded: () -> Unit,
) {
    val currentOnForegroundChanged by rememberUpdatedState(onForegroundChanged)
    val currentOnBackgrounded by rememberUpdatedState(onBackgrounded)
    DisposableEffect(lifecycleOwner) {
        var stopped = false
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        stopped = true
                        currentOnForegroundChanged(false)
                        currentOnBackgrounded()
                    }

                    Lifecycle.Event.ON_START -> if (stopped) currentOnForegroundChanged(true)

                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/** 예매처를 Custom Tabs로 연다. 열 수 있는 앱이 없으면 일반 보기 요청으로 한 번 더 연다. */
private fun openTicket(context: Context, url: String) {
    val uri = url.toUri()
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            // 링크를 열 앱이 없다. iOS 앱 안 웹 화면이 없는 동안 할 수 있는 것이 없다.
        }
    }
}

/** 목록 · 검색 화면(순수 UI). [now]는 상태 태그 · D-day · 다가오는 페스티벌의 기준 시각이다. */
@Composable
fun FestivalListContent(
    state: FestivalListState,
    now: Instant,
    modifier: Modifier = Modifier,
    onSearchOpened: () -> Unit = {},
    onSearchClosed: () -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onListFilterChanged: (FestivalListFilter) -> Unit = {},
    onRefreshRequested: () -> Unit = {},
    onLoadMoreFestivals: () -> Unit = {},
    onSelectFestival: (String) -> Unit = {},
    onTicketLinkTapped: (String) -> Unit = {},
    onDownloadPack: (String) -> Unit = {},
    onRecentSearchSubmitted: (String) -> Unit = {},
    onRecentSearchRemoved: (String) -> Unit = {},
    onRecentSearchesCleared: () -> Unit = {},
    onToastExpired: () -> Unit = {},
) {
    val toastItem =
        remember(state.toast, state.toastRevision) {
            when (state.toast) {
                FestivalListToast.TicketLinkMissing -> FCToastItem(R.drawable.ic_circle_info_16, "예매처 정보가 없습니다")
                FestivalListToast.PackDownloadFailed -> FCToastItem(R.drawable.ic_circle_info_16, "팩을 받지 못했어요. 다시 시도해 주세요")
                null -> null
            }
        }
    FCToastHost(
        item = toastItem,
        onItemChange = { if (it == null) onToastExpired() },
        modifier =
            modifier
                .fillMaxSize()
                .background(FestiCoreSemanticColor.backgroundNormal.color)
                .semantics { testTagsAsResourceId = true },
    ) {
        AnimatedContent(
            targetState = state.isSearching,
            transitionSpec = {
                fadeIn(tween(SWITCH_MILLIS, easing = EaseOut)) togetherWith fadeOut(tween(SWITCH_MILLIS, easing = EaseOut))
            },
            label = "festivalListSearch",
        ) { isSearching ->
            if (isSearching) {
                SearchMode(
                    state = state,
                    now = now,
                    onSearchClosed = onSearchClosed,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onRefreshRequested = onRefreshRequested,
                    onSelectFestival = onSelectFestival,
                    onRecentSearchSubmitted = onRecentSearchSubmitted,
                    onRecentSearchRemoved = onRecentSearchRemoved,
                    onRecentSearchesCleared = onRecentSearchesCleared,
                )
            } else {
                ListMode(
                    state = state,
                    now = now,
                    onSearchOpened = onSearchOpened,
                    onListFilterChanged = onListFilterChanged,
                    onRefreshRequested = onRefreshRequested,
                    onLoadMoreFestivals = onLoadMoreFestivals,
                    onSelectFestival = onSelectFestival,
                    onTicketLinkTapped = onTicketLinkTapped,
                    onDownloadPack = onDownloadPack,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListMode(
    state: FestivalListState,
    now: Instant,
    onSearchOpened: () -> Unit,
    onListFilterChanged: (FestivalListFilter) -> Unit,
    onRefreshRequested: () -> Unit,
    onLoadMoreFestivals: () -> Unit,
    onSelectFestival: (String) -> Unit,
    onTicketLinkTapped: (String) -> Unit,
    onDownloadPack: (String) -> Unit,
) {
    val visibleFestivals =
        FestivalListPresentation.sorted(
            FestivalListPresentation.visible(
                state.festivals,
                query = "",
                bookedIds = state.bookedFestivalIds,
                onlyBooked = state.filter == FestivalListFilter.Booked,
            ),
            now,
        )
    val listState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize()) {
        FCTopBar(style = FCTopBarStyle.Large, title = "페스티벌", onTrailingAction = onSearchOpened)
        FCTab(
            selection = state.filter.ordinal,
            onSelectionChange = { onListFilterChanged(FestivalListFilter.entries[it]) },
            items = listOf("전체", "예매 완료"),
            indicatorColor = FestiCoreSemanticColor.staticLime.color,
        )
        state.offlineMessage?.let { FCOfflineBadge(it) }
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefreshRequested,
            // Figma S00 목록 Contents 위 여백 16
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = FestiCoreSpacing.paddingP16),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().filterSwipe { toBooked ->
                    onListFilterChanged(if (toBooked) FestivalListFilter.Booked else FestivalListFilter.All)
                },
                contentPadding = listContentPadding(horizontal = FestiCoreSpacing.paddingP16),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
            ) {
                item(key = "guide") { Guide(state.filter) }
                when {
                    state.showsListSkeleton -> item(key = "skeleton") { FestivalListSkeleton() }

                    visibleFestivals.isEmpty() -> item(key = "empty") { ListEmptyCard(state, onRefreshRequested, onListFilterChanged) }

                    else -> {
                        items(visibleFestivals, key = { it.id }) { festival ->
                            FestivalCard(
                                festival = festival,
                                now = now,
                                imageRevision = state.imageRevision,
                                isBooked = festival.id in state.bookedFestivalIds,
                                download = FestivalListPresentation.packDownloadDisplay(state.packDownloads, festival.id),
                                onSelect = { onSelectFestival(festival.id) },
                                onTicketLink = { onTicketLinkTapped(festival.id) },
                                onDownloadPack = { onDownloadPack(festival.id) },
                            )
                        }
                        val cursor = state.nextCursor
                        if (state.filter == FestivalListFilter.All && cursor != null && !state.catalogRefreshFailed) {
                            item(key = "loadMore-$cursor") { LoadMoreIndicator(cursor, onLoadMoreFestivals) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Guide(filter: FestivalListFilter) {
    Text(
        text = if (filter == FestivalListFilter.All) ALL_GUIDE else BOOKED_GUIDE,
        // Figma 안내 문구 두 줄 44 = 줄 높이 22 × 2
        style = FestiCoreTextStyle.baseSubheadlineMedium.textStyle,
        color = FestiCoreSemanticColor.labelNeutral.color,
    )
}

@Composable
private fun ListEmptyCard(
    state: FestivalListState,
    onRefreshRequested: () -> Unit,
    onListFilterChanged: (FestivalListFilter) -> Unit,
) {
    if (state.festivals.isEmpty()) {
        FCEmptyCard(
            icon = R.drawable.ic_bell,
            title = "불러온 페스티벌이 없어요",
            message = if (state.hasCatalogFailure) "연결을 확인하고 다시 시도해 주세요." else "새 페스티벌이 등록되면 여기에 표시해요.",
            action = if (state.hasCatalogFailure) FCCardAction("다시 시도", onClick = onRefreshRequested) else null,
        )
    } else {
        FCEmptyCard(
            icon = R.drawable.ic_bookmark_20,
            title = "북마크한 페스티벌이 없어요",
            message = "예매한 페스티벌을 북마크하면 여기에 모아 볼 수 있어요.",
            action = FCCardAction("전체 보기") { onListFilterChanged(FestivalListFilter.All) },
        )
    }
}

private val CardShape = RoundedCornerShape(FestiCoreRadius.r16)

@Composable
private fun FestivalCard(
    festival: FestivalOverview,
    now: Instant,
    imageRevision: Int,
    isBooked: Boolean,
    download: FestivalListPresentation.PackDownloadDisplay?,
    onSelect: () -> Unit,
    onTicketLink: () -> Unit,
    onDownloadPack: () -> Unit,
) {
    FCFestivalCard(
        title = festival.name,
        meta = FestivalListPresentation.meta(festival),
        status = cardStatus(festival, now),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .clickable(onClick = onSelect)
                .testTag(FestivalAccessibilityId.card(festival.id)),
        pack = pack(festival, download, onDownloadPack),
        isBooked = isBooked,
        // 북마크는 로그인 · 대기 예약 흐름과 함께 옮긴다(설계 D3). 그 전에는 버튼을 숨긴다.
        onBookmark = null,
        onTicketLink = onTicketLink,
        media = { FestivalPoster(url = festival.thumbnailUrl, imageRevision = imageRevision) },
    )
}

private fun cardStatus(festival: FestivalOverview, now: Instant): FCFestivalCardStatus =
    when (val status = FestivalListPresentation.cardStatus(festival, now)) {
        is FestivalListPresentation.CardStatus.DDay -> FCFestivalCardStatus.DDay(status.label)
        is FestivalListPresentation.CardStatus.Closing -> FCFestivalCardStatus.Closing(status.label)
        FestivalListPresentation.CardStatus.Upcoming -> FCFestivalCardStatus.Upcoming()
        FestivalListPresentation.CardStatus.Ended -> FCFestivalCardStatus.Ended()
    }

/** 받는 중이면 진행 표시, 아니면 팩 문구(iOS `FestivalListView.pack(for:)`). */
private fun pack(
    festival: FestivalOverview,
    download: FestivalListPresentation.PackDownloadDisplay?,
    onDownloadPack: () -> Unit,
): FCFestivalCardPack {
    if (download != null) return FCFestivalCardPack.Downloading(progress = download.progress, text = download.text)
    return when (val copy = FestivalListPresentation.packCopy(festival)) {
        is FestivalListPresentation.PackCopy.Unavailable -> FCFestivalCardPack.Unavailable(copy.text)
        is FestivalListPresentation.PackCopy.Ready -> FCFestivalCardPack.Ready(title = copy.title, onClick = onDownloadPack)
        is FestivalListPresentation.PackCopy.Received -> FCFestivalCardPack.Received(copy.text)
    }
}

private val LoadMoreSize = 20.dp
private val LoadMoreStroke = 2.dp

/** 다음 쪽 인디케이터(iOS `ProgressView().id(cursor).onAppear`). 보이면 커서마다 한 번 다음 쪽을 부른다. */
@Composable
private fun LoadMoreIndicator(cursor: String, onLoadMoreFestivals: () -> Unit) {
    LaunchedEffect(cursor) { onLoadMoreFestivals() }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(LoadMoreSize),
            color = FestiCoreSemanticColor.labelAssistive.color,
            strokeWidth = LoadMoreStroke,
        )
    }
}

@Composable
private fun SearchMode(
    state: FestivalListState,
    now: Instant,
    onSearchClosed: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onRefreshRequested: () -> Unit,
    onSelectFestival: (String) -> Unit,
    onRecentSearchSubmitted: (String) -> Unit,
    onRecentSearchRemoved: (String) -> Unit,
    onRecentSearchesCleared: () -> Unit,
) {
    val festivals = state.festivals
    val query = state.trimmedQuery
    val supportsArtistSearch = festivals.any { it.artists != null }
    val saveRecentSearch = { if (query.isNotEmpty()) onRecentSearchSubmitted(query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // iOS `scrollDismissesKeyboard(.immediately)`: 손가락으로 스크롤을 시작하면 키보드를 닫는다.
    val dismissKeyboardOnScroll =
        remember(keyboard, focusManager) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && available.y != 0f) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }
                    return Offset.Zero
                }
            }
        }
    Column(modifier = Modifier.fillMaxSize()) {
        FCTopBar(
            style = FCTopBarStyle.Search(text = state.query, onTextChange = onSearchQueryChanged),
            searchPlaceholder = if (supportsArtistSearch) "페스티벌, 아티스트, 장소 검색" else "페스티벌, 장소 검색",
            onCancel = onSearchClosed,
            onSearchSubmit = saveRecentSearch,
        )
        state.offlineMessage?.let { FCOfflineBadge(it) }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(dismissKeyboardOnScroll),
            contentPadding = listContentPadding(horizontal = 0.dp),
        ) {
            when {
                query.isEmpty() -> recentAndUpcoming(
                    state,
                    now,
                    onSearchQueryChanged,
                    onRecentSearchRemoved,
                    onRecentSearchesCleared,
                    onSelectFestival,
                )

                state.viewState == FestivalListViewState.Loading && festivals.isEmpty() -> item(key = "searchSkeleton") {
                    FestivalSearchSkeleton()
                }

                festivals.isEmpty() && state.hasCatalogFailure ->
                    item(key = "searchFailure") {
                        FCEmptyCard(
                            icon = R.drawable.ic_bell,
                            title = "페스티벌을 불러오지 못했어요",
                            message = "연결을 확인하고 다시 시도해 주세요.",
                            modifier = Modifier.padding(FestiCoreSpacing.paddingP16),
                            action = FCCardAction("다시 시도", onClick = onRefreshRequested),
                        )
                    }

                else -> searchResults(state, now, query, supportsArtistSearch, saveRecentSearch, onSelectFestival)
            }
        }
    }
}

private fun LazyListScope.recentAndUpcoming(
    state: FestivalListState,
    now: Instant,
    onSearchQueryChanged: (String) -> Unit,
    onRecentSearchRemoved: (String) -> Unit,
    onRecentSearchesCleared: () -> Unit,
    onSelectFestival: (String) -> Unit,
) {
    if (state.recentSearches.isNotEmpty()) {
        item(key = "recentHeader") { SearchSectionHeader("최근 검색", actionTitle = "전체 삭제", onAction = onRecentSearchesCleared) }
        item(key = "recentKeywords") {
            FCFlowLayout(modifier = Modifier.padding(horizontal = FestiCoreSpacing.paddingP16), spacing = FestiCoreSpacing.paddingP8) {
                state.recentSearches.forEach { keyword ->
                    FCRecentKeyword(
                        keyword = keyword,
                        onSelect = { onSearchQueryChanged(keyword) },
                        onDelete = { onRecentSearchRemoved(keyword) },
                    )
                }
            }
        }
    }
    // Figma S00 검색 - 최근 검색어: 검색어 입력 전 아래에 다가오는 페스티벌을 보여 준다.
    val upcoming = FestivalListPresentation.upcoming(state.festivals, now)
    if (upcoming.isNotEmpty()) {
        item(key = "upcomingHeader") { SearchSectionHeader("다가오는 페스티벌") }
        festivalRows(upcoming, now, query = "", imageRevision = state.imageRevision, keyPrefix = "upcoming") { onSelectFestival(it) }
    }
}

private fun LazyListScope.searchResults(
    state: FestivalListState,
    now: Instant,
    query: String,
    supportsArtistSearch: Boolean,
    saveRecentSearch: () -> Unit,
    onSelectFestival: (String) -> Unit,
) {
    val festivalResults =
        FestivalListPresentation.sorted(
            FestivalListPresentation.visible(state.festivals, query, state.bookedFestivalIds, onlyBooked = false),
            now,
        )
    val artistResults = FestivalListPresentation.matchingArtists(FestivalListPresentation.sorted(state.festivals, now), query)
    if (festivalResults.isEmpty() && artistResults.isEmpty()) {
        item(key = "searchEmpty") {
            FCSearchEmpty(
                query = query,
                message = if (supportsArtistSearch) "페스티벌, 아티스트 이름이나 장소로 다시 찾아보세요" else "페스티벌 이름이나 장소로 다시 찾아보세요",
            )
        }
        return
    }
    if (festivalResults.isNotEmpty()) {
        item(key = "festivalHeader") { SearchSectionHeader("페스티벌 ${festivalResults.size}") }
        festivalRows(festivalResults, now, query, state.imageRevision, keyPrefix = "festival") {
            saveRecentSearch()
            onSelectFestival(it)
        }
    }
    if (artistResults.isNotEmpty()) {
        item(key = "artistHeader") { SearchSectionHeader("아티스트 ${artistResults.size}") }
        items(artistResults, key = { "artist-${it.id}" }) { result ->
            FCSearchResultRow(
                kind = FCSearchResultRowKind.Artist(initial = result.artist.name.take(1)),
                title = result.artist.name,
                meta = FestivalListPresentation.artistMeta(result, now),
                highlightedQuery = query,
                onClick = {
                    saveRecentSearch()
                    onSelectFestival(result.festival.id)
                },
            )
        }
    }
}

private fun LazyListScope.festivalRows(
    festivals: List<FestivalOverview>,
    now: Instant,
    query: String,
    imageRevision: Int,
    keyPrefix: String,
    onSelect: (String) -> Unit,
) {
    items(festivals, key = { "$keyPrefix-${it.id}" }) { festival ->
        FCSearchResultRow(
            kind = FCSearchResultRowKind.Festival(image = null, dDay = FestivalListPresentation.searchBadge(festival, now)),
            title = festival.name,
            meta = FestivalListPresentation.meta(festival),
            highlightedQuery = query,
            badgeStyle = badgeStyle(festival, now),
            thumbnail = { FestivalPoster(url = festival.thumbnailUrl, imageRevision = imageRevision) },
            onClick = { onSelect(festival.id) },
        )
    }
}

private fun badgeStyle(festival: FestivalOverview, now: Instant): FCSearchResultRowBadgeStyle =
    when (FestivalListPresentation.cardStatus(festival, now)) {
        is FestivalListPresentation.CardStatus.Closing -> FCSearchResultRowBadgeStyle.Closing
        FestivalListPresentation.CardStatus.Upcoming -> FCSearchResultRowBadgeStyle.Upcoming
        is FestivalListPresentation.CardStatus.DDay, FestivalListPresentation.CardStatus.Ended -> FCSearchResultRowBadgeStyle.Day
    }

@Composable
private fun SearchSectionHeader(
    title: String,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    FCSectionHeader(
        title = title,
        modifier =
            Modifier.padding(
                start = FestiCoreSpacing.paddingP16,
                end = FestiCoreSpacing.paddingP16,
                top = FestiCoreSpacing.paddingP20,
                bottom = FestiCoreSpacing.paddingP8,
            ),
        actionTitle = actionTitle,
        isCompact = true,
        onAction = onAction,
    )
}

/** 아래 여백 20(iOS `fcScrollBottomInset`). 라이브 탭 루트는 탭 바가 없어 내비게이션 바 높이를 더한다. */
@Composable
private fun listContentPadding(horizontal: Dp): PaddingValues {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = horizontal, end = horizontal, bottom = FestiCoreSpacing.paddingP20 + navigationBar)
}

private val SwipeMinimumDistance = 30.dp
private val SwipeThreshold = 80.dp
private const val SWIPE_RATIO = 1.5f

/**
 * 목록을 좌우로 밀면 탭을 바꾼다(iOS `DragGesture(minimumDistance: 30)` + `simultaneousGesture`).
 * 세로 스크롤과 함께 받도록 이벤트를 소비하지 않고 손을 뗀 위치만 본다. 왼쪽으로 밀면 예매 완료, 오른쪽이면 전체.
 */
private fun Modifier.filterSwipe(onSwipe: (toBooked: Boolean) -> Unit): Modifier = pointerInput(Unit) {
    val minimumDistance = SwipeMinimumDistance.toPx()
    val threshold = SwipeThreshold.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var last = down.position
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            last = change.position
            if (!change.pressed) break
        }
        val horizontal = last.x - down.position.x
        val vertical = last.y - down.position.y
        if (hypot(horizontal, vertical) >= minimumDistance && abs(horizontal) > threshold &&
            abs(horizontal) > abs(vertical) * SWIPE_RATIO
        ) {
            onSwipe(horizontal < 0)
        }
    }
}

private const val SWITCH_MILLIS = 200
private const val ALL_GUIDE = "가고 싶은 페스티벌을 찾아보아요.\n이미 예매한 페스티벌은 북마크 해서 따로 모아 볼 수 있어요."
private const val BOOKED_GUIDE = "북마크한 페스티벌만 모아 봤어요.\n팩을 미리 받아 두면 현장에서 통신이 약해도 볼 수 있어요."
