package io.festicore.feature.festival.liveboard

import io.festicore.domain.festival.entity.CachedFestivalPack
import io.festicore.domain.festival.entity.FestivalBoardSelection
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalLiveBoardError
import io.festicore.domain.festival.entity.FestivalOfficialNews
import io.festicore.domain.festival.entity.FestivalPackDocument
import io.festicore.domain.festival.entity.FestivalPackState
import io.festicore.domain.festival.entity.FestivalSchedule
import io.festicore.domain.festival.entity.FestivalScheduleError
import io.festicore.domain.festival.entity.FestivalScheduleSaved
import io.festicore.domain.festival.entity.PackSyncResult
import io.festicore.domain.festival.liveboard.FestivalLiveBoardUseCase
import io.festicore.domain.usernotification.entity.UserNotificationBadge
import io.festicore.domain.usernotification.entity.UserNotificationItem
import io.festicore.feature.festival.festivallist.FakeCatalogUseCase
import io.festicore.feature.festival.festivallist.FakeCurrentUserProvider
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * 라이브 보드 ViewModel. iOS `FestivalLiveBoardOfficialNewsTests` · `FestivalLiveBoardSessionTests` · `FestivalOfflineTests`(보드 사례) ·
 * `FestivalFeatureStateTests`(보드 계산 값)의 보드 사례를 옮긴다. 북마크 · 원격 푸시 · Live Activity 사례는 해당 문서 몫이라 뺀다.
 */
class LiveBoardViewModelTest {
    private val item =
        FestivalCatalogItem(id = "fst", name = "원더리벳 2026", firstDate = "2026-10-20", lastDate = "2026-10-20", packVersion = 1)
    private val other =
        FestivalCatalogItem(id = "fst_b", name = "다른 축제", firstDate = "2026-10-25", lastDate = "2026-10-25", packVersion = 1)
    private val clock = MutableClock(Instant.parse("2026-10-20T06:00:00Z"))
    private val sleeper = TestSleeper()
    private val news =
        FestivalOfficialNews(
            isAvailable = true,
            account = FestivalOfficialNews.Account("wonderlivet", "https://www.instagram.com/wonderlivet"),
            posts = listOf(
                FestivalOfficialNews.Post("p1", "공지", null, "https://www.instagram.com/p/p1", Instant.parse("2026-10-20T05:00:00Z")),
            ),
        )

    private fun cached(
        id: String,
        etag: String = "v1",
        document: FestivalPackDocument? = FestivalPackDocument(id = id),
    ) = CachedFestivalPack(id, 1, etag, checkedAt = null, document = document)

    private fun viewModel(
        liveBoard: FakeLiveBoardUseCase = FakeLiveBoardUseCase(),
        packs: FakeBoardPackUseCase = FakeBoardPackUseCase(),
        schedule: FakeScheduleUseCase = FakeScheduleUseCase(),
        preference: FakePreferenceUseCase = FakePreferenceUseCase(),
        user: FakeCurrentUserProvider = FakeCurrentUserProvider(),
        catalog: List<FestivalCatalogItem> = listOf(item, other),
        badge: FakeNotificationBadgeUseCase = FakeNotificationBadgeUseCase(),
        selection: FakeBoardSelectionUseCase = FakeBoardSelectionUseCase(),
    ) = LiveBoardViewModel(
        festivalId = "fst",
        initialCatalog = catalog,
        liveBoardUseCase = liveBoard,
        packUseCase = packs,
        catalogUseCase = FakeCatalogUseCase(),
        scheduleUseCase = schedule,
        preferenceUseCase = preference,
        currentUserProvider = user,
        notificationBadgeUseCase = badge,
        boardSelectionUseCase = selection,
        clock = clock,
        sleep = sleeper::sleep,
    )

    // MARK: - 공식 소식 (iOS FestivalLiveBoardOfficialNewsTests)

