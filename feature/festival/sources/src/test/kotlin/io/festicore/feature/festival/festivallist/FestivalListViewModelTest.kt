package io.festicore.feature.festival.festivallist

import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.domain.festival.catalog.FestivalCatalogUseCase
import io.festicore.domain.festival.entity.FestivalCatalogError
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalCatalogPage
import io.festicore.domain.festival.entity.FestivalTicketLink
import io.festicore.domain.festival.library.FestivalLibraryUseCase
import io.festicore.feature.festival.RecordingAnalyticsUseCase
import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.AnalyticsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `FestivalCatalogPaginationTests` · `FestivalOfflineTests` · `FestivalRecentSearchFeatureTests` · `FestivalTicketLinkToastTests`의 목록 사례. */
class FestivalListViewModelTest {
    private val analytics = RecordingAnalyticsUseCase()

    private val first = FestivalCatalogItem(id = "fst_first", name = "첫 축제", packVersion = 0)
    private val second = FestivalCatalogItem(id = "fst_second", name = "둘째 축제", packVersion = 0)
    private val booked = FestivalCatalogItem(id = "fst_booked", name = "예매 축제", packVersion = 0)
    private val cached = FestivalCatalogItem(id = "fst_cached", name = "저장 축제", packVersion = 1)

    private val loaded =
        FestivalListState(
            viewState = FestivalListViewState.Loaded,
            hasFinishedFirstCatalogRefresh = true,
            hasStarted = true,
        )

    private fun viewModel(
        catalog: FakeCatalogUseCase = FakeCatalogUseCase(),
        library: FakeLibraryUseCase = FakeLibraryUseCase(),
        currentUser: FakeCurrentUserProvider = FakeCurrentUserProvider(),
    ) = FestivalListViewModel(catalog, library, currentUser, FakePackUseCase(), analytics)

    @Test
    fun listSkeletonShowsUntilFirstCatalogRefresh() = runTest {
        val catalog = FakeCatalogUseCase(cached = { listOf(cached) }, page = { _, _ -> throw FestivalCatalogError.Offline() })
        viewModel(catalog).test(this) {
            containerHost.onAppear()

            expectState { copy(hasStarted = true) }
            expectState { copy(catalog = listOf(cached), viewState = FestivalListViewState.Loaded) }
            expectState { copy(hasFinishedFirstCatalogRefresh = true, catalogRefreshFailed = true, imageRetryPending = true) }
        }
        assertEquals(listOf(AnalyticsEvent.ScreenView(AnalyticsScreen.FestivalList)), analytics.events)
    }

    @Test
    fun `첫 쪽은 저장 목록을 버리고 서버 쪽으로 새로 시작한다`() = runTest {
        val catalog =
            FakeCatalogUseCase(cached = { listOf(cached) }, page = { _, _ -> FestivalCatalogPage(listOf(first), nextCursor = "cursor-a") })
        viewModel(catalog).test(this) {
            containerHost.onAppear()

            expectState { copy(hasStarted = true) }
            expectState { copy(catalog = listOf(cached), viewState = FestivalListViewState.Loaded) }
            expectState { copy(catalog = listOf(first), nextCursor = "cursor-a", hasFinishedFirstCatalogRefresh = true) }
        }
        assertEquals(listOf<String?>(null), catalog.requestedCursors)
    }

    @Test
    fun appendsPagesAndKeepsFirstCopyOfDuplicateFestival() = runTest {
        val duplicate = first.copy(name = "중복 축제")
        val catalog = FakeCatalogUseCase(page = { _, _ -> FestivalCatalogPage(listOf(duplicate, second), nextCursor = null) })
        viewModel(catalog).test(this, initialState = loaded.copy(catalog = listOf(first), nextCursor = "cursor-a")) {
            containerHost.onLoadMoreFestivals()

            expectState { copy(isLoadingMore = true) }
            expectState { copy(catalog = listOf(first, second), nextCursor = null, isLoadingMore = false) }
        }
        assertEquals(listOf<String?>("cursor-a"), catalog.requestedCursors)
    }

