package io.festicore.feature.festival.liveboard

import androidx.lifecycle.ViewModel
import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.domain.festival.catalog.FestivalCatalogUseCase
import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalLiveBoardError
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.domain.festival.entity.FestivalOfficialNews
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.entity.FestivalPackState
import io.festicore.domain.festival.entity.FestivalScheduleSaved
import io.festicore.domain.festival.entity.PackSyncResult
import io.festicore.domain.festival.liveboard.FestivalLiveBoardPreferenceUseCase
import io.festicore.domain.festival.liveboard.FestivalLiveBoardUseCase
import io.festicore.domain.festival.pack.FestivalPackUseCase
import io.festicore.domain.festival.schedule.FestivalScheduleUseCase
import io.festicore.domain.festival.selection.FestivalBoardSelectionUseCase
import io.festicore.domain.usernotification.entity.UserNotificationBadge
import io.festicore.domain.usernotification.entity.UserNotificationBadgeUseCase
import io.festicore.feature.festival.crewgather.CrewGatherInput
import io.festicore.feature.festival.festivallist.FestivalListPresentation
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.OrbitContainer
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * S01 라이브 보드(설계 D1: 목록과 별도 ViewModel). iOS 보드 route의 `FestivalFeature`(보드 부분)와 자식 `FestivalLiveBoardFeature`를 합친다.
 * 함수 이름은 iOS `Action.View`와 같고, 순서는 view → async → inner를 따른다.
 * 알림 배지 · 배너는 공유 저장소(설계 E7)를 따르고, 보일 때 · 세션이 바뀔 때 다시 조회한다(iOS `FestivalFeature+NotificationUnreadCount`).
 * 라이브 상태 폴링(35초) · 공식 소식(300초) · 팩 감시 · 상세 · 내 일정 · 배지 조회는 각각 하나만 돈다. 새로 시작하면 이전 것을 취소한다(iOS `cancelInFlight`).
 * [sleep]은 테스트에서 기다림을 조절하려고 주입한다.
 */
