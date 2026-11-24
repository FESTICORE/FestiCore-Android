package io.festicore.feature.festival.liveboard

import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalBoardSelection
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalOfficialNews
import io.festicore.domain.festival.entity.FestivalOverview
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.entity.PackDownloadProgress
import io.festicore.domain.usernotification.entity.UserNotificationItem
import io.festicore.feature.festival.festivallist.FestivalListPresentation
import io.festicore.feature.festival.liveboard.crewgather.CrewGatherInput
import java.time.Instant

/** 선택 축제 팩 감시 상태. iOS `FestivalFeature.Connection`. */
enum class LiveBoardConnection {
    Loading,
    Cached,
    Connecting,
    Reconnecting,
    Live,
    Offline,
    Unavailable,
}

/** 서버에 저장된 내 일정 중 한 축제의 공연 ID. iOS `FestivalFeature.MySchedule`. */
data class LiveBoardMySchedule(
    val festivalId: String,
    val setIds: Set<String>,
)

/** 축제 바꾸기 시트 항목. iOS `FestivalSwitcherFeature.State.Item`. */
data class LiveBoardSwitcherItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val thumbnailUrl: String?,
)

/**
 * S01 라이브 보드 State. iOS 보드 route의 `FestivalFeature.State` + 자식 `FestivalLiveBoardFeature.State`에서 보드에 필요한 것만 옮긴다.
 * 받은 팩 · 받는 중 진행률은 팩 저장소의 공유 상태를 따른다(설계 D1).
 */
data class LiveBoardState(
    val festivalId: String,
    /** 목록에서 넘겨받은 카탈로그. 비었으면 저장 목록을 읽는다(iOS는 목록 상태를 복사해 push한다). */
    val catalog: List<FestivalCatalogItem> = emptyList(),
    /** 카탈로그를 아직 읽는 중이다(iOS `viewState == .loading`). */
    val isCatalogLoading: Boolean = false,
    val cachedPacks: Map<String, CachedFestivalPack> = emptyMap(),
    val packDownloads: Map<String, PackDownloadProgress?> = emptyMap(),
    val selectedPack: FestivalPackDocument? = null,
    val onlineDetail: FestivalPackDocument? = null,
    val onlineDetailLoading: Boolean = false,
    val onlineDetailFailed: Boolean = false,
    val connection: LiveBoardConnection = LiveBoardConnection.Loading,
    val packWatchFailed: Boolean = false,
    // 라이브 상태(iOS `FestivalLiveBoardFeature.State`)
    val snapshot: FestivalLiveBoardSnapshot? = null,
    val isUnavailable: Boolean = false,
    val isUnpublished: Boolean = false,
    /** 현재 serverAt을 처음 받은 기기 시각. 304 응답에서도 서버 시각 보정 기준을 유지한다. */
    val clockAnchorAt: Instant? = null,
    /** 마지막으로 라이브 보드가 정상 확인된 기기 시각. 304 응답도 포함한다. */
    val receivedAt: Instant? = null,
    val officialNews: FestivalOfficialNews? = null,
    val officialNewsFestivalId: String? = null,
    // 세션
    val hasSession: Boolean = false,
    /** 확인된 사용자 ID. 크루의 `나` 표시에만 쓴다. */
    val sessionUserId: String? = null,
    /** 세션이 바뀔 때마다 올려 이전 세션의 응답을 버린다. */
    val accountGeneration: Int = 0,
    val mySchedule: LiveBoardMySchedule? = null,
    val myScheduleUnavailable: Boolean = false,
    val myScheduleRequestId: Int = 0,
    /** 종 아이콘 배지(iOS `notificationUnreadCount`). 공유 배지 상태를 따른다(설계 E7). */
    val notificationUnreadCount: Int = 0,
    /** 24시간 안의 최신 안 읽은 알림 배너(iOS `notificationBanner`). */
    val notificationBanner: UserNotificationItem? = null,
    /** 선택 축제의 빈 팔로우 깃발 안내를 숨겼는지. */
    val isFollowedFlagGuideHidden: Boolean = false,
    /** 축제 바꾸기 시트. null이면 닫힘. */
    val switcher: List<LiveBoardSwitcherItem>? = null,
    /** 팔로우 안내 숨김 확인 다이얼로그. */
    val isFollowGuideDialogPresented: Boolean = false,
    /** "모여!" 시트(iOS `crewGather`). null이면 닫힘. 탭 바 숨김 대상이 아니다(iOS `shouldHideTabBar`). */
    val crewGather: CrewGatherInput? = null,
    /** 모여 시트를 연 횟수. 시트 ViewModel 키로 쓴다. */
    val crewGatherCount: Int = 0,
    /** 귀가 카드 도보 계산 단계(iOS `homeWalk`). */
    val homeWalk: HomeWalkState = HomeWalkState.Idle,
    val isVisible: Boolean = false,
    val isForeground: Boolean = true,
    val hasStarted: Boolean = false,
) {
    val festivals: List<FestivalOverview> get() = FestivalListPresentation.festivals(catalog, cachedPacks)

    val selectedFestival: FestivalOverview? get() = festivals.firstOrNull { it.id == festivalId }

    /** 표시 문서 = 온라인 상세, 없으면 받은 팩(iOS `displayDocument`). */
    val displayDocument: FestivalPackDocument? get() = onlineDetail ?: selectedPack

    /** 지금 선택한 축제의 공식 소식. 받기 전이거나 다른 축제 것이면 null. */
    val visibleOfficialNews: FestivalOfficialNews? get() = officialNews?.takeIf { officialNewsFestivalId == festivalId }

    val mySetIds: Set<String> get() = mySchedule?.setIds.orEmpty()

    val showsFollowGuide: Boolean get() = !isFollowedFlagGuideHidden

    /** 로그인한 사용자가 이 축제에서 맡은 깃발. 여러 개면 첫 번째를 연다. */
    val bearerFlagId: String?
        get() = snapshot?.takeIf { hasSession && it.festivalId == festivalId }?.bearerFlagIds?.firstOrNull()

    /**
     * 선택 축제의 팩을 처음 받는 중이다. 이때는 공연 영역에 빈 상태 대신 스켈레톤을 보인다.
     * 오프라인 · 재연결 중에는 팩 서비스가 재시도만 하고 끝나지 않으므로 스켈레톤을 남기지 않는다.
     */
    val isPackLoading: Boolean
        get() {
            if (displayDocument == null && onlineDetailLoading) return true
            if (onlineDetailFailed) return false
            if (selectedPack != null || packWatchFailed || cachedPacks[festivalId] == null) return false
            return connection == LiveBoardConnection.Loading || connection == LiveBoardConnection.Connecting
        }

    /** 시트 · 다이얼로그가 떠 있다. 상위는 이 값으로 탭 바를 숨긴다(설계 D9, iOS `MainTabCoordinator.shouldHideTabBar`). */
    val isPresentingModal: Boolean get() = switcher != null || isFollowGuideDialogPresented
}

