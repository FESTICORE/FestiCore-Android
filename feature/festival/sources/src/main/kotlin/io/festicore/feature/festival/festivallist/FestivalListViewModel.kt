package io.festicore.feature.festival.festivallist

import androidx.lifecycle.ViewModel
import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.domain.festival.catalog.FestivalCatalogUseCase
import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalCatalogPage
import io.festicore.domain.festival.entity.PackDownloadEvent
import io.festicore.domain.festival.entity.PackDownloadProgress
import io.festicore.domain.festival.entity.PackSyncResult
import io.festicore.domain.festival.library.FestivalLibraryUseCase
import io.festicore.domain.festival.pack.FestivalPackUseCase
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.blockingIntent
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S00 페스티벌 목록. iOS `FestivalFeature`의 목록 부분만 옮긴다(라이브 보드 · 북마크는 이후 단계).
 * 함수 이름은 iOS `Action.View`와 같고, 순서는 view → async → inner를 따른다.
 * 카탈로그 요청(첫 쪽 · 다음 쪽 · 전체)은 한 번에 하나만 돈다. 새 요청은 진행 중인 것을 취소한다(iOS `CancelID.catalog` + `cancelInFlight`).
 * 팩 받기는 축제마다 하나만 돈다(iOS `PackDownloadID`). 받은 팩은 팩 저장소의 공유 상태를 따라 목록에 합친다(설계 D1).
 */