    @Test
    fun `검색 중이거나 예매 완료 탭이면 다음 쪽을 부르지 않는다`() = runTest {
        val catalog = FakeCatalogUseCase()
        viewModel(catalog).test(this, initialState = loaded.copy(catalog = listOf(first), nextCursor = "cursor-a", isSearching = true)) {
            containerHost.onLoadMoreFestivals()
        }
        viewModel(
            catalog,
        ).test(this, initialState = loaded.copy(catalog = listOf(first), nextCursor = "cursor-a", filter = FestivalListFilter.Booked)) {
            containerHost.onLoadMoreFestivals()
        }
        assertTrue(catalog.requestedCursors.isEmpty())
    }

    @Test
    fun bookedFilterBeforeFirstPageFetchesTheCompleteCatalog() = runTest {
        val catalog =
            FakeCatalogUseCase(
                page = { _, _ -> FestivalCatalogPage(listOf(first), nextCursor = "cursor-a") },
                refresh = { listOf(first, booked) },
            )
        viewModel(catalog).test(this) {
            containerHost.onListFilterChanged(FestivalListFilter.Booked)
            expectState { copy(filter = FestivalListFilter.Booked) }

            containerHost.onAppear()
            expectState { copy(hasStarted = true) }
            expectState {
                copy(
                    catalog = listOf(first),
                    nextCursor = "cursor-a",
                    viewState = FestivalListViewState.Loaded,
                    hasFinishedFirstCatalogRefresh = true,
                )
            }
            expectState { copy(catalog = listOf(first, booked), nextCursor = null) }
        }
        assertEquals(1, catalog.refreshCount)
    }

    @Test
    fun `검색어 첫 글자를 넣으면 남은 쪽까지 전체를 받는다`() = runTest {
        val catalog = FakeCatalogUseCase(refresh = { listOf(first, second) })
        viewModel(catalog).test(this, initialState = loaded.copy(catalog = listOf(first), nextCursor = "cursor-a", isSearching = true)) {
            containerHost.onSearchQueryChanged("둘")
            expectState { copy(query = "둘") }
            expectState { copy(catalog = listOf(first, second), nextCursor = null) }

            containerHost.onSearchQueryChanged("둘째")
            expectState { copy(query = "둘째") }
        }
        assertEquals(1, catalog.refreshCount)
    }

    @Test
    fun `저장 목록 없이 갱신이 실패하면 다시 시도 배지를 보인다`() = runTest {
        val catalog = FakeCatalogUseCase(page = { _, _ -> throw FestivalCatalogError.Offline() })
        viewModel(catalog).test(this) {
            containerHost.onAppear()

            expectState { copy(hasStarted = true) }
            expectState {
                copy(
                    viewState = FestivalListViewState.Loaded,
                    hasFinishedFirstCatalogRefresh = true,
                    catalogRefreshFailed = true,
                    imageRetryPending = true,
                )
            }
        }
    }

    @Test
    fun `저장 목록을 읽지 못하면 읽기 실패 배지를 보이고 서버 성공 시 지운다`() = runTest {
        val catalog =
            FakeCatalogUseCase(cached = {
                throw FestivalCatalogError.CacheUnreadable()
            }, page = { _, _ -> FestivalCatalogPage(listOf(first), null) })
        viewModel(catalog).test(this) {
            containerHost.onAppear()

            expectState { copy(hasStarted = true) }
            expectState { copy(catalogCacheReadFailed = true) }
            expectState {
                copy(
                    catalogCacheReadFailed = false,
                    catalog = listOf(first),
                    viewState = FestivalListViewState.Loaded,
                    hasFinishedFirstCatalogRefresh = true,
                )
            }
        }
    }

