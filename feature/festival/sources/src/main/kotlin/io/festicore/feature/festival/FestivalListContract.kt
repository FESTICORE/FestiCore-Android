package io.festicore.feature.festival

import io.festicore.domain.festival.FestivalCatalogItem
import io.festicore.domain.festival.FestivalOverview

/** iOS `FestivalFeature.ViewState`. 새로고침 중이면 [Loading]. */
enum class FestivalListViewState {
    Loading,
    Loaded,
}

/** S00 탭. iOS `FestivalListInteractionState.Filter`. 순서는 탭 순서와 같다. */
enum class FestivalListFilter {
    All,
    Booked,
}

/** 목록 위 토스트. iOS `FestivalFeature.Toast`의 목록 단계 범위. 북마크 · 팩 토스트는 그 단계에서 붙인다. */
enum class FestivalListToast {
    TicketLinkMissing,
}

/** S00 목록 State. iOS `FestivalFeature.State`에서 목록에 필요한 것만 옮긴다. */
data class FestivalListState(
    val viewState: FestivalListViewState = FestivalListViewState.Loading,
    /** 첫 서버 응답(성공 · 실패)을 받았다. */
    val hasFinishedFirstCatalogRefresh: Boolean = false,
    /** 같은 ID는 처음 것만 남긴다(iOS `uniqueCatalog`). */
    val catalog: List<FestivalCatalogItem> = emptyList(),
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val catalogCacheReadFailed: Boolean = false,
    val catalogRefreshFailed: Boolean = false,
    /** 북마크 단계 전까지 빈 값이다. */
    val bookedFestivalIds: Set<String> = emptySet(),
    /** 최근 검색 키에 쓰는 현재 사용자. 비로그인은 null. */
    val userId: String? = null,
    val recentSearches: List<String> = emptyList(),
    val filter: FestivalListFilter = FestivalListFilter.All,
    val isSearching: Boolean = false,
    val query: String = "",
    val toast: FestivalListToast? = null,
    /** 새 토스트가 이전 것을 바로 바꾸게 하는 값(iOS `toastRevision`). */
    val toastRevision: Int = 0,
    /** 갱신 실패 뒤 복구되면 포스터를 다시 받는다(iOS `imageRetryPending` · `imageRevision`). */
    val imageRetryPending: Boolean = false,
    val imageRevision: Int = 0,
    val isForeground: Boolean = true,
    val hasStarted: Boolean = false,
) {
    val festivals: List<FestivalOverview> get() = catalog.map(FestivalListPresentation::overview)

    val trimmedQuery: String get() = query.trim()

    /** 첫 서버 갱신 전이거나, 불러오는 중인데 보일 목록이 없을 때(iOS `showsListSkeleton`). */
    val showsListSkeleton: Boolean get() = !hasFinishedFirstCatalogRefresh ||
        (viewState == FestivalListViewState.Loading && catalog.isEmpty())

    val hasCatalogFailure: Boolean get() = catalogCacheReadFailed || catalogRefreshFailed

    /** 상단 바 아래 오프라인 배지 문구(iOS `FestivalListView.offlineMessage`). */
    val offlineMessage: String?
        get() =
            when {
                catalogCacheReadFailed -> "저장된 페스티벌 목록을 읽지 못했습니다 · 연결 후 다시 시도해 주세요"
                catalogRefreshFailed && catalog.isEmpty() -> "페스티벌 목록을 불러오지 못했습니다 · 다시 시도해 주세요"
                catalogRefreshFailed -> "최신 페스티벌 목록을 불러오지 못했습니다 · 저장된 목록을 표시합니다"
                else -> null
            }

    /** 당겨서 새로고침 표시. 첫 갱신 전에는 스켈레톤이 대신한다. */
    val isRefreshing: Boolean get() = viewState == FestivalListViewState.Loading && hasFinishedFirstCatalogRefresh
}

sealed interface FestivalListSideEffect {
    /** 예매처를 연다. Screen이 Custom Tabs로 연다. */
    data class OpenTicket(val url: String) : FestivalListSideEffect

    /** 축제를 골랐다(iOS `selectFestival` → `FestivalCoordinator`). */
    data class FestivalSelected(val festivalId: String) : FestivalListSideEffect
}