class FestivalListViewModel(
    private val catalogUseCase: FestivalCatalogUseCase,
    private val libraryUseCase: FestivalLibraryUseCase,
    private val currentUserProvider: CurrentUserProvider,
    private val packUseCase: FestivalPackUseCase,
) : ViewModel(),
    OrbitContainerHost<FestivalListState, FestivalListState, FestivalListSideEffect> {
    override val container =
        orbitContainer<FestivalListState, FestivalListSideEffect>(FestivalListState()) {
            coroutineScope {
                // 보드 · 다른 화면이 받거나 지운 팩도 목록에 바로 보인다(설계 D1).
                launch {
                    packUseCase.state.map { it.packs }.distinctUntilChanged().collect { packs ->
                        reduce { state.copy(cachedPacks = packs) }
                    }
                }
                // iOS `MainTabCoordinator`가 사용자가 바뀔 때 보내는 `reloadBookmarks(userID)` · `sessionChanged`.
                currentUserProvider.currentUserId.collect { onUserChanged(it) }
            }
        }

    private val catalogJob = AtomicReference<Job?>(null)
    private val packDownloadJobs = ConcurrentHashMap<String, Job>()

    // MARK: - View

    fun onAppear() = intent { start() }

    fun onForegroundChanged(isForeground: Boolean) = intent {
        reduce { state.copy(isForeground = isForeground) }
        if (!state.hasStarted) {
            if (isForeground) start()
            return@intent
        }
        // iOS `resumeForeground`의 목록 부분.
        if (isForeground) refreshCatalog()
    }

    /** 앱이 배경으로 갔다(iOS `backgrounded`). 받는 중인 팩을 모두 취소한다. 포그라운드를 잃기만 해서는 취소하지 않는다. */
    fun onBackgrounded() = intent {
        cancelPackDownloads()
        reduce { state.copy(packDownloads = emptyMap()) }
    }

    fun onRefreshRequested() = intent {
        reduce { state.copy(viewState = FestivalListViewState.Loading, nextCursor = null) }
        refreshCatalog()
    }

    fun onLoadMoreFestivals() = intent {
        val cursor = state.nextCursor ?: return@intent
        if (state.isLoadingMore || state.isSearching || state.filter != FestivalListFilter.All) return@intent
        reduce { state.copy(isLoadingMore = true) }
        loadCatalogPage(after = cursor)
    }

    /** 라이브 탭을 골랐다(iOS `MainTabCoordinator` `.live` → `reloadCachedPacks`). 첫 진입은 [onAppear]가 읽는다. */
    fun onReloadCachedPacks() = intent {
        if (!state.hasStarted) return@intent
        reloadCachedPacks()
    }

    fun onSelectFestival(festivalId: String) = intent {
        if (state.festivals.none { it.id == festivalId }) return@intent
        postSideEffect(FestivalListSideEffect.FestivalSelected(festivalId))
    }

    fun onTicketLinkTapped(festivalId: String) = intent {
        val url = state.festivals.firstOrNull { it.id == festivalId }?.ticketUrl
        if (url != null) {
            postSideEffect(FestivalListSideEffect.OpenTicket(url))
            return@intent
        }
        reduce { state.copy(toast = FestivalListToast.TicketLinkMissing, toastRevision = state.toastRevision + 1) }
    }

    fun onRecentSearchSubmitted(query: String) = intent {
        val userId = state.userId
        val searches = libraryUseCase.addRecentSearch(query, userId)
        if (state.userId == userId) reduce { state.copy(recentSearches = searches) }
    }

    fun onRecentSearchRemoved(query: String) = intent {
        val userId = state.userId
        val searches = libraryUseCase.removeRecentSearch(query, userId)
        if (state.userId == userId) reduce { state.copy(recentSearches = searches) }
    }

    fun onRecentSearchesCleared() = intent {
        val userId = state.userId
        libraryUseCase.clearRecentSearches(userId)
        if (state.userId == userId) reduce { state.copy(recentSearches = emptyList()) }
    }

    fun onSearchOpened() = intent { reduce { state.copy(isSearching = true) } }

    fun onSearchClosed() = intent { reduce { state.copy(isSearching = false, query = "") } }

    /** 입력 칸 값이라 바로 반영한다(Orbit `blockingIntent`). 비동기로 늦게 반영하면 빠른 입력에서 글자가 빠진다. */
    fun onSearchQueryChanged(query: String) = blockingIntent {
        val wasEmpty = state.trimmedQuery.isEmpty()
        reduce { state.copy(query = query) }
        if (wasEmpty && state.trimmedQuery.isNotEmpty() && state.nextCursor != null) refreshFullCatalog()
    }

    fun onListFilterChanged(filter: FestivalListFilter) = intent {
        reduce { state.copy(filter = filter) }
        if (filter == FestivalListFilter.Booked && state.nextCursor != null) refreshFullCatalog()
    }

    /** 카드 "팩 받기" · "팩 업데이트"(iOS `downloadPack`). 발행됐고 받은 버전이 더 낮을 때만 받는다. */
    fun onDownloadPack(festivalId: String) = intent {
        if (!state.isForeground || festivalId in state.packDownloads) return@intent
        val festival = state.festivals.firstOrNull { it.id == festivalId } ?: return@intent
        if (festival.packVersion <= 0 || (festival.cachedPackVersion ?: -1) >= festival.packVersion) return@intent
        reduce { state.copy(packDownloads = state.packDownloads + (festivalId to null)) }
        downloadPack(festivalId, state.accountGeneration)
    }

    /** 토스트가 2초 뒤 사라졌다(iOS `toastExpired`). 시간은 `FCToastHost`가 잰다. */
    fun onToastExpired() = intent { reduce { state.copy(toast = null) } }

    // MARK: - Async

    /** iOS `onAppear`: 최근 검색을 읽고, 처음 한 번만 저장된 목록을 읽는다. */
    private suspend fun start() = subIntent {
        val userId = state.userId
        val searches = libraryUseCase.recentSearches(userId)
        val starting = !state.hasStarted
        reduce { state.copy(recentSearches = if (state.userId == userId) searches else state.recentSearches, hasStarted = true) }
        if (starting) loadCachedFestivals()
    }

    private suspend fun loadCachedFestivals() = subIntent {
        val packs = attempt { packUseCase.reloadCachedPacks() }
        val cached = attempt { catalogUseCase.cachedFestivals() }
        onCachedLoaded(cached.orEmpty(), packs, readFailed = cached == null)
    }

    /** 저장된 팩을 다시 읽는다. 읽지 못하면 보이던 팩을 그대로 둔다(iOS `try?`). */
    private suspend fun reloadCachedPacks() = subIntent {
        val packs = attempt { packUseCase.reloadCachedPacks() } ?: return@subIntent
        reduce { state.copy(cachedPacks = packs.associateBy { it.festivalId }) }
    }

    /** 축제마다 하나만 받는다. 같은 축제를 다시 받으면 이전 것을 취소한다(iOS `cancelInFlight`). */
    private fun downloadPack(festivalId: String, generation: Int) {
        val job =
            intent {
                try {
                    packUseCase.downloadPack(festivalId).collect { event ->
                        when (event) {
                            PackDownloadEvent.Reset -> onPackDownloadProgress(festivalId, generation, null)
                            is PackDownloadEvent.Progress -> onPackDownloadProgress(festivalId, generation, event.progress)
                            is PackDownloadEvent.Finished -> onPackDownloadFinished(festivalId, generation, event.result)
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    onPackDownloadFailed(festivalId, generation)
                }
            }
        packDownloadJobs.put(festivalId, job)?.cancel()
        job.invokeOnCompletion { packDownloadJobs.remove(festivalId, job) }
    }

    /** 첫 쪽(limit 10). 다음 쪽이 없으면 갱신 완료, 있으면 첫 쪽으로 새로 시작한다. */
    private fun refreshCatalog() = launchCatalog {
        val page = attempt { catalogUseCase.festivalPage(after = null) }
        when {
            page == null -> onCatalogFailed()
            page.nextCursor == null -> onCatalogRefreshed(page.items)
            else -> onCatalogPageLoaded(page, after = null)
        }
    }

    private fun loadCatalogPage(after: String) = launchCatalog {
        val page = attempt { catalogUseCase.festivalPage(after = after) }
        if (page == null) onCatalogFailed() else onCatalogPageLoaded(page, after = after)
    }

    /** 검색 · 예매 완료 탭은 전체 목록이 필요하다. */
    private fun refreshFullCatalog() = launchCatalog {
        val items = attempt { catalogUseCase.refreshFestivals() }
        if (items == null) onCatalogFailed() else onCatalogRefreshed(items)
    }

    // MARK: - Inner

    private suspend fun onCachedLoaded(
        items: List<FestivalCatalogItem>,
        packs: List<CachedFestivalPack>?,
        readFailed: Boolean,
    ) = subIntent {
        reduce {
            val loaded =
                state.copy(
                    catalog = uniqueCatalog(items),
                    cachedPacks = packs?.associateBy { it.festivalId } ?: state.cachedPacks,
                    catalogCacheReadFailed = readFailed,
                )
            if (loaded.festivals.isNotEmpty()) loaded.copy(viewState = FestivalListViewState.Loaded) else loaded
        }
        refreshCatalog()
    }

    /** 진행 이벤트(Reset이면 [progress] null). 사용자가 바뀌었거나 이미 끝난 받기면 버린다. */
    private suspend fun onPackDownloadProgress(
        festivalId: String,
        generation: Int,
        progress: PackDownloadProgress?,
    ) = subIntent {
        if (generation != state.accountGeneration || festivalId !in state.packDownloads) return@subIntent
        reduce { state.copy(packDownloads = state.packDownloads + (festivalId to progress)) }
    }

    /** 저장까지 끝났으면 팩을 합치고, 재시도 대기 · 재접속으로 끝났으면 실패 토스트를 보인다. */
    private suspend fun onPackDownloadFinished(
        festivalId: String,
        generation: Int,
        result: PackSyncResult,
    ) = subIntent {
        if (generation != state.accountGeneration || festivalId !in state.packDownloads) return@subIntent
        reduce {
            val finished = state.copy(packDownloads = state.packDownloads - festivalId)
            when (result) {
                is PackSyncResult.Updated -> finished.copy(cachedPacks = finished.cachedPacks + (festivalId to result.pack))
                is PackSyncResult.NotModified -> finished.copy(cachedPacks = finished.cachedPacks + (festivalId to result.pack))
                is PackSyncResult.RetryAfter, PackSyncResult.Reconnecting -> finished.withPackFailureToast()
            }
        }
    }

    private suspend fun onPackDownloadFailed(festivalId: String, generation: Int) = subIntent {
        if (generation != state.accountGeneration || festivalId !in state.packDownloads) return@subIntent
        reduce { state.copy(packDownloads = state.packDownloads - festivalId).withPackFailureToast() }
    }

    private suspend fun onCatalogRefreshed(items: List<FestivalCatalogItem>) = subIntent {
        reduce { refreshed(state, items, nextCursor = null) }
    }

    /**
     * 첫 쪽(`after == null`)은 저장 목록을 버리고 그 쪽으로 새로 시작하고, 다음 쪽부터 뒤에 이어 붙인다.
     * 현재 커서와 다른 쪽은 버린다. 다음 쪽이 남았는데 검색어가 있거나 예매 완료 탭이면 전체를 받는다.
     */
    private suspend fun onCatalogPageLoaded(page: FestivalCatalogPage, after: String?) = subIntent {
        if (after != null && after != state.nextCursor) return@subIntent
        val items = if (after == null) page.items else state.catalog + page.items
        reduce { refreshed(state, items, nextCursor = page.nextCursor) }
        if (page.nextCursor != null && (state.trimmedQuery.isNotEmpty() || state.filter == FestivalListFilter.Booked)) {
            refreshFullCatalog()
        }
    }

    private suspend fun onCatalogFailed() = subIntent {
        reduce {
            state.copy(
                viewState = FestivalListViewState.Loaded,
                hasFinishedFirstCatalogRefresh = true,
                catalogRefreshFailed = true,
                isLoadingMore = false,
                imageRetryPending = true,
            )
        }
    }

    /**
     * iOS `reloadBookmarks(userID)` · `sessionChanged`: 사용자가 바뀌면 검색 · 탭을 처음으로 돌리고 그 사용자의 최근 검색을 읽는다.
     * 받는 중인 팩은 취소하고 세대를 올려 늦게 온 이벤트를 버린다.
     */
    private suspend fun onUserChanged(userId: String?) = subIntent {
        if (state.userId != userId) {
            cancelPackDownloads()
            reduce {
                state.copy(
                    userId = userId,
                    filter = FestivalListFilter.All,
                    isSearching = false,
                    query = "",
                    recentSearches = emptyList(),
                    packDownloads = emptyMap(),
                    accountGeneration = state.accountGeneration + 1,
                )
            }
        }
        val searches = libraryUseCase.recentSearches(userId)
        if (state.userId == userId) reduce { state.copy(recentSearches = searches) }
    }

    private fun refreshed(
        state: FestivalListState,
        items: List<FestivalCatalogItem>,
        nextCursor: String?,
    ): FestivalListState = state.copy(
        viewState = FestivalListViewState.Loaded,
        hasFinishedFirstCatalogRefresh = true,
        catalogRefreshFailed = false,
        catalogCacheReadFailed = false,
        nextCursor = nextCursor,
        isLoadingMore = false,
        catalog = uniqueCatalog(items),
        imageRetryPending = false,
        imageRevision = if (state.imageRetryPending) state.imageRevision + 1 else state.imageRevision,
    )

    private fun cancelPackDownloads() {
        packDownloadJobs.values.forEach(Job::cancel)
        packDownloadJobs.clear()
    }

    private fun FestivalListState.withPackFailureToast() =
        copy(toast = FestivalListToast.PackDownloadFailed, toastRevision = toastRevision + 1)

    private fun launchCatalog(block: suspend () -> Unit) {
        val job = intent { block() }
        catalogJob.getAndSet(job)?.cancel()
    }

    private companion object {
        fun uniqueCatalog(items: List<FestivalCatalogItem>): List<FestivalCatalogItem> = items.distinctBy { it.id }

        /** 카탈로그 오류는 종류를 가리지 않고 실패로 본다(iOS `catalogFailed`). 취소는 그대로 올린다. */
        suspend fun <T> attempt(block: suspend () -> T): T? = try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }
}