/** 공유 선택 값. 표시 문서는 ID가 선택 축제와 같을 때만 넘긴다(iOS `MainTabCoordinator+Sync.syncTimetable`). */
internal fun LiveBoardState.toBoardSelection() = FestivalBoardSelection(
    festivalId = festivalId,
    document = displayDocument?.takeIf { it.id == festivalId },
    isOnlineDetailLoading = onlineDetailLoading,
    isOnlineDetailFailed = onlineDetailFailed,
    isPackWatchFailed = packWatchFailed,
    connection = when (connection) {
        LiveBoardConnection.Loading -> FestivalBoardSelection.Connection.Loading
        LiveBoardConnection.Cached -> FestivalBoardSelection.Connection.Cached
        LiveBoardConnection.Connecting -> FestivalBoardSelection.Connection.Connecting
        LiveBoardConnection.Reconnecting -> FestivalBoardSelection.Connection.Reconnecting
        LiveBoardConnection.Live -> FestivalBoardSelection.Connection.Live
        LiveBoardConnection.Offline -> FestivalBoardSelection.Connection.Offline
        LiveBoardConnection.Unavailable -> FestivalBoardSelection.Connection.Unavailable
    },
    packCheckedAt = cachedPacks[festivalId]?.checkedAt,
)

/** 보드가 알리는 이동. Coordinator가 대상 화면을 쌓거나(존 지도 · 알림함), 대상이 아직 없으면 받아 두기만 한다(설계 "경계만 표시"). */
sealed interface LiveBoardBoundary {
    data object NotificationInbox : LiveBoardBoundary

    /** 비로그인 알림 아이콘(iOS `loginRequested`). */
    data object Login : LiveBoardBoundary

    data object CrewLogin : LiveBoardBoundary

    /** 참여 확인에서 비로그인으로 "참여하기"(iOS `crewJoinLoginRequested(crewName:)`). [crewName]은 로그인 시트 문구에 넣는 초대받은 크루 이름(불러오기 전이면 null). */
    data class CrewJoinLogin(val crewName: String?) : LiveBoardBoundary

    data object FollowLogin : LiveBoardBoundary

    /** 팔로우 안내의 "깃발 찾기"(iOS `flagDirectoryRequested`). */
    data object FlagDirectory : LiveBoardBoundary

    data class ZoneMap(
        val festivalId: String,
        val zoneId: String,
    ) : LiveBoardBoundary

    data class Bearer(
        val festivalId: String,
        val flagId: String,
        val userId: String?,
    ) : LiveBoardBoundary

    data class CrewCreate(val festivalId: String) : LiveBoardBoundary

    data class Crew(
        val festivalId: String,
        val crewId: String,
    ) : LiveBoardBoundary

    /** 귀가 "가는 길"에서 안내할 역 · 정류장이 없거나 지도 앱을 열지 못했다. 팩 가는 길로 보낸다(iOS `packDirectionsRequested`). */
    data class PackDirections(
        val festivalId: String,
        val title: String,
    ) : LiveBoardBoundary
}

sealed interface LiveBoardSideEffect {
    /** 목록으로 돌아간다(iOS `backToFestivalList`). */
    data object BackRequested : LiveBoardSideEffect

    /** 인스타그램 원본 게시물을 연다. Screen이 Custom Tabs로 연다(설계 D10). */
    data class OpenUrl(val url: String) : LiveBoardSideEffect

    data class Boundary(val boundary: LiveBoardBoundary) : LiveBoardSideEffect
}
