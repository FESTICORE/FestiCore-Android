package io.festicore.feature.festival.liveboard

import io.festicore.core.location.client.LocationClient
import io.festicore.core.location.client.MapsClient
import io.festicore.core.location.entity.LocationAuthorization
import io.festicore.core.location.entity.LocationClientError
import io.festicore.core.location.entity.LocationCoordinate
import io.festicore.core.location.entity.WalkingDuration
import io.festicore.core.location.entity.WalkingPlace
import io.festicore.domain.festival.entity.FestivalBoardSelection
import io.festicore.domain.festival.entity.FestivalFlagFollowResult
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalOfficialNews
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.entity.FestivalPackState
import io.festicore.domain.festival.entity.FestivalSchedule
import io.festicore.domain.festival.entity.FestivalScheduleSaved
import io.festicore.domain.festival.entity.PackDownloadEvent
import io.festicore.domain.festival.entity.PackSyncResult
import io.festicore.domain.festival.flagdetail.FestivalFlagUseCase
import io.festicore.domain.festival.liveboard.FestivalLiveBoardPreferenceUseCase
import io.festicore.domain.festival.liveboard.FestivalLiveBoardUseCase
import io.festicore.domain.festival.pack.FestivalPackUseCase
import io.festicore.domain.festival.schedule.FestivalScheduleUseCase
import io.festicore.domain.festival.selection.FestivalBoardSelectionUseCase
import io.festicore.domain.usernotification.entity.UserNotificationBadge
import io.festicore.domain.usernotification.entity.UserNotificationBadgeUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.orbitmvi.orbit.test.Item
import org.orbitmvi.orbit.test.OrbitTestContext

internal class FakeLiveBoardUseCase(
    var liveBoard: suspend (String) -> FestivalLiveBoardSnapshot = { snapshot(it) },
    var officialNews: suspend (String) -> FestivalOfficialNews = { FestivalOfficialNews.Unavailable },
    var detail: suspend (String) -> FestivalPackDocument = { awaitCancellation() },
    var cached: suspend (String) -> FestivalLiveBoardSnapshot? = { null },
) : FestivalLiveBoardUseCase {
    val liveBoardCalls = mutableListOf<String>()
    val officialNewsCalls = mutableListOf<String>()

    override suspend fun liveBoard(festivalId: String): FestivalLiveBoardSnapshot {
        liveBoardCalls += festivalId
        return liveBoard.invoke(festivalId)
    }

    override suspend fun officialNews(festivalId: String): FestivalOfficialNews {
        officialNewsCalls += festivalId
        return officialNews.invoke(festivalId)
    }

    override suspend fun festivalDetail(festivalId: String): FestivalPackDocument = detail(festivalId)

    override suspend fun cachedLiveBoard(festivalId: String): FestivalLiveBoardSnapshot? = cached(festivalId)

    companion object {
        fun snapshot(
            festivalId: String,
            serverAt: Instant? = Instant.parse("2026-10-20T06:00:00Z"),
            activeFlags: List<FestivalLiveBoardSnapshot.Flag> = emptyList(),
        ) = FestivalLiveBoardSnapshot(
            festivalId = festivalId,
            activeFlags = activeFlags,
            followedFlags = emptyList(),
            crew = null,
            serverAt = serverAt,
        )
    }
}

internal class FakeBoardPackUseCase(
    initial: FestivalPackState = FestivalPackState(),
    private val updates: (String) -> Flow<PackSyncResult> = { emptyFlow() },
    var sync: suspend (String) -> PackSyncResult = { error("사용하지 않음") },
) : FestivalPackUseCase {
    override val state = MutableStateFlow(initial)
    val watched = mutableListOf<String>()

    override suspend fun reloadCachedPacks() = state.value.packs.values.toList()

    override fun packUpdates(festivalId: String): Flow<PackSyncResult> {
        watched += festivalId
        return updates(festivalId)
    }

    override fun downloadPack(festivalId: String): Flow<PackDownloadEvent> = emptyFlow()

    override suspend fun syncPack(festivalId: String): PackSyncResult = sync(festivalId)

    override suspend fun deleteExpiredPacks(): List<String> = error("사용하지 않음")
}