    @Test
    fun `라이브 보드를 감시하면 공식 소식을 받고, 5분 뒤 바뀐 소식만 다시 반영한다`() = runTest {
        val changed = news.copy(posts = news.posts + news.posts.first().copy(id = "p2"))
        val answers = ArrayDeque(listOf(news, news, changed))
        val liveBoard = FakeLiveBoardUseCase(officialNews = { answers.removeFirst() })
        viewModel(liveBoard).test(this) {
            containerHost.onAppear()
            awaitBoard { it.visibleOfficialNews == news }
            sleeper.release(FestivalLiveBoardUseCase.OFFICIAL_NEWS_INTERVAL)
            sleeper.release(FestivalLiveBoardUseCase.OFFICIAL_NEWS_INTERVAL)
            val state = awaitBoard { it.visibleOfficialNews == changed }
            assertEquals("fst", state.officialNewsFestivalId)
            assertEquals(3, liveBoard.officialNewsCalls.size)
            cancelAndIgnoreRemainingItems()
        }
        assertTrue(FestivalLiveBoardUseCase.OFFICIAL_NEWS_INTERVAL in sleeper.requested)
    }

    @Test
    fun `공식 계정이 없거나 불러오지 못하면 보내지 않고 이미 보이던 소식을 유지한다`() = runTest {
        val answers =
            ArrayDeque(listOf<suspend () -> FestivalOfficialNews>({ news }, { FestivalOfficialNews.Unavailable }, { error("실패") }))
        val liveBoard = FakeLiveBoardUseCase(officialNews = { answers.removeFirst()() })
        viewModel(liveBoard).test(this) {
            containerHost.onAppear()
            awaitBoard { it.visibleOfficialNews == news }
            sleeper.release(FestivalLiveBoardUseCase.OFFICIAL_NEWS_INTERVAL)
            val unavailable = awaitBoard { it.visibleOfficialNews == FestivalOfficialNews.Unavailable }
            assertFalse(unavailable.visibleOfficialNews?.isVisible ?: true)
            sleeper.release(FestivalLiveBoardUseCase.OFFICIAL_NEWS_INTERVAL)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `이전 축제의 소식은 다른 축제를 고르면 보이지 않는다`() = runTest {
        val packs = FakeBoardPackUseCase(FestivalPackState(packs = mapOf("fst" to cached("fst"), "fst_b" to cached("fst_b"))))
        val liveBoard = FakeLiveBoardUseCase(officialNews = { if (it == "fst") news else awaitCancellation() })
        viewModel(liveBoard, packs).test(this) {
            containerHost.onAppear()
            awaitBoard { it.visibleOfficialNews == news }
            containerHost.onSwitchTapped()
            awaitBoard { it.switcher != null }
            containerHost.onSwitcherFestivalChosen("fst_b")
            val switched = awaitBoard { it.festivalId == "fst_b" }
            assertNull(switched.visibleOfficialNews)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `다른 축제나 계정 세대의 늦은 응답은 버린다`() = runTest {
        val late = CompletableDeferred<FestivalOfficialNews>()
        var calls = 0
        val user = FakeCurrentUserProvider()
        val liveBoard = FakeLiveBoardUseCase(officialNews = { if (calls++ == 0) late.await() else awaitCancellation() })
        viewModel(liveBoard, user = user).test(this) {
            runOnCreate()
            containerHost.onAppear()
            awaitBoard { it.snapshot != null }
            user.currentUserId.value = "usr_1"
            awaitBoard { it.accountGeneration == 1 && it.snapshot != null }
            late.complete(news)
            testScheduler.runCurrent()
            cancelAndIgnoreRemainingItems()
            assertNull(containerHost.container.stateFlow.value.officialNews)
        }
    }

    @Test
    fun `게시물을 누르면 인스타그램 원본 게시물을 연다`() = runTest {
        viewModel().test(this) {
            containerHost.onOfficialNewsPostTapped("https://www.instagram.com/p/p1")
            expectSideEffect(LiveBoardSideEffect.OpenUrl("https://www.instagram.com/p/p1"))
        }
    }

    // MARK: - 세션 · 폴링 (iOS FestivalLiveBoardSessionTests)

    @Test
    fun `팩 WebSocket에서 새 팩을 받으면 라이브 보드를 즉시 다시 조회한다`() = runTest {
        val updates = MutableSharedFlow<PackSyncResult>()
        val packs = FakeBoardPackUseCase(FestivalPackState(packs = mapOf("fst" to cached("fst"))), updates = { updates })
        val liveBoard = FakeLiveBoardUseCase()
        viewModel(liveBoard, packs).test(this) {
            containerHost.onAppear()
            awaitBoard { it.snapshot != null }
            assertEquals(1, liveBoard.liveBoardCalls.size)
            updates.emit(PackSyncResult.Updated(cached("fst", etag = "v2")))
            awaitBoard { it.connection == LiveBoardConnection.Live }
            // 같은 팩을 다시 확인한 것은 재조회하지 않는다.
            updates.emit(PackSyncResult.NotModified(cached("fst", etag = "v2")))
            testScheduler.runCurrent()
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(2, liveBoard.liveBoardCalls.size)
        assertEquals(listOf("fst"), packs.watched)
    }

    @Test
    fun `목록에서 같은 축제 팩이 새 버전으로 바뀌면 보드도 새 문서를 그린다`() = runTest {
        val packs =
            FakeBoardPackUseCase(
                FestivalPackState(
                    packs = mapOf("fst" to cached("fst", etag = "v1", document = FestivalPackDocument(id = "fst", name = "옛 이름"))),
                ),
            )
        viewModel(packs = packs).test(this) {
            // 공유 팩 상태 구독은 onCreate에서 시작한다.
            runOnCreate()
            containerHost.onAppear()
            awaitBoard { it.selectedPack?.name == "옛 이름" }
            packs.state.value =
                FestivalPackState(
                    packs = mapOf("fst" to cached("fst", etag = "v2", document = FestivalPackDocument(id = "fst", name = "새 이름"))),
                )
            awaitBoard { it.selectedPack?.name == "새 이름" }
            cancelAndIgnoreRemainingItems()
        }
        // 같은 축제라 감시는 다시 시작하지 않는다.
        assertEquals(listOf("fst"), packs.watched)
    }

    @Test
    fun `미발행 축제는 공개 전 상태를 표시하고 발행 후 자동으로 보드를 보여준다`() = runTest {
        var published = false
        val liveBoard =
            FakeLiveBoardUseCase(liveBoard = {
                if (!published) throw FestivalLiveBoardError.FestivalNotFound()
                FakeLiveBoardUseCase.snapshot(it)
            })
        viewModel(liveBoard).test(this) {
            containerHost.onAppear()
            val unpublished = awaitBoard { it.isUnpublished }
            assertNull(unpublished.snapshot)
            assertFalse(LiveBoardScreenModel(unpublished).showsOfflineStatus)
            published = true
            sleeper.release(FestivalLiveBoardUseCase.POLL_INTERVAL)
            val shown = awaitBoard { it.snapshot != null }
            assertFalse(shown.isUnpublished)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `조회 제한(429)이면 오프라인으로 바꾸지 않고 보이던 보드를 둔 채 허용 시각 뒤에 다시 불러온다`() = runTest {
        var limited = false
        val liveBoard =
            FakeLiveBoardUseCase(liveBoard = {
                if (limited) throw FestivalLiveBoardError.RateLimited(60.seconds)
                FakeLiveBoardUseCase.snapshot(it)
            })
        viewModel(liveBoard).test(this) {
            containerHost.onAppear()
            val loaded = awaitBoard { it.snapshot != null }
            limited = true
            sleeper.release(FestivalLiveBoardUseCase.POLL_INTERVAL)
            testScheduler.runCurrent()
            cancelAndIgnoreRemainingItems()
            val state = containerHost.container.stateFlow.value
            assertEquals(loaded.snapshot, state.snapshot)
            assertFalse(state.isUnavailable)
            assertFalse(LiveBoardScreenModel(state).showsOfflineStatus)
        }
        assertEquals(setOf(35.seconds, 300.seconds, 60.seconds), sleeper.requested.toSet())
    }

    @Test
    fun `로그인 상태에서 라이브 보드가 보이면 내 일정을 불러온다`() = runTest {
        val schedule = FakeScheduleUseCase { FestivalSchedule(listOf("set_next")) }
        viewModel(schedule = schedule, user = FakeCurrentUserProvider("usr_1")).test(this) {
            containerHost.onAppear()
            val state = awaitBoard { it.mySchedule != null }
            assertEquals(LiveBoardMySchedule("fst", setOf("set_next")), state.mySchedule)
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(listOf("fst"), schedule.calls)
    }

    @Test
    fun `로그아웃 상태에서는 내 일정을 불러오지 않는다`() = runTest {
        val schedule = FakeScheduleUseCase()
        viewModel(schedule = schedule).test(this) {
            containerHost.onAppear()
            awaitBoard { it.snapshot != null }
            cancelAndIgnoreRemainingItems()
        }
        assertTrue(schedule.calls.isEmpty())
    }

    @Test
    fun `다른 축제 · 이전 요청의 내 일정 응답은 버리고 화면을 닫아도 일정을 유지한다`() = runTest {
        val first = CompletableDeferred<FestivalSchedule>()
        var calls = 0
        val schedule = FakeScheduleUseCase { if (calls++ == 0) first.await() else FestivalSchedule(listOf("set_b")) }
        val packs = FakeBoardPackUseCase(FestivalPackState(packs = mapOf("fst" to cached("fst"), "fst_b" to cached("fst_b"))))
        viewModel(packs = packs, schedule = schedule, user = FakeCurrentUserProvider("usr_1")).test(this) {
            containerHost.onAppear()
            awaitBoard { it.myScheduleRequestId == 1 }
            containerHost.onSwitchTapped()
            awaitBoard { it.switcher != null }
            containerHost.onSwitcherFestivalChosen("fst_b")
            awaitBoard { it.mySchedule == LiveBoardMySchedule("fst_b", setOf("set_b")) }
            first.complete(FestivalSchedule(listOf("set_a")))
            containerHost.onDisappear()
            val hidden = awaitBoard { !it.isVisible }
            assertEquals(LiveBoardMySchedule("fst_b", setOf("set_b")), hidden.mySchedule)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `계정 변경 뒤 이전 세대의 라이브 보드 응답은 버린다`() = runTest {
        val late = CompletableDeferred<Unit>()
        var calls = 0
        val user = FakeCurrentUserProvider()
        val liveBoard =
            FakeLiveBoardUseCase(liveBoard = {
                if (calls++ == 0) late.await()
                FakeLiveBoardUseCase.snapshot(it, serverAt = Instant.parse("2026-10-20T06:0$calls:00Z"))
            })
        viewModel(liveBoard, user = user).test(this) {
            runOnCreate()
            containerHost.onAppear()
            awaitBoard { it.hasStarted }
            user.currentUserId.value = "usr_1"
            val renewed = awaitBoard { it.accountGeneration == 1 && it.snapshot != null }
            late.complete(Unit)
            testScheduler.runCurrent()
            cancelAndIgnoreRemainingItems()
            assertEquals(renewed.snapshot, containerHost.container.stateFlow.value.snapshot)
        }
    }

    @Test
    fun `같은 세션 값이 다시 와도 비우거나 다시 불러오지 않는다`() = runTest {
        val user = FakeCurrentUserProvider("usr_1")
        val liveBoard = FakeLiveBoardUseCase()
        viewModel(liveBoard, user = user).test(this) {
            runOnCreate()
            containerHost.onAppear()
            val loaded = awaitBoard { it.snapshot != null }
            user.currentUserId.value = "usr_1"
            testScheduler.runCurrent()
            cancelAndIgnoreRemainingItems()
            assertEquals(loaded.accountGeneration, containerHost.container.stateFlow.value.accountGeneration)
        }
        assertEquals(1, liveBoard.liveBoardCalls.size)
    }

    @Test
    fun `내 일정 재조회 실패 시 이전 일정은 유지한다`() = runTest {
        var fail = false
        val schedule = FakeScheduleUseCase { if (fail) throw FestivalScheduleError.Unavailable() else FestivalSchedule(listOf("set_a")) }
        viewModel(schedule = schedule, user = FakeCurrentUserProvider("usr_1")).test(this) {
            containerHost.onAppear()
            awaitBoard { it.mySchedule != null }
            fail = true
            containerHost.onRetryMySchedule()
            val failed = awaitBoard { it.myScheduleUnavailable }
            assertEquals(LiveBoardMySchedule("fst", setOf("set_a")), failed.mySchedule)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `304로 같은 서버 시각을 다시 받아도 시계 기준점은 유지한다`() = runTest {
        viewModel().test(this) {
            containerHost.onAppear()
            val first = awaitBoard { it.snapshot != null }
            val anchor = first.clockAnchorAt
            clock.now = clock.now.plusSeconds(35)
            sleeper.release(FestivalLiveBoardUseCase.POLL_INTERVAL)
            val second = awaitBoard { it.receivedAt == clock.now }
            assertEquals(anchor, second.clockAnchorAt)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `내 일정 조회 실패를 표시하고 다시 시도해 복구한다`() = runTest {
        var fail = true
        val schedule = FakeScheduleUseCase { if (fail) throw FestivalScheduleError.Unavailable() else FestivalSchedule(listOf("set_a")) }
        viewModel(schedule = schedule, user = FakeCurrentUserProvider("usr_1")).test(this) {
            containerHost.onAppear()
            awaitBoard { it.myScheduleUnavailable }
            fail = false
            containerHost.onRetryMySchedule()
            val recovered = awaitBoard { it.mySchedule != null }
            assertFalse(recovered.myScheduleUnavailable)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `라이브 실패면 세 섹션 실패를 표시하고 다시 시도로 다시 조회한다`() = runTest {
        var fail = true
        val liveBoard =
            FakeLiveBoardUseCase(liveBoard = {
                if (fail) throw FestivalLiveBoardError.Unavailable()
                FakeLiveBoardUseCase.snapshot(it)
            })
        viewModel(liveBoard).test(this) {
            containerHost.onAppear()
            val failed = awaitBoard { it.isUnavailable }
            assertTrue(LiveBoardScreenModel(failed).showsOfflineStatus)
            fail = false
            containerHost.onRetryLiveBoard()
            awaitBoard { it.snapshot != null && !it.isUnavailable }
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `섹션의 로그인 · 깃발 찾기 · 크루 만들기는 Coordinator 경계로 보낸다`() = runTest {
        viewModel().test(this) {
            containerHost.onFollowLoginTapped()
            expectSideEffect(LiveBoardSideEffect.Boundary(LiveBoardBoundary.FollowLogin))
            containerHost.onFindFlagTapped()
            expectSideEffect(LiveBoardSideEffect.Boundary(LiveBoardBoundary.FlagDirectory))
            containerHost.onCrewLoginTapped()
            expectSideEffect(LiveBoardSideEffect.Boundary(LiveBoardBoundary.CrewLogin))
            // 세션 없이 크루 만들기는 로그인 요청이다.
            containerHost.onCreateCrewTapped()
            expectSideEffect(LiveBoardSideEffect.Boundary(LiveBoardBoundary.CrewLogin))
            containerHost.onNotificationTapped()
            expectSideEffect(LiveBoardSideEffect.Boundary(LiveBoardBoundary.Login))
        }
    }

    @Test
    fun `팔로우 안내 닫기는 숨김 확인을 띄우고 확인하면 이 축제 안내를 저장해 숨긴다`() = runTest {
        val preference = FakePreferenceUseCase()
        viewModel(preference = preference).test(this) {
            containerHost.onHideFollowGuideTapped()
            expectState { copy(isFollowGuideDialogPresented = true) }
            assertTrue(containerHost.container.stateFlow.value.isPresentingModal)
            containerHost.onHideFollowGuideConfirmed()
            expectState { copy(isFollowGuideDialogPresented = false, isFollowedFlagGuideHidden = true) }
        }
        assertEquals(setOf("fst"), preference.hidden)
    }

    // MARK: - 시트 · 수명 주기

    @Test
    fun `바꾸기 시트는 받은 팩 축제만 보이고 고르면 같은 보드에서 축제가 바뀌며 선택을 저장한다`() = runTest {
        val packs = FakeBoardPackUseCase(FestivalPackState(packs = mapOf("fst" to cached("fst"), "fst_b" to cached("fst_b"))))
        val preference = FakePreferenceUseCase()
        viewModel(
            packs = packs,
            preference = preference,
            catalog = listOf(item, other, item.copy(id = "fst_c", name = "팩 없음")),
        ).test(this) {
            containerHost.onAppear()
            awaitBoard { it.snapshot != null }
            containerHost.onSwitchTapped()
            val sheet = awaitBoard { it.switcher != null }
            assertEquals(setOf("fst", "fst_b"), sheet.switcher?.map { it.id }?.toSet())
            assertTrue(sheet.isPresentingModal)
            containerHost.onSwitcherFestivalChosen("fst_b")
            val switched = awaitBoard { it.festivalId == "fst_b" && it.switcher == null }
            assertEquals(LiveBoardConnection.Cached, switched.connection)
            testScheduler.runCurrent()
            cancelAndIgnoreRemainingItems()
        }
        assertEquals("fst_b", preference.savedId)
        assertEquals(listOf("fst", "fst_b"), packs.watched)
    }

    @Test
    fun `시트의 전체 페스티벌 보기는 시트를 닫고 목록으로 돌아간다`() = runTest {
        viewModel().test(this, initialState = LiveBoardState(festivalId = "fst", switcher = emptyList())) {
            containerHost.onSeeAllFestivals()
            expectState { copy(switcher = null) }
            expectSideEffect(LiveBoardSideEffect.BackRequested)
        }
    }

    @Test
    fun `백그라운드로 가면 폴링을 멈추고 포그라운드로 오면 다시 조회한다`() = runTest {
        val liveBoard = FakeLiveBoardUseCase()
        viewModel(liveBoard).test(this) {
            containerHost.onAppear()
            awaitBoard { it.snapshot != null }
            containerHost.onForegroundChanged(false)
            awaitBoard { !it.isForeground }
            testScheduler.runCurrent()
            assertEquals(1, liveBoard.liveBoardCalls.size)
            containerHost.onForegroundChanged(true)
            awaitBoard { it.isForeground }
            testScheduler.runCurrent()
            cancelAndIgnoreRemainingItems()
        }
        // 포그라운드로 돌아오면 기다리지 않고 바로 한 번 다시 부른다.
        assertEquals(2, liveBoard.liveBoardCalls.size)
    }

    // MARK: - 오프라인 · 표시 값 (iOS FestivalOfflineTests · FestivalFeatureStateTests)

    @Test
    fun `오프라인 표시는 라이브 스냅샷 없이 팩 감시 상태만으로도 보인다`() {
        val offline = LiveBoardState(festivalId = "fst", connection = LiveBoardConnection.Offline)
        assertTrue(LiveBoardScreenModel(offline).showsOfflineStatus)
        assertTrue(LiveBoardScreenModel(offline.copy(connection = LiveBoardConnection.Reconnecting)).showsOfflineStatus)
        assertFalse(LiveBoardScreenModel(offline.copy(connection = LiveBoardConnection.Cached)).showsOfflineStatus)
        // 미공개는 실패여도 배지를 보이지 않는다.
        assertFalse(
            LiveBoardScreenModel(LiveBoardState(festivalId = "fst", isUnavailable = true, isUnpublished = true)).showsOfflineStatus,
        )
    }

    @Test
    fun `팩 로딩 스켈레톤은 받은 팩을 처음 연결하는 동안에만 보인다`() {
        val waiting = LiveBoardState(festivalId = "fst", cachedPacks = mapOf("fst" to cached("fst", document = null)))
        assertTrue(waiting.isPackLoading)
        assertTrue(waiting.copy(connection = LiveBoardConnection.Connecting).isPackLoading)
        assertFalse(waiting.copy(connection = LiveBoardConnection.Reconnecting).isPackLoading)
        assertFalse(waiting.copy(connection = LiveBoardConnection.Offline, packWatchFailed = true).isPackLoading)
        assertFalse(waiting.copy(selectedPack = FestivalPackDocument(id = "fst")).isPackLoading)
        // 팩이 없으면 온라인 상세를 받는 동안만 스켈레톤이다.
        val online = LiveBoardState(festivalId = "fst", onlineDetailLoading = true)
        assertTrue(online.isPackLoading)
        assertFalse(online.copy(onlineDetailLoading = false, onlineDetailFailed = true).isPackLoading)
    }

    @Test
    fun `온라인 상세는 오프라인 팩으로 저장하지 않고 표시만 한다`() = runTest {
        val detail = FestivalPackDocument(id = "fst", name = "원더리벳 2026")
        val packs = FakeBoardPackUseCase()
        viewModel(FakeLiveBoardUseCase(detail = { detail }), packs).test(this) {
            containerHost.onAppear()
            val shown = awaitBoard { it.onlineDetail != null }
            assertEquals(detail, shown.displayDocument)
            assertTrue(shown.cachedPacks.isEmpty())
            assertEquals(LiveBoardConnection.Unavailable, shown.connection)
            cancelAndIgnoreRemainingItems()
        }
        assertTrue(packs.watched.isEmpty())
    }

    @Test
    fun `상세 실패면 시간표 실패를 표시하고 다시 시도로 다시 받는다`() = runTest {
        var fail = true
        val detail = FestivalPackDocument(id = "fst")
        viewModel(FakeLiveBoardUseCase(detail = { if (fail) throw FestivalLiveBoardError.Unavailable() else detail })).test(this) {
            containerHost.onAppear()
            awaitBoard { it.onlineDetailFailed }
            fail = false
            containerHost.onRetryOnlineDetail()
            awaitBoard { it.onlineDetail == detail && !it.onlineDetailFailed }
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `다른 축제 문서가 오면 상세 실패로 본다`() = runTest {
        viewModel(FakeLiveBoardUseCase(detail = { FestivalPackDocument(id = "other") })).test(this) {
            containerHost.onAppear()
            val failed = awaitBoard { it.onlineDetailFailed }
            assertNull(failed.onlineDetail)
            cancelAndIgnoreRemainingItems()
        }
    }

    // MARK: - 알림 배지 · 배너 (iOS FestivalNotificationBadgeTests의 보드 부분, 나머지 4건은 domain UserNotificationBadgeStoreTest)

    private val banner =
        UserNotificationItem(
            id = "new",
            type = "crew",
            festivalId = "fst",
            title = "모여! · 민지",
            body = "리벳 스테이지 앞으로 모여 주세요",
            createdAt = Instant.parse("2026-10-20T05:59:00Z"),
            readAt = null,
            route = null,
        )

    @Test
    fun notificationEntryAndAccountChange() = runTest {
        val user = FakeCurrentUserProvider("user-a")
        val badge = FakeNotificationBadgeUseCase(refreshResult = UserNotificationBadge(3, banner))
        viewModel(user = user, badge = badge).test(this) {
            runOnCreate()
            containerHost.onAppear()
            awaitBoard { it.notificationUnreadCount == 3 && it.notificationBanner == banner }
            assertEquals(1, badge.refreshCount)
            containerHost.onNotificationTapped()
            assertEquals(LiveBoardSideEffect.Boundary(LiveBoardBoundary.NotificationInbox), awaitEffect())

            user.currentUserId.value = null
            awaitBoard { !it.hasSession && it.notificationUnreadCount == 0 && it.notificationBanner == null && it.accountGeneration == 1 }
            assertEquals(1, badge.clearCount)
            containerHost.onNotificationTapped()
            assertEquals(LiveBoardSideEffect.Boundary(LiveBoardBoundary.Login), awaitEffect())
            cancelAndIgnoreRemainingItems()
        }
        // 비로그인으로 바뀌면 배지를 다시 조회하지 않는다.
        assertEquals(1, badge.refreshCount)
    }

    @Test
    fun `비로그인 보드는 배지를 조회하지 않고 공유 배지가 바뀌어도 보이지 않는다`() = runTest {
        val badge = FakeNotificationBadgeUseCase()
        viewModel(badge = badge).test(this) {
            runOnCreate()
            containerHost.onAppear()
            awaitBoard { it.isVisible }
            badge.badge.value = UserNotificationBadge(2, banner)
            testScheduler.runCurrent()
            cancelAndIgnoreRemainingItems()
            assertEquals(0, containerHost.container.stateFlow.value.notificationUnreadCount)
            assertNull(containerHost.container.stateFlow.value.notificationBanner)
        }
        assertEquals(0, badge.refreshCount)
    }

    @Test
    fun `알림함이 연 다른 축제로 보드를 바꾸고 같은 축제면 그대로 둔다`() = runTest {
        viewModel().test(this) {
            runOnCreate()
            containerHost.onAppear()
            awaitBoard { it.isVisible }
            containerHost.onNotificationFestivalRequested("fst")
            containerHost.onNotificationFestivalRequested("fst_b")
            awaitBoard { it.festivalId == "fst_b" }
            cancelAndIgnoreRemainingItems()
        }
    }

    // MARK: - 타임테이블 공유 (설계 T1 · T2)

    @Test
    fun `보드는 선택 축제와 표시 문서 · 연결 · 팩 확인 시각을 공유 상태에 쓴다`() = runTest {
        val checkedAt = Instant.parse("2026-10-20T05:58:00Z")
        val document = FestivalPackDocument(id = "fst", name = "원더리벳 2026")
        val packs =
            FakeBoardPackUseCase(
                FestivalPackState(packs = mapOf("fst" to CachedFestivalPack("fst", 1, "v1", checkedAt = checkedAt, document = document))),
            )
        val selection = FakeBoardSelectionUseCase()
        viewModel(packs = packs, selection = selection).test(this) {
            runOnCreate()
            containerHost.onAppear()
            awaitBoard { it.selectedPack == document }
            testScheduler.runCurrent()
            val shared = selection.selection.value
            assertEquals("fst", shared?.festivalId)
            assertEquals(document, shared?.document)
            assertEquals(checkedAt, shared?.packCheckedAt)
            assertEquals(FestivalBoardSelection.Connection.Cached, shared?.connection)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `다른 축제의 문서는 공유하지 않는다`() {
        val state = LiveBoardState(festivalId = "fst", onlineDetail = FestivalPackDocument(id = "fst_b"))

        assertNull(state.toBoardSelection().document)
    }

    @Test
    fun `타임테이블이 저장한 같은 축제의 내 일정을 보드에 반영하고 읽던 요청은 버린다`() = runTest {
        val late = CompletableDeferred<FestivalSchedule>()
        val schedule = FakeScheduleUseCase { late.await() }
        viewModel(schedule = schedule, user = FakeCurrentUserProvider("usr_1")).test(this) {
            runOnCreate()
            containerHost.onAppear()
            val loading = awaitBoard { it.myScheduleRequestId == 1 }
            schedule.notifyMyScheduleSaved(FestivalScheduleSaved("fst", setOf("set_saved")))
            val saved = awaitBoard { it.mySchedule != null }
            assertEquals(LiveBoardMySchedule("fst", setOf("set_saved")), saved.mySchedule)
            assertEquals(loading.myScheduleRequestId + 1, saved.myScheduleRequestId)
            late.complete(FestivalSchedule(listOf("set_old")))
            testScheduler.runCurrent()
            cancelAndIgnoreRemainingItems()
            assertEquals(LiveBoardMySchedule("fst", setOf("set_saved")), containerHost.container.stateFlow.value.mySchedule)
        }
    }

    @Test
    fun `다른 축제나 비로그인이면 저장 알림을 무시한다`() = runTest {
        val schedule = FakeScheduleUseCase()
        viewModel(schedule = schedule).test(this) {
            runOnCreate()
            testScheduler.runCurrent()
            schedule.notifyMyScheduleSaved(FestivalScheduleSaved("fst", setOf("set_a")))
            testScheduler.runCurrent()
            assertNull(containerHost.container.stateFlow.value.mySchedule)
            cancelAndIgnoreRemainingItems()
        }
        val user = FakeCurrentUserProvider("usr_1")
        viewModel(schedule = schedule, user = user).test(this) {
            runOnCreate()
            testScheduler.runCurrent()
            schedule.notifyMyScheduleSaved(FestivalScheduleSaved("fst_b", setOf("set_b")))
            testScheduler.runCurrent()
            assertNull(containerHost.container.stateFlow.value.mySchedule)
            cancelAndIgnoreRemainingItems()
        }
    }
}