class LiveBoardViewModel(
    festivalId: String,
    initialCatalog: List<FestivalCatalogItem>,
    private val liveBoardUseCase: FestivalLiveBoardUseCase,
    private val packUseCase: FestivalPackUseCase,
    private val catalogUseCase: FestivalCatalogUseCase,
    private val scheduleUseCase: FestivalScheduleUseCase,
    private val preferenceUseCase: FestivalLiveBoardPreferenceUseCase,
    private val currentUserProvider: CurrentUserProvider,
    private val notificationBadgeUseCase: UserNotificationBadgeUseCase,
    private val boardSelectionUseCase: FestivalBoardSelectionUseCase,
    private val clock: Clock = Clock.systemUTC(),
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
) : ViewModel(),
    OrbitContainerHost<LiveBoardState, LiveBoardState, LiveBoardSideEffect> {
    // 생성 블록이 자기 상태 흐름을 읽어 타입을 적는다.
    override val container: OrbitContainer<LiveBoardState, LiveBoardState, LiveBoardSideEffect> =
        orbitContainer(
            LiveBoardState(
                festivalId = festivalId,
                catalog = initialCatalog,
                isCatalogLoading = initialCatalog.isEmpty(),
                hasSession = currentUserProvider.currentUserId.value != null,
                sessionUserId = currentUserProvider.currentUserId.value,
            ),
        ) {
            coroutineScope {
                // 목록이 받거나 지운 팩 · 받는 중 진행률도 보드에 바로 보인다(설계 D1).
                launch { packUseCase.state.collect { onPackStateChanged(it) } }
                launch { notificationBadgeUseCase.badge.collect { onNotificationBadgeChanged(it) } }
                // 보드가 보여 주는 축제를 앱 범위 공유 상태에 쓴다. 타임테이블이 읽는다(설계 T1).
                launch {
                    container.stateFlow.map { it.toBoardSelection() }.distinctUntilChanged().collect(boardSelectionUseCase::update)
                }
                launch { scheduleUseCase.savedSchedules.collect { onMyScheduleSaved(it) } }
                currentUserProvider.currentUserId.collect { onSessionChanged(hasSession = it != null, userId = it) }
            }
        }

    private val liveBoardJob = AtomicReference<Job?>(null)
    private val officialNewsJob = AtomicReference<Job?>(null)
    private val packWatchJob = AtomicReference<Job?>(null)
    private val onlineDetailJob = AtomicReference<Job?>(null)
    private val myScheduleJob = AtomicReference<Job?>(null)
    private val notificationUnreadCountJob = AtomicReference<Job?>(null)

    // MARK: - View

    /** 보드가 화면에 들어왔다(iOS `onAppear` · `liveBoardOnAppear` · `onlineDetailOnAppear`). */
    fun onAppear() = intent {
        val hidden = guideHidden(state.festivalId)
        reduce { state.copy(isVisible = true, isFollowedFlagGuideHidden = hidden) }
        if (!state.hasStarted) {
            reduce { state.copy(hasStarted = true) }
            start()
        } else {
            startLiveBoard()
        }
        loadOnlineDetail()
        loadNotificationUnreadCount()
    }

    /** 보드가 화면에서 빠졌다(iOS `liveBoardOnDisappear`). 폴링 · 공식 소식 · 상세를 멈춘다. 팩 감시는 그대로 둔다. */
    fun onDisappear() = intent {
        reduce { state.copy(isVisible = false, onlineDetailLoading = false) }
        stopLiveBoard()
        onlineDetailJob.getAndSet(null)?.cancel()
    }

    /** 앱이 포그라운드를 얻거나 잃었다(iOS `foregroundChanged`). */
    fun onForegroundChanged(isForeground: Boolean) = intent {
        if (state.isForeground == isForeground) return@intent
        if (!isForeground) {
            // 상태를 바꾸기 전에 멈춰, 배경으로 가는 사이에 조회가 한 번 더 나가지 않게 한다.
            packWatchJob.getAndSet(null)?.cancel()
            stopLiveBoard()
        }
        reduce { state.copy(isForeground = isForeground) }
        if (!state.hasStarted || !isForeground) return@intent
        reduce { state.copy(packWatchFailed = false) }
        // iOS `resumeForeground`의 보드 부분.
        watchSelectedPack()
        watchLiveBoard()
        loadMySchedule()
    }

    /** 헤더의 축제 이름(iOS `festivalSwitchTapped`). 전환 목록은 이 기기에 받은 팩 기준이다. */
    fun onSwitchTapped() = intent {
        val now = clock.instant()
        val downloaded = FestivalListPresentation.sorted(state.festivals.filter { it.isCached }, now)
        val items =
            downloaded.map { festival ->
                LiveBoardSwitcherItem(
                    id = festival.id,
                    name = festival.name,
                    subtitle = LiveBoardPresentation.switcherSubtitle(state.pack(festival.id), festival, now),
                    thumbnailUrl = festival.thumbnailUrl,
                )
            }
        reduce { state.copy(switcher = items) }
    }

    fun onSwitcherDismissed() = intent { reduce { state.copy(switcher = null) } }

    /** 시트에서 축제를 골랐다(iOS 시트 `festivalSelected`). */
    fun onSwitcherFestivalChosen(festivalId: String) = intent {
        val items = state.switcher ?: return@intent
        reduce { state.copy(switcher = null) }
        if (items.any { it.id == festivalId }) selectFestival(festivalId)
    }

    /** 시트 "전체 페스티벌 보기"(iOS `seeAllFestivals` → `backToFestivalList`). */
    fun onSeeAllFestivals() = intent {
        reduce { state.copy(switcher = null) }
        postSideEffect(LiveBoardSideEffect.BackRequested)
    }

    /** 세 섹션의 "다시 시도"(iOS `retryLiveBoard`). 보이는 중일 때만 실패를 지우고 다시 조회한다. */
    fun onRetryLiveBoard() = intent {
        if (!state.isVisible) return@intent
        reduce { state.copy(isUnavailable = false) }
        startLiveBoard()
    }

    fun onRetryMySchedule() = intent { loadMySchedule() }

    /** 시간표 실패 카드의 "다시 시도"(iOS `onlineDetailOnAppear`). */
    fun onRetryOnlineDetail() = intent { loadOnlineDetail(force = true) }

    fun onHideFollowGuideTapped() = intent { reduce { state.copy(isFollowGuideDialogPresented = true) } }

    fun onFollowGuideDialogDismissed() = intent { reduce { state.copy(isFollowGuideDialogPresented = false) } }

    /** 안내 숨김 확인(iOS `followGuideAlert(.confirmTapped)`). 이 축제 동안 안내를 숨긴다. */
    fun onHideFollowGuideConfirmed() = intent {
        val festivalId = state.festivalId
        reduce { state.copy(isFollowGuideDialogPresented = false, isFollowedFlagGuideHidden = true) }
        attempt { preferenceUseCase.hideFollowedFlagGuide(festivalId) }
    }

    fun onOfficialNewsPostTapped(url: String) = intent { postSideEffect(LiveBoardSideEffect.OpenUrl(url)) }

    fun onNotificationTapped() = intent {
        boundary(if (state.hasSession) LiveBoardBoundary.NotificationInbox else LiveBoardBoundary.Login)
    }

    /**
     * 알림함에서 다른 축제 알림을 열었다(설계 E8, iOS Coordinator가 보드에 보내는 `selectFestival`).
     * 이미 고른 축제면 아무것도 하지 않는다.
     */
    fun onNotificationFestivalRequested(festivalId: String) = intent {
        if (festivalId != state.festivalId) selectFestival(festivalId)
    }

    fun onBearerTapped() = intent {
        val flagId = state.bearerFlagId ?: return@intent
        val festivalId = state.selectedFestival?.id ?: return@intent
        boundary(LiveBoardBoundary.Bearer(festivalId, flagId, state.sessionUserId))
    }

    fun onCrewLoginTapped() = intent { boundary(LiveBoardBoundary.CrewLogin) }

    fun onFollowLoginTapped() = intent { boundary(LiveBoardBoundary.FollowLogin) }

    fun onFindFlagTapped() = intent { boundary(LiveBoardBoundary.FlagDirectory) }

    fun onZoneChipTapped(zoneId: String) = intent { boundary(LiveBoardBoundary.ZoneMap(state.festivalId, zoneId)) }

    /** "모여!"(iOS `gatherCrewTapped`). 로그인 · 온라인 · 크루 있음 · 행사 종료 전일 때만. */
    fun onGatherCrewTapped() = intent {
        val crew = state.snapshot?.crew ?: return@intent
        val festival = state.selectedFestival ?: return@intent
        if (!state.hasSession || state.isUnavailable) return@intent
        if (state.connection == LiveBoardConnection.Offline || state.connection == LiveBoardConnection.Reconnecting) return@intent
        val now = LiveBoardPresentation.serverNow(state.snapshot?.serverAt, state.clockAnchorAt, clock.instant())
        if (LiveBoardPresentation.phase(state.displayDocument, festival, now) == LiveBoardPresentation.Phase.Ended) return@intent
        val count = state.crewGatherCount + 1
        reduce {
            state.copy(
                crewGatherCount = count,
                crewGather =
                    CrewGatherInput(
                        presentation = count,
                        crewId = crew.id,
                        zones = state.displayDocument?.zones.orEmpty(),
                        availableAt = crew.gatherAvailableAt,
                        recipientCount = crew.members.count { it.id != state.sessionUserId },
                    ),
            )
        }
    }

    fun onCrewGatherDismissed() = intent { reduce { state.copy(crewGather = null) } }

    /** 모여를 보냈다(iOS `crewGather(.delegate(.gathered))`). 시트를 닫고 크루 카드를 새로 받는다. */
    fun onCrewGathered() = intent {
        reduce { state.copy(crewGather = null) }
        startLiveBoard()
    }

    fun onCreateCrewTapped() = intent { createCrew() }

    fun onCrewTapped() = intent {
        val crew = state.snapshot?.crew ?: return@intent
        if (state.hasSession) boundary(LiveBoardBoundary.Crew(state.festivalId, crew.id))
    }

    fun onHomeDirectionsTapped() = intent {
        val festival = state.selectedFestival ?: return@intent
        boundary(LiveBoardBoundary.PackDirections(festival.id, festival.name))
    }

    /** "같이 귀가"(iOS `homeTogetherTapped` → `createCrewTapped`). */
    fun onHomeTogetherTapped() = intent { createCrew() }

    // MARK: - Async

    /** 첫 진입: 카탈로그 · 저장 팩을 맞추고 선택 축제를 그린 뒤 감시를 시작한다(iOS `cachedLoaded`의 보드 부분). */
    private suspend fun start() = subIntent {
        if (state.catalog.isEmpty()) {
            val cached = attempt { catalogUseCase.cachedFestivals() }.orEmpty()
            reduce { state.copy(catalog = cached.distinctBy { it.id }, isCatalogLoading = false) }
        }
        reduce {
            showCachedPack(state.copy(cachedPacks = packUseCase.state.value.packs, packDownloads = packUseCase.state.value.downloads))
        }
        val festivalId = state.festivalId
        attempt { preferenceUseCase.saveSelectedFestivalId(festivalId) }
        watchSelectedPack()
        startLiveBoard()
    }

    /** iOS `startLiveBoard`. */
    private suspend fun startLiveBoard() = subIntent {
        watchLiveBoard()
        loadMySchedule()
    }

    /** iOS `selectFestival` + `selectedFestivalChanged`. 같은 보드에서 축제를 바꾼다. */
    private suspend fun selectFestival(festivalId: String) = subIntent {
        if (state.festivals.none { it.id == festivalId }) return@subIntent
        val hidden = guideHidden(festivalId)
        stopLiveBoard()
        reduce {
            showCachedPack(
                state.copy(
                    festivalId = festivalId,
                    isFollowedFlagGuideHidden = hidden,
                    onlineDetail = null,
                    onlineDetailLoading = state.isVisible,
                    onlineDetailFailed = false,
                    snapshot = null,
                    isUnavailable = false,
                    isUnpublished = false,
                    clockAnchorAt = null,
                    receivedAt = null,
                    officialNews = null,
                    officialNewsFestivalId = null,
                    mySchedule = null,
                    myScheduleUnavailable = false,
                    packWatchFailed = false,
                    isFollowGuideDialogPresented = false,
                ),
            )
        }
        attempt { preferenceUseCase.saveSelectedFestivalId(festivalId) }
        if (state.isVisible) launchOnlineDetail(festivalId)
        watchSelectedPack()
        watchLiveBoard()
        loadMySchedule()
    }

    /** 포그라운드 · 화면 표시를 확인해 폴링과 공식 소식을 시작하거나 멈춘다(iOS `watchLiveBoard`). */
    private suspend fun watchLiveBoard() = subIntent {
        if (!state.isForeground || !state.isVisible) {
            stopLiveBoard()
            return@subIntent
        }
        val festivalId = state.festivalId
        val generation = state.accountGeneration
        replace(liveBoardJob) {
            while (true) {
                val failure = fetchLiveBoard(festivalId, generation)
                sleep(FestivalLiveBoardUseCase.nextPollDelay(failure))
            }
        }
        replace(officialNewsJob) {
            // 보조 정보라 실패하면 보이던 소식을 둔다. 처음에는 빈 소식으로 보고 같으면 보내지 않는다.
            var lastSent = FestivalOfficialNews.Unavailable
            while (true) {
                val news = attempt { liveBoardUseCase.officialNews(festivalId) }
                if (news != null && news != lastSent) {
                    lastSent = news
                    onOfficialNewsLoaded(festivalId, generation, news)
                }
                sleep(FestivalLiveBoardUseCase.OFFICIAL_NEWS_INTERVAL)
            }
        }
    }

    /** 팩 WebSocket이 새 시간표를 알리면 보드도 즉시 한 번 다시 조회한다(iOS `refreshLiveBoard`). */
    private suspend fun refreshLiveBoard() = subIntent {
        if (!state.isForeground || !state.isVisible) return@subIntent
        val festivalId = state.festivalId
        val generation = state.accountGeneration
        intent { fetchLiveBoard(festivalId, generation) }
    }

    /** 한 번 조회해 반영하고, 다음 조회 간격을 정할 실패(429 포함)를 돌려준다. */
    private suspend fun fetchLiveBoard(festivalId: String, generation: Int): Throwable? = try {
        onLiveBoardLoaded(festivalId, generation, liveBoardUseCase.liveBoard(festivalId))
        null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: FestivalLiveBoardError.FestivalNotFound) {
        onUnpublished(festivalId, generation)
        null
    } catch (rateLimited: FestivalLiveBoardError.RateLimited) {
        // 조회 제한은 장애가 아니다. 보이던 보드를 두고 허용 시각 뒤에 다시 불러온다.
        rateLimited
    } catch (error: Exception) {
        onLiveBoardFailed(festivalId, generation)
        error
    }

    /** 세션이 있고 포그라운드면 내 일정을 불러온다(iOS `loadMySchedule`). */
    private suspend fun loadMySchedule() = subIntent {
        if (!state.hasSession || !state.isForeground) {
            myScheduleJob.getAndSet(null)?.cancel()
            return@subIntent
        }
        val requestId = state.myScheduleRequestId + 1
        reduce { state.copy(myScheduleRequestId = requestId, myScheduleUnavailable = false) }
        val festivalId = state.festivalId
        val generation = state.accountGeneration
        replace(myScheduleJob) {
            val schedule = attempt { scheduleUseCase.mySchedule(festivalId) }
            if (schedule == null) {
                onMyScheduleFailed(festivalId, requestId, generation)
            } else {
                onMyScheduleLoaded(festivalId, requestId, generation, schedule.setIds.toSet())
            }
        }
    }

    /** 로그인이면 배지 · 배너를 다시 조회한다. 새로 부르면 이전 조회를 취소한다(iOS `loadNotificationUnreadCount`, `cancelInFlight`). */
    private suspend fun loadNotificationUnreadCount() = subIntent {
        if (!state.hasSession) {
            notificationUnreadCountJob.getAndSet(null)?.cancel()
            return@subIntent
        }
        replace(notificationUnreadCountJob) { notificationBadgeUseCase.refresh() }
    }

    /** 선택 축제 팩 변경을 따라간다. 받은 팩이 없거나 백그라운드면 멈춘다(iOS `watchSelectedPack`). */
    private suspend fun watchSelectedPack() = subIntent {
        val festivalId = state.festivalId
        if (!state.isForeground || state.cachedPacks[festivalId] == null) {
            packWatchJob.getAndSet(null)?.cancel()
            return@subIntent
        }
        replace(packWatchJob) {
            try {
                packUseCase.packUpdates(festivalId).collect { onPackReceived(festivalId, it) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                onPackFailed(festivalId)
            }
        }
    }

    /** 온라인 상세(iOS `loadOnlineDetailIfNeeded` · 강제면 `onlineDetailOnAppear`). */
    private suspend fun loadOnlineDetail(force: Boolean = false) = subIntent {
        if (!state.isVisible) return@subIntent
        val loaded = state.onlineDetail?.id == state.festivalId
        val loading = state.onlineDetailLoading && onlineDetailJob.get()?.isActive == true
        if (!force && (loaded || loading)) {
            return@subIntent
        }
        reduce { state.copy(onlineDetailLoading = true, onlineDetailFailed = false) }
        launchOnlineDetail(state.festivalId)
    }

    private fun launchOnlineDetail(festivalId: String) = replace(onlineDetailJob) {
        val document = attempt { liveBoardUseCase.festivalDetail(festivalId) }
        if (document == null) onOnlineDetailFailed(festivalId) else onOnlineDetailLoaded(festivalId, document)
    }

    // MARK: - Inner

    private suspend fun onLiveBoardLoaded(
        festivalId: String,
        generation: Int,
        snapshot: FestivalLiveBoardSnapshot,
    ) = subIntent {
        if (state.festivalId != festivalId || state.accountGeneration != generation) return@subIntent
        val now = clock.instant()
        reduce {
            state.copy(
                clockAnchorAt = FestivalLiveBoardUseCase.clockAnchor(state.snapshot?.serverAt, state.clockAnchorAt, snapshot.serverAt, now),
                snapshot = snapshot,
                isUnavailable = false,
                isUnpublished = false,
                receivedAt = now,
            )
        }
    }

    private suspend fun onUnpublished(festivalId: String, generation: Int) = subIntent {
        if (state.festivalId != festivalId || state.accountGeneration != generation) return@subIntent
        reduce { state.copy(snapshot = null, isUnavailable = false, isUnpublished = true, clockAnchorAt = null, receivedAt = null) }
    }

    private suspend fun onLiveBoardFailed(festivalId: String, generation: Int) = subIntent {
        if (state.festivalId != festivalId || state.accountGeneration != generation) return@subIntent
        reduce { state.copy(isUnavailable = true) }
    }

    private suspend fun onOfficialNewsLoaded(
        festivalId: String,
        generation: Int,
        news: FestivalOfficialNews,
    ) = subIntent {
        if (state.festivalId != festivalId || state.accountGeneration != generation) return@subIntent
        reduce { state.copy(officialNews = news, officialNewsFestivalId = festivalId) }
    }

    private suspend fun onMyScheduleLoaded(
        festivalId: String,
        requestId: Int,
        generation: Int,
        setIds: Set<String>,
    ) = subIntent {
        if (!state.isCurrentScheduleRequest(festivalId, requestId, generation)) return@subIntent
        reduce { state.copy(mySchedule = LiveBoardMySchedule(festivalId, setIds), myScheduleUnavailable = false) }
    }

    /**
     * 타임테이블 · 고르기가 저장한 내 일정(iOS `myScheduleSaved`, 설계 T2). 같은 축제이고 로그인 중일 때만 바꾸고,
     * 읽던 요청은 요청 ID를 올려 버린다.
     */
    private suspend fun onMyScheduleSaved(saved: FestivalScheduleSaved) = subIntent {
        if (state.festivalId != saved.festivalId || !state.hasSession) return@subIntent
        myScheduleJob.getAndSet(null)?.cancel()
        reduce {
            state.copy(
                myScheduleRequestId = state.myScheduleRequestId + 1,
                mySchedule = LiveBoardMySchedule(saved.festivalId, saved.setIds),
                myScheduleUnavailable = false,
            )
        }
    }

    /** 실패해도 이전 일정은 둔다(iOS와 같음). */
    private suspend fun onMyScheduleFailed(
        festivalId: String,
        requestId: Int,
        generation: Int,
    ) = subIntent {
        if (!state.isCurrentScheduleRequest(festivalId, requestId, generation)) return@subIntent
        reduce { state.copy(myScheduleUnavailable = true) }
    }

    /** iOS `packReceived`. 받은 팩이 바뀌었으면 라이브 보드를 즉시 다시 조회한다. */
    private suspend fun onPackReceived(festivalId: String, result: PackSyncResult) = subIntent {
        if (state.festivalId != festivalId) return@subIntent
        when (result) {
            is PackSyncResult.Updated -> onPackSaved(result.pack)

            is PackSyncResult.NotModified -> onPackSaved(result.pack)

            is PackSyncResult.RetryAfter ->
                reduce {
                    state.copy(connection = if (state.selectedPack == null) LiveBoardConnection.Connecting else LiveBoardConnection.Cached)
                }

            PackSyncResult.Reconnecting -> reduce { state.copy(connection = LiveBoardConnection.Reconnecting) }
        }
    }

    private suspend fun onPackSaved(pack: CachedFestivalPack) = subIntent {
        val previous = state.cachedPacks[pack.festivalId]
        val document = pack.document
        reduce {
            val saved = state.copy(cachedPacks = state.cachedPacks + (pack.festivalId to pack))
            if (document == null) {
                saved.copy(connection = if (saved.selectedPack == null) LiveBoardConnection.Unavailable else LiveBoardConnection.Cached)
            } else {
                saved.copy(selectedPack = document, connection = LiveBoardConnection.Live, packWatchFailed = false)
            }
        }
        val changed =
            previous == null || previous.etag != pack.etag || previous.packVersion != pack.packVersion || previous.document != document
        if (document != null && changed) refreshLiveBoard()
    }

    private suspend fun onPackFailed(festivalId: String) = subIntent {
        if (state.festivalId != festivalId) return@subIntent
        reduce { state.copy(packWatchFailed = true, connection = LiveBoardConnection.Offline) }
    }

    private suspend fun onOnlineDetailLoaded(festivalId: String, document: FestivalPackDocument) = subIntent {
        if (state.festivalId != festivalId) return@subIntent
        reduce {
            if (document.id != festivalId) {
                state.copy(onlineDetailLoading = false, onlineDetailFailed = true)
            } else {
                state.copy(onlineDetail = document, onlineDetailLoading = false, onlineDetailFailed = false)
            }
        }
    }

    private suspend fun onOnlineDetailFailed(festivalId: String) = subIntent {
        if (state.festivalId != festivalId || !state.isVisible) return@subIntent
        reduce { state.copy(onlineDetailLoading = false, onlineDetailFailed = true) }
    }

    /**
     * 공유 팩 상태가 바뀌었다(설계 D1). 받는 중 진행률을 따르고, 선택 축제 팩이 새로 생기거나 사라지면 표시 팩과 감시를 맞추고,
     * 같은 축제 팩이 다른 버전 · 내용으로 바뀌면(목록에서 받기 · 다시 읽기) 표시 팩을 새로 그린다.
     */
    private suspend fun onPackStateChanged(packState: FestivalPackState) = subIntent {
        val previous = state.cachedPacks[state.festivalId]
        reduce { state.copy(cachedPacks = packState.packs, packDownloads = packState.downloads) }
        val current = state.cachedPacks[state.festivalId]
        if (!state.hasStarted) return@subIntent
        when {
            // 선택 축제 팩이 새로 생기거나 사라졌다: 표시 팩과 감시를 맞춘다.
            (previous == null) != (current == null) -> {
                reduce { showCachedPack(state) }
                watchSelectedPack()
            }

            // 목록 · 다시 읽기로 같은 축제 팩이 새 버전 · 내용으로 바뀌었다: 표시 팩만 새로 그린다(감시는 그대로).
            previous != null && current != null && previous != current -> reduce { showCachedPack(state) }
        }
    }

    /** 공유 배지가 바뀌었다(설계 E7). 비로그인이면 보이지 않는다(iOS `guard state.hasSession`). */
    private suspend fun onNotificationBadgeChanged(badge: UserNotificationBadge) = subIntent {
        reduce {
            if (state.hasSession) {
                state.copy(notificationUnreadCount = badge.unreadCount, notificationBanner = badge.banner)
            } else {
                state.copy(notificationUnreadCount = 0, notificationBanner = null)
            }
        }
    }

    /** 세션이 바뀌었다(iOS `sessionChanged`). 세대를 올리고 이전 계정의 보드 · 일정 · 배지를 비운 뒤 다시 부른다. */
    private suspend fun onSessionChanged(hasSession: Boolean, userId: String?) = subIntent {
        if (state.hasSession == hasSession && state.sessionUserId == userId) return@subIntent
        stopLiveBoard()
        notificationUnreadCountJob.getAndSet(null)?.cancel()
        notificationBadgeUseCase.clear()
        reduce {
            state.copy(
                hasSession = hasSession,
                sessionUserId = userId,
                accountGeneration = state.accountGeneration + 1,
                snapshot = null,
                isUnavailable = false,
                isUnpublished = false,
                clockAnchorAt = null,
                receivedAt = null,
                officialNews = null,
                officialNewsFestivalId = null,
                mySchedule = null,
                myScheduleUnavailable = false,
                notificationUnreadCount = 0,
                notificationBanner = null,
                crewGather = null,
            )
        }
        if (state.isVisible) watchLiveBoard()
        if (state.isVisible) loadMySchedule()
        if (state.isVisible) loadNotificationUnreadCount()
    }

    // MARK: - 도우미

    private suspend fun createCrew() = subIntent {
        if (!state.hasSession) {
            boundary(LiveBoardBoundary.CrewLogin)
            return@subIntent
        }
        boundary(LiveBoardBoundary.CrewCreate(state.festivalId))
    }

    private suspend fun boundary(boundary: LiveBoardBoundary) = subIntent { postSideEffect(LiveBoardSideEffect.Boundary(boundary)) }

    private suspend fun guideHidden(festivalId: String): Boolean =
        attempt { preferenceUseCase.isFollowedFlagGuideHidden(festivalId) } ?: false

    private fun stopLiveBoard() {
        liveBoardJob.getAndSet(null)?.cancel()
        officialNewsJob.getAndSet(null)?.cancel()
    }

    private fun replace(slot: AtomicReference<Job?>, block: suspend () -> Unit) {
        val job = intent { block() }
        slot.getAndSet(job)?.cancel()
    }

    private fun LiveBoardState.isCurrentScheduleRequest(
        festivalId: String,
        requestId: Int,
        generation: Int,
    ) = this.festivalId == festivalId && myScheduleRequestId == requestId && accountGeneration == generation && hasSession

    private companion object {
        /** 받은 팩이 있으면 그 문서를 보이고, 없으면 감시할 것이 없다(iOS `showCachedPack`). */
        fun showCachedPack(state: LiveBoardState): LiveBoardState {
            val pack = state.cachedPacks[state.festivalId]
            return if (pack != null) {
                state.copy(selectedPack = pack.document, connection = LiveBoardConnection.Cached)
            } else {
                state.copy(selectedPack = null, connection = LiveBoardConnection.Unavailable)
            }
        }

        fun LiveBoardState.pack(festivalId: String): FestivalPackDocument? =
            if (festivalId == this.festivalId) displayDocument else cachedPacks[festivalId]?.document

        /** 취소는 그대로 올리고 나머지 오류는 null. */
        suspend fun <T> attempt(block: suspend () -> T): T? = try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }
}