internal class FakeScheduleUseCase(var schedule: suspend (String) -> FestivalSchedule = { FestivalSchedule(emptyList()) }) :
    FestivalScheduleUseCase {
    val calls = mutableListOf<String>()

    override suspend fun mySchedule(festivalId: String): FestivalSchedule {
        calls += festivalId
        return schedule(festivalId)
    }

    override suspend fun saveMySchedule(
        festivalId: String,
        setIds: List<String>,
        clientAt: Instant,
    ): FestivalSchedule = error("보드는 내 일정을 저장하지 않는다")

    /** 저장 알림. 테스트가 보낸다. */
    override val savedSchedules = MutableSharedFlow<FestivalScheduleSaved>(extraBufferCapacity = 8)

    override suspend fun notifyMyScheduleSaved(saved: FestivalScheduleSaved) = savedSchedules.emit(saved)
}

/** 보드 선택 공유 상태 가짜 구현. 보드가 쓴 값을 그대로 담는다. */
internal class FakeBoardSelectionUseCase : FestivalBoardSelectionUseCase {
    override val selection = MutableStateFlow<FestivalBoardSelection?>(null)

    /** 타임테이블 "다시 시도". 테스트가 보낸다. */
    val retries = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val retryRequests: Flow<String> = retries

    override fun update(selection: FestivalBoardSelection) {
        this.selection.value = selection
    }

    override fun clear() {
        selection.value = null
    }

    override suspend fun requestRetry(festivalId: String) = Unit
}

internal class FakePreferenceUseCase : FestivalLiveBoardPreferenceUseCase {
    var savedId: String? = null
    val hidden = mutableSetOf<String>()

    override suspend fun savedFestivalId(): String? = savedId

    override suspend fun saveSelectedFestivalId(festivalId: String) {
        savedId = festivalId
    }

    override suspend fun isFollowedFlagGuideHidden(festivalId: String) = festivalId in hidden

    override suspend fun hideFollowedFlagGuide(festivalId: String) {
        hidden += festivalId
    }
}

/** 테스트가 시각을 옮기는 시계. */
internal class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

/** 폴링 기다림을 간격마다 따로 풀어 주는 대역. [release]할 때까지 기다림이 끝나지 않는다. */
internal class TestSleeper {
    val requested = mutableListOf<Duration>()
    private val gates = mutableMapOf<Duration, Channel<Unit>>()

    private fun gate(duration: Duration) = gates.getOrPut(duration) { Channel(Channel.UNLIMITED) }

    suspend fun sleep(duration: Duration) {
        requested += duration
        gate(duration).receive()
    }

    fun release(duration: Duration) {
        gate(duration).trySend(Unit)
    }
}

internal typealias BoardTest = OrbitTestContext<LiveBoardState, LiveBoardSideEffect, LiveBoardViewModel>

/** [predicate]를 만족하는 State가 올 때까지 앞선 항목을 건너뛴다. */
internal suspend fun BoardTest.awaitBoard(predicate: (LiveBoardState) -> Boolean): LiveBoardState {
    while (true) {
        val item = awaitItem()
        if (item is Item.StateItem && predicate(item.value)) return item.value
    }
}

/** 다음 SideEffect가 올 때까지 State를 건너뛴다. */
internal suspend fun BoardTest.awaitEffect(): LiveBoardSideEffect {
    while (true) {
        val item = awaitItem()
        if (item is Item.SideEffectItem) return item.value
    }
}

/** 공유 배지 가짜 구현. 호출 횟수만 센다. [refreshResult]가 있으면 [refresh] 때 그 값으로 바꾼다. */
internal class FakeNotificationBadgeUseCase(var refreshResult: UserNotificationBadge? = null) : UserNotificationBadgeUseCase {
    override val badge = MutableStateFlow(UserNotificationBadge())
    var refreshCount = 0
    var clearCount = 0
    val markedRead = mutableListOf<String>()
    val markedReadSessions = mutableListOf<String>()
    var unreadCountRefreshCount = 0