    @Test
    fun `갱신 실패 뒤 복구되면 포스터를 다시 받는다`() = runTest {
        val catalog = FakeCatalogUseCase(page = { _, _ -> FestivalCatalogPage(listOf(first), null) })
        viewModel(
            catalog,
        ).test(this, initialState = loaded.copy(catalog = listOf(first), catalogRefreshFailed = true, imageRetryPending = true)) {
            containerHost.onRefreshRequested()

            expectState { copy(viewState = FestivalListViewState.Loading) }
            expectState {
                copy(viewState = FestivalListViewState.Loaded, catalogRefreshFailed = false, imageRetryPending = false, imageRevision = 1)
            }
        }
    }

    @Test
    fun switchingAccountsLoadsOnlyThatAccountsSearches() = runTest {
        val currentUser = FakeCurrentUserProvider()
        val library = FakeLibraryUseCase(mutableMapOf(null to listOf("익명"), "u1" to listOf("펜타"), "u2" to listOf("원더")))
        viewModel(
            library = library,
            currentUser = currentUser,
        ).test(this, initialState = FestivalListState(isSearching = true, query = "x")) {
            val subscription = runOnCreate()
            expectState { copy(recentSearches = listOf("익명")) }

            currentUser.currentUserId.value = "u1"
            // 사용자가 바뀌면 검색 · 탭을 처음으로 돌리고 이전 사용자의 최근 검색을 남기지 않는다(iOS `accountChangeResetsSearchAndSelectedFilter`).
            expectState { copy(userId = "u1", isSearching = false, query = "", recentSearches = emptyList(), accountGeneration = 1) }
            expectState { copy(recentSearches = listOf("펜타")) }

            currentUser.currentUserId.value = "u2"
            expectState { copy(userId = "u2", recentSearches = emptyList(), accountGeneration = 2) }
            expectState { copy(recentSearches = listOf("원더")) }
            subscription.cancel()
        }
    }

    @Test
    fun searchActionsUpdateVisibleKeywordsThroughDependency() = runTest {
        val library = FakeLibraryUseCase(mutableMapOf("u1" to listOf("원더")))
        viewModel(library = library).test(this, initialState = loaded.copy(userId = "u1", recentSearches = listOf("원더"))) {
            containerHost.onRecentSearchSubmitted("펜타")
            expectState { copy(recentSearches = listOf("펜타", "원더")) }

            containerHost.onRecentSearchRemoved("원더")
            expectState { copy(recentSearches = listOf("펜타")) }

            containerHost.onRecentSearchesCleared()
            expectState { copy(recentSearches = emptyList()) }
        }
        assertEquals(emptyList<String>(), library.searches["u1"])
    }

    @Test
    fun searchStartsWithOnlyRecentKeywordsAndClosesWithoutChangingFilter() = runTest {
        viewModel().test(this, initialState = loaded.copy(filter = FestivalListFilter.Booked)) {
            containerHost.onSearchOpened()
            expectState { copy(isSearching = true) }
            containerHost.onSearchQueryChanged("펜타")
            expectState { copy(query = "펜타") }
            containerHost.onSearchClosed()
            expectState { copy(isSearching = false, query = "") }
        }
    }

    @Test
    fun `예매처가 있으면 열고 없으면 토스트를 보인다`() = runTest {
        val withLink = first.copy(ticketLinks = listOf(FestivalTicketLink(label = "멜론", url = "https://ticket.melon.com/a")))
        viewModel().test(this, initialState = loaded.copy(catalog = listOf(withLink, second))) {
            containerHost.onTicketLinkTapped(withLink.id)
            expectSideEffect(FestivalListSideEffect.OpenTicket("https://ticket.melon.com/a"))

            containerHost.onTicketLinkTapped(second.id)
            expectState { copy(toast = FestivalListToast.TicketLinkMissing, toastRevision = 1) }
            containerHost.onTicketLinkTapped(second.id)
            expectState { copy(toastRevision = 2) }

            containerHost.onToastExpired()
            expectState { copy(toast = null) }
        }
    }

