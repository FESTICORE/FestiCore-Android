package io.festicore.feature.festival.festivallist
import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalOverview
import io.festicore.domain.festival.entity.PackDownloadProgress
import io.festicore.feature.festival.FestivalCoordinator
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

/** 목록 위 토스트. iOS `FestivalFeature.Toast`의 목록 단계 범위. 북마크 토스트는 그 단계에서 붙인다. */
enum class FestivalListToast {
    TicketLinkMissing,
    PackDownloadFailed,
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
    /** 기기에 받은 팩(축제 ID → 팩). 팩 저장소의 공유 상태를 따른다(설계 D1). */
    val cachedPacks: Map<String, CachedFestivalPack> = emptyMap(),
    /** 받는 중인 축제 → 진행률(첫 진행 전이면 null). iOS `packDownloads`. */
    val packDownloads: Map<String, PackDownloadProgress?> = emptyMap(),
    /** 사용자가 바뀔 때마다 올려 이전 사용자의 받기 이벤트를 버린다(iOS `accountGeneration`). */
    val accountGeneration: Int = 0,
    val isForeground: Boolean = true,
    val hasStarted: Boolean = false,
) {
    val festivals: List<FestivalOverview> get() = FestivalListPresentation.festivals(catalog, cachedPacks)

    val trimmedQuery: String get() = query.trim()

    /** 첫 서버 갱신 전이거나, 불러오는 중인데 보일 목록이 없을 때(iOS `showsListSkeleton`). */
    val showsListSkeleton: Boolean get() = !hasFinishedFirstCatalogRefresh ||
        (viewState == FestivalListViewState.Loading && festivals.isEmpty())

    val hasCatalogFailure: Boolean get() = catalogCacheReadFailed || catalogRefreshFailed

    /** 상단 바 아래 오프라인 배지 문구(iOS `FestivalListView.offlineMessage`). */
    val offlineMessage: String?
        get() =
            when {
                catalogCacheReadFailed -> "저장된 페스티벌 목록을 읽지 못했습니다 · 연결 후 다시 시도해 주세요"
                catalogRefreshFailed && festivals.isEmpty() -> "페스티벌 목록을 불러오지 못했습니다 · 다시 시도해 주세요"
                catalogRefreshFailed -> "최신 페스티벌 목록을 불러오지 못했습니다 · 저장된 목록을 표시합니다"
                else -> null
            }

    /** 당겨서 새로고침 표시. 첫 갱신 전에는 스켈레톤이 대신한다. */
    val isRefreshing: Boolean get() = viewState == FestivalListViewState.Loading && hasFinishedFirstCatalogRefresh
}

sealed interface FestivalListSideEffect {
    data class PrepRequested(
        val festivalId: String,
        val token: Long,
    ) : FestivalListSideEffect

    /** 예매처를 연다. Screen이 Custom Tabs로 연다. */
    data class OpenTicket(val url: String) : FestivalListSideEffect

    /** 축제를 골랐다(iOS `selectFestival` → `FestivalCoordinator`). */
    data class FestivalSelected(val festivalId: String) : FestivalListSideEffect
}