    override suspend fun refresh() {
        refreshCount += 1
        refreshResult?.let { badge.value = it }
    }

    override suspend fun refreshUnreadCount() {
        unreadCountRefreshCount += 1
    }

    override fun markReadInBackground(id: String, sessionId: String) {
        markedRead += id
        markedReadSessions += sessionId
    }

    override fun clear() {
        clearCount += 1
        badge.value = UserNotificationBadge()
    }
}

/** 깃발 팔로우 가짜 구현. 기본은 요청한 값대로 팔로워 1을 돌려준다. 부른 순서를 [calls]에 남긴다. */
internal class FakeFlagUseCase(
    var setFollowing: suspend (
        Boolean,
        String,
    ) -> FestivalFlagFollowResult = { following, id -> FestivalFlagFollowResult(id, following, 1) },
) : FestivalFlagUseCase {
    val calls = mutableListOf<Pair<Boolean, String>>()

    override suspend fun setFollowing(following: Boolean, flagId: String): FestivalFlagFollowResult {
        calls += following to flagId
        return setFollowing.invoke(following, flagId)
    }
}

/**
 * 위치 클라이언트 가짜 구현(iOS `MockLocationClient`). 넘기지 않은 호출은 [unexpected]에 남기고 위치를 쓰지 않는 쪽으로 답한다.
 */
internal class FakeLocationClient(
    private val authorization: (suspend () -> LocationAuthorization)? = null,
    private val requestWhenInUseAuthorization: (suspend () -> LocationAuthorization)? = null,
    private val currentLocation: (suspend () -> LocationCoordinate)? = null,
) : LocationClient {
    val unexpected = mutableListOf<String>()
    var requestCount = 0

    override suspend fun authorization(): LocationAuthorization =
        authorization?.invoke() ?: LocationAuthorization.Denied.also { unexpected += "authorization" }

    override suspend fun requestWhenInUseAuthorization(): LocationAuthorization {
        requestCount += 1
        return requestWhenInUseAuthorization?.invoke()
            ?: LocationAuthorization.Denied.also { unexpected += "requestWhenInUseAuthorization" }
    }

    override suspend fun currentLocation(): LocationCoordinate {
        val handler = currentLocation ?: run {
            unexpected += "currentLocation"
            throw LocationClientError.LocationUnavailable
        }
        return handler()
    }
}

/** 지도 클라이언트 가짜 구현(iOS `MockAppleMapsClient`). 넘기지 않은 호출은 [unexpected]에 남기고 실패로 답한다. */
internal class FakeMapsClient(
    private val walkingDuration: (suspend (LocationCoordinate, WalkingPlace) -> WalkingDuration)? = null,
    private val openWalkingDirections: (suspend (WalkingPlace) -> Boolean)? = null,
    /** 넘기지 않으면 키가 없는 것처럼 자동차 시간을 계산하지 못한다. */
    private val drivingDuration: suspend (
        LocationCoordinate,
        WalkingPlace,
    ) -> WalkingDuration = { _, _ -> throw LocationClientError.RouteUnavailable },
) : MapsClient {
    val unexpected = mutableListOf<String>()
    val opened = mutableListOf<WalkingPlace>()

    override suspend fun walkingDuration(origin: LocationCoordinate, place: WalkingPlace): WalkingDuration {
        val handler = walkingDuration ?: run {
            unexpected += "walkingDuration"
            throw LocationClientError.RouteUnavailable
        }
        return handler(origin, place)
    }

    override suspend fun drivingDuration(origin: LocationCoordinate, place: WalkingPlace): WalkingDuration =
        drivingDuration.invoke(origin, place)

    override suspend fun openWalkingDirections(place: WalkingPlace): Boolean {
        opened += place
        return openWalkingDirections?.invoke(place) ?: false.also { unexpected += "openWalkingDirections" }
    }
}