    @Test
    fun `스켈레톤 · 오프라인 배지는 첫 갱신 · 실패 · 목록 유무로 정한다`() {
        val cachedBeforeRefresh = FestivalListState(catalog = listOf(cached), viewState = FestivalListViewState.Loaded, hasStarted = true)
        assertTrue(cachedBeforeRefresh.showsListSkeleton)
        assertNull(cachedBeforeRefresh.offlineMessage)
        assertTrue(loaded.copy(viewState = FestivalListViewState.Loading).showsListSkeleton)
        assertFalse(loaded.copy(viewState = FestivalListViewState.Loading, catalog = listOf(first)).showsListSkeleton)

        assertEquals(
            "저장된 페스티벌 목록을 읽지 못했습니다 · 연결 후 다시 시도해 주세요",
            loaded.copy(catalogCacheReadFailed = true, catalogRefreshFailed = true).offlineMessage,
        )
        assertEquals("페스티벌 목록을 불러오지 못했습니다 · 다시 시도해 주세요", loaded.copy(catalogRefreshFailed = true).offlineMessage)
        // 첫 쪽 성공 뒤 다음 쪽이 실패해도 iOS와 같은 문구(설계 위험 FESTIVAL-006).
        assertEquals(
            "최신 페스티벌 목록을 불러오지 못했습니다 · 저장된 목록을 표시합니다",
            loaded.copy(catalog = listOf(first), catalogRefreshFailed = true).offlineMessage,
        )
        assertNull(loaded.copy(catalog = listOf(first)).offlineMessage)
    }

    @Test
    fun `목록에 있는 축제만 고를 수 있다`() = runTest {
        viewModel().test(this, initialState = loaded.copy(catalog = listOf(first))) {
            containerHost.onSelectFestival("missing")
            containerHost.onSelectFestival(first.id)
            expectSideEffect(FestivalListSideEffect.FestivalSelected(first.id))
        }
    }
}

internal class FakeCatalogUseCase(
    private val cached: suspend () -> List<FestivalCatalogItem> = { emptyList() },
    private val page: suspend (String?, Int) -> FestivalCatalogPage = { _, _ -> FestivalCatalogPage(emptyList(), null) },
    private val refresh: suspend () -> List<FestivalCatalogItem> = { emptyList() },
) : FestivalCatalogUseCase {
    val requestedCursors = mutableListOf<String?>()
    var refreshCount = 0

    override suspend fun cachedFestivals(): List<FestivalCatalogItem> = cached()

    override suspend fun refreshFestivals(): List<FestivalCatalogItem> {
        refreshCount += 1
        return refresh()
    }

    override suspend fun festivalPage(after: String?, limit: Int): FestivalCatalogPage {
        requestedCursors += after
        return page(after, limit)
    }

    override fun imageUrl(value: String?): String? = value
}

internal class FakeLibraryUseCase(val searches: MutableMap<String?, List<String>> = mutableMapOf()) : FestivalLibraryUseCase {
    override suspend fun recentSearches(userId: String?): List<String> = searches[userId].orEmpty()

    override suspend fun addRecentSearch(query: String, userId: String?): List<String> {
        val updated = (listOf(query.trim()) + searches[userId].orEmpty()).distinct().take(10)
        searches[userId] = updated
        return updated
    }

    override suspend fun removeRecentSearch(query: String, userId: String?): List<String> {
        val updated = searches[userId].orEmpty() - query
        searches[userId] = updated
        return updated
    }

    override suspend fun clearRecentSearches(userId: String?) {
        searches[userId] = emptyList()
    }
}

internal class FakeCurrentUserProvider(userId: String? = null) : CurrentUserProvider {
    override val currentUserId = MutableStateFlow(userId)
}
