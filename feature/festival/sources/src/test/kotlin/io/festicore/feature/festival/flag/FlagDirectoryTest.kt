package io.festicore.feature.festival.flag

import io.festicore.domain.festival.FestivalFlagFollowError
import io.festicore.domain.festival.FestivalFlagFollowResult
import io.festicore.domain.festival.FestivalLiveBoardError
import io.festicore.domain.festival.FestivalLiveBoardSnapshot
import io.festicore.feature.festival.flag.FlagFixtures.board
import io.festicore.feature.festival.flag.FlagFixtures.flag
import io.festicore.feature.festival.flag.FlagFixtures.loadedState
import io.festicore.feature.festival.liveboard.FakeFlagUseCase
import io.festicore.feature.festival.liveboard.FakeLiveBoardUseCase
import io.festicore.feature.festival.liveboard.TestSleeper
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `FestivalFlagDirectoryTests`. 순수 함수는 [FlagDirectory] · [FlagPresentation], 흐름은 [FlagViewModel]로 옮겼다. flagFollow 분석 확인은 뺐다. */
class FlagDirectoryTest {
    private fun viewModel(
        follow: FakeFlagUseCase = FakeFlagUseCase(),
        liveBoard: FakeLiveBoardUseCase = FakeLiveBoardUseCase(liveBoard = { awaitCancellation() }),
        sleeper: TestSleeper = TestSleeper(),
    ) = FlagViewModel(liveBoardUseCase = liveBoard, flagUseCase = follow, festivalId = "festival", sleep = sleeper::sleep)

    /** 차례로 돌려주는 조회. null이면 형식 오류, [firstError]가 있으면 첫 번째 대신 던진다(iOS `SequenceLiveBoardService`). */
    private fun sequence(snapshots: List<FestivalLiveBoardSnapshot?>, firstError: Exception? = null): FakeLiveBoardUseCase {
        val queue = ArrayDeque(snapshots)
        var error = firstError
        return FakeLiveBoardUseCase(liveBoard = {
            val next = queue.removeFirst()
            error?.let {
                error = null
                throw it
            }
            next ?: throw FestivalLiveBoardError.InvalidResponse()
        })
    }

    private fun rows(
        state: FlagState,
        snapshot: FestivalLiveBoardSnapshot,
        isFailed: Boolean = false,
    ) = FlagPresentation.rows(state.directory, snapshot, isFailed)

    private fun override(
        isFollowing: Boolean?,
        followerCount: Int?,
        baseCount: Int?,
        skipsNextLoad: Boolean = false,
    ) = FlagFollowOverride(isFollowing, followerCount, baseCount, skipsNextLoad)

    private fun FlagState.withDirectory(transform: FlagDirectoryState.() -> FlagDirectoryState) = copy(directory = directory.transform())

    // MARK: - 표시 행 · 정렬

    @Test
    fun activeSortFollowsFigmaOrderWithUnknownStatusBeforeScheduled() {
        val snapshot =
            board(
                listOf(
                    flag("ended", status = "ended", count = 97),
                    flag("scheduled", status = "scheduled", count = 213),
                    flag("resting", status = "resting", count = 540),
                    flag("unknown", status = "unknown", count = 300),
                    flag("active-small", status = "active", count = 862),
                    flag("active-big", status = "active", count = 1284),
                ),
            )

        val rows = FlagPresentation.rows(FlagDirectoryState(), snapshot, isFailed = false)

        assertEquals(listOf("active-big", "active-small", "resting", "unknown", "scheduled", "ended"), rows.map { it.id })
        assertEquals(
            listOf(FlagStatus.Active, FlagStatus.Active, FlagStatus.Resting, null, FlagStatus.Scheduled, FlagStatus.EndedToday),
            rows.map { it.status },
        )
    }

    @Test
    fun followerAndRecentSortsKeepServerOrderForTies() {
        val early = Instant.ofEpochSecond(100)
        val late = Instant.ofEpochSecond(200)
        val snapshot =
            board(
                listOf(
                    flag("a", count = 10, updatedAt = early),
                    flag("b", count = 30),
                    flag("c", count = 10, updatedAt = late),
                    flag("d", count = 30, updatedAt = early),
                ),
            )

        assertEquals(
            listOf("b", "d", "a", "c"),
            FlagPresentation.rows(FlagDirectoryState(sort = FlagSort.Followers), snapshot, false).map {
                it.id
            },
        )
        assertEquals(
            listOf("c", "a", "d", "b"),
            FlagPresentation.rows(FlagDirectoryState(sort = FlagSort.Recent), snapshot, false).map {
                it.id
            },
        )
    }

    @Test
    fun rowShowsTagsFollowersAndZoneNames() {
        val snapshot =
            board(
                listOf(
                    flag("live", tags = listOf("slam", "jump", "newbie"), count = 1284, zone = "zone_live", home = "zone_home"),
                    flag("home", tags = listOf("sing"), home = "zone_home"),
                    flag("nowhere", tags = listOf("slam"), zone = "zone_unknown"),
                ),
                followed = listOf("home"),
            )
        val state = FlagDirectoryState(zoneNames = mapOf("zone_live" to "리벳 스테이지 앞 왼쪽", "zone_home" to "원더 스테이지 앞"))

        val rows = FlagPresentation.rows(state, snapshot, false)

        assertEquals(listOf("리벳 스테이지 앞 왼쪽", "원더 스테이지 앞", "위치 미정"), rows.map { it.zone })
        assertEquals("팔로워 1,284", rows.first().followersText)
        assertEquals(listOf("슬램", "초보 환영"), rows.first().tags)
        assertEquals(listOf(false, true, false), rows.map { it.isFollowing })
        assertEquals(listOf("live", "nowhere"), FlagPresentation.rows(state.copy(tag = FlagTag.Slam), snapshot, false).map { it.id })
    }

    @Test
    fun refreshFailureAndPendingRequestDisableFollow() {
        val snapshot = board(listOf(flag("a"), flag("b")))
        val state = FlagDirectoryState(pendingFollows = mapOf("a" to null))

        assertEquals(listOf(false, true), FlagPresentation.rows(state, snapshot, isFailed = false).map { it.isFollowEnabled })
        assertEquals(listOf(false, false), FlagPresentation.rows(state, snapshot, isFailed = true).map { it.isFollowEnabled })
    }

    @Test
    fun offlineTextUsesLastConfirmationTime() {
        val now = Instant.ofEpochSecond(1000)
        assertEquals("오프라인 · 3분 전 정보", FlagPresentation.offlineText(now.minusSeconds(180), now))
        assertEquals("오프라인 · 방금 전 정보", FlagPresentation.offlineText(now.minusSeconds(30), now))
        assertEquals("오프라인 · 방금 전 정보", FlagPresentation.offlineText(now.plusSeconds(60), now))
        assertEquals("오프라인 · 방금 전 정보", FlagPresentation.offlineText(null, now))
    }

    // MARK: - 팔로우

    @Test
    fun followSuccessOverridesSnapshotAndIgnoresRepeatedTap() = runTest {
        val snapshot = board(listOf(flag("a", count = 10)))
        val gate = CompletableDeferred<Unit>()
        val follow =
            FakeFlagUseCase(setFollowing = { following, id ->
                gate.await()
                FestivalFlagFollowResult(id, following, 11)
            })
        viewModel(follow).test(this, initialState = loadedState(snapshot)) {
            containerHost.onFollowTapped("a", follow = true, baseCount = 10)
            expectState { withDirectory { copy(pendingFollows = mapOf("a" to 10)) } }
            containerHost.onFollowTapped("a", follow = true, baseCount = 10)
            // 인텐트는 부른 순서대로 돈다. 표시용 인텐트가 반영되면 앞선 두 번째 누르기는 이미 무시됐다.
            containerHost.onSortTapped()
            expectState { withDirectory { copy(isSortMenuPresented = true) } }
            gate.complete(Unit)
            val state = awaitState()
            assertEquals(
                FlagDirectoryState(isSortMenuPresented = true, followOverrides = mapOf("a" to override(true, 11, 10))),
                state.directory,
            )
            val row = rows(state, snapshot).first()
            assertEquals(true, row.isFollowing)
            assertEquals("팔로워 11", row.followersText)
            assertEquals(listOf(true to "a"), follow.calls)
        }
    }

    @Test
    fun `카드를 누르면 부모에 깃발 상세를 요청한다`() = runTest {
        viewModel().test(this, initialState = loadedState(board(listOf(flag("a"))))) {
            containerHost.onCardTapped("a")
            expectSideEffect(FlagSideEffect.DetailRequested("a"))
        }
    }

    @Test
    fun `상세에서 바꾼 팔로우는 디렉토리 팔로우 성공처럼 카드에 보인다`() = runTest {
        val snapshot = board(listOf(flag("a", count = 10)))
        viewModel().test(this, initialState = loadedState(snapshot)) {
            containerHost.onFollowChangedElsewhere(FestivalFlagFollowResult("a", isFollowing = true, followerCount = 11), baseCount = 10)
            val state = awaitState()
            assertEquals(mapOf("a" to override(true, 11, 10)), state.directory.followOverrides)
            val row = rows(state, snapshot).first()
            assertEquals(true, row.isFollowing)
            assertEquals("팔로워 11", row.followersText)
        }
    }

    @Test
    fun loggedOutFollowAsksAppToShowLogin() = runTest {
        val follow = FakeFlagUseCase(setFollowing = { _, _ -> throw FestivalFlagFollowError.LoginRequired() })
        viewModel(follow).test(this, initialState = loadedState(board(listOf(flag("a"))))) {
            containerHost.onFollowTapped("a", follow = true, baseCount = null)
            expectState { withDirectory { copy(pendingFollows = mapOf("a" to null)) } }
            expectState { withDirectory { copy(pendingFollows = emptyMap()) } }
            expectSideEffect(FlagSideEffect.LoginRequested)
        }
    }

    @Test
    fun termsRequiredFollowRequestsConsentFlow() = runTest {
        val follow = FakeFlagUseCase(setFollowing = { _, _ -> throw FestivalFlagFollowError.TermsRequired() })
        viewModel(follow).test(this, initialState = loadedState(board(listOf(flag("a"))))) {
            containerHost.onFollowTapped("a", follow = true, baseCount = null)
            expectState { withDirectory { copy(pendingFollows = mapOf("a" to null)) } }
            expectState { withDirectory { copy(pendingFollows = emptyMap()) } }
            expectSideEffect(FlagSideEffect.TermsAcceptanceRequested)
        }
    }

    @Test
    fun failureShowsToastForTwoSeconds() = runTest {
        listOf(
            FestivalFlagFollowError.LimitReached() to "팔로우는 100개까지 할 수 있어요",
            FestivalFlagFollowError.Unavailable() to "팔로우를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요",
        ).forEach { (error, message) ->
            val sleeper = TestSleeper()
            val follow = FakeFlagUseCase(setFollowing = { _, _ -> throw error })
            viewModel(follow, sleeper = sleeper).test(this, initialState = loadedState(board(listOf(flag("a"))))) {
                containerHost.onFollowTapped("a", follow = true, baseCount = null)
                expectState { withDirectory { copy(pendingFollows = mapOf("a" to null)) } }
                expectState { withDirectory { copy(pendingFollows = emptyMap(), toast = message) } }
                sleeper.release(2.seconds)
                expectState { withDirectory { copy(toast = null) } }
                assertEquals(listOf(2.seconds), sleeper.requested)
            }
        }
    }

    // MARK: - 스냅샷 반영 (reconcile)

    @Test
    fun followFinishedDuringLoadSurvivesThatLoadUntilLaterConfirmation() = runTest {
        val before = board(listOf(flag("a", count = 10)))
        val followedButCached = board(listOf(flag("a", count = 10)), followed = listOf("a"))
        val fresh = board(listOf(flag("a", count = 12)), followed = listOf("a"))
        val follow = FakeFlagUseCase(setFollowing = { _, id -> FestivalFlagFollowResult(id, true, 11) })
        viewModel(follow, sequence(listOf(followedButCached, fresh))).test(
            this,
            initialState = loadedState(before).copy(viewState = FlagViewState.Loading),
        ) {
            containerHost.onFollowTapped("a", follow = true, baseCount = 10)
            expectState { withDirectory { copy(pendingFollows = mapOf("a" to 10)) } }
            expectState {
                withDirectory {
                    copy(pendingFollows = emptyMap(), followOverrides = mapOf("a" to override(true, 11, 10, skipsNextLoad = true)))
                }
            }
            containerHost.onLoaded("festival", 0, before)
            val loaded = awaitState()
            assertEquals(FlagViewState.Loaded, loaded.viewState)
            assertEquals(mapOf("a" to override(true, 11, 10)), loaded.directory.followOverrides)
            assertEquals(true, rows(loaded, before).first().isFollowing)

            containerHost.refresh()
            expectState { copy(viewState = FlagViewState.Loading) }
            val cached = awaitState()
            assertEquals(
                loaded.copy(snapshot = followedButCached).withDirectory {
                    copy(followOverrides = mapOf("a" to override(null, 11, 10)))
                },
                cached,
            )
            assertEquals("팔로워 11", rows(cached, followedButCached).first().followersText)

            containerHost.refresh()
            expectState { copy(viewState = FlagViewState.Loading) }
            expectState { copy(snapshot = fresh, viewState = FlagViewState.Loaded).withDirectory { copy(followOverrides = emptyMap()) } }
        }
    }

    @Test
    fun rejectedLoadDoesNotClearFollowResult() = runTest {
        val initial =
            loadedState(board(listOf(flag("a"))))
                .copy(viewState = FlagViewState.Loading)
                .withDirectory { copy(followOverrides = mapOf("a" to override(true, 1, 0))) }
        viewModel().test(this, initialState = initial) {
            containerHost.onLoaded("another-festival", 0, board(listOf(flag("a"))))
        }
    }

    @Test
    fun staleRefreshKeepsFollowResultUntilSnapshotConfirmsIt() = runTest {
        listOf(true, false).forEach { isFollowing ->
            val stale = board(listOf(flag("a", count = 10)), followed = if (isFollowing) emptyList() else listOf("a"))
            val confirmed =
                board(listOf(flag("a", count = if (isFollowing) 11 else 9)), followed = if (isFollowing) listOf("a") else emptyList())
            val initial = loadedState(stale).withDirectory {
                copy(followOverrides = mapOf("a" to override(isFollowing, if (isFollowing) 11 else 9, 10)))
            }
            viewModel(liveBoard = sequence(listOf(stale, confirmed))).test(this, initialState = initial) {
                containerHost.refresh()
                expectState { copy(viewState = FlagViewState.Loading) }
                val staleState = awaitState()
                assertEquals(initial, staleState)
                assertEquals(isFollowing, rows(staleState, stale).first().isFollowing)

                containerHost.refresh()
                expectState { copy(viewState = FlagViewState.Loading) }
                val confirmedState = awaitState()
                assertEquals(initial.copy(snapshot = confirmed).withDirectory { copy(followOverrides = emptyMap()) }, confirmedState)
                assertEquals(isFollowing, rows(confirmedState, confirmed).first().isFollowing)
            }
        }
    }

    @Test
    fun rapidFollowThenUnfollowKeepsFinalCountThroughIntermediateCache() = runTest {
        val original = board(listOf(flag("a", count = 10)))
        val intermediate = board(listOf(flag("a", count = 11)))
        val confirmed = board(listOf(flag("a", count = 10)))
        val toggle = FakeFlagUseCase(setFollowing = { following, id -> FestivalFlagFollowResult(id, following, if (following) 11 else 10) })
        viewModel(toggle, sequence(listOf(intermediate, confirmed))).test(this, initialState = loadedState(original)) {
            containerHost.onFollowTapped("a", follow = true, baseCount = 10)
            expectState { withDirectory { copy(pendingFollows = mapOf("a" to 10)) } }
            expectState { withDirectory { copy(pendingFollows = emptyMap(), followOverrides = mapOf("a" to override(true, 11, 10))) } }
            containerHost.onFollowTapped("a", follow = false, baseCount = 10)
            expectState { withDirectory { copy(pendingFollows = mapOf("a" to 11)) } }
            expectState { withDirectory { copy(pendingFollows = emptyMap(), followOverrides = mapOf("a" to override(false, 10, 11))) } }

            containerHost.refresh()
            expectState { copy(viewState = FlagViewState.Loading) }
            val intermediateState = awaitState()
            assertEquals(mapOf("a" to override(null, 10, 11)), intermediateState.directory.followOverrides)
            assertEquals("팔로워 10", rows(intermediateState, intermediate).first().followersText)

            containerHost.refresh()
            expectState { copy(viewState = FlagViewState.Loading) }
            val confirmedState = awaitState()
            assertEquals(emptyMap<String, FlagFollowOverride>(), confirmedState.directory.followOverrides)
            assertEquals("팔로워 10", rows(confirmedState, confirmed).first().followersText)
        }
    }

    @Test
    fun leavingTabCancelsRequestButKeepsFollowResultUntilConfirmed() = runTest {
        val snapshot = board(listOf(flag("a"), flag("b")))
        val initial = loadedState(snapshot).withDirectory { copy(followOverrides = mapOf("a" to override(true, 3, 2))) }
        val follow = FakeFlagUseCase(setFollowing = { _, _ -> awaitCancellation() })
        val liveBoard = FakeLiveBoardUseCase(liveBoard = { throw FestivalLiveBoardError.InvalidResponse() })
        viewModel(follow, liveBoard).test(this, initialState = initial) {
            containerHost.onFollowTapped("b", follow = true, baseCount = null)
            expectState { withDirectory { copy(pendingFollows = mapOf("b" to null)) } }
            containerHost.onDisappeared()
            expectState { copy(isVisible = false).withDirectory { copy(pendingFollows = emptyMap()) } }

            containerHost.onAppeared()
            expectState { copy(isVisible = true) }
            expectState { copy(viewState = FlagViewState.Loading) }
            val failed = awaitState()
            assertEquals(FlagViewState.InvalidResponse, failed.viewState)
            val row = rows(failed, snapshot, isFailed = true).first()
            assertEquals(true, row.isFollowing)
            assertEquals(false, row.isFollowEnabled)
        }
    }

    @Test
    fun festivalAndAccountChangesDropFollowResults() = runTest {
        val initial =
            loadedState(board(listOf(flag("a"))))
                .copy(isVisible = false)
                .withDirectory { copy(followOverrides = mapOf("a" to override(true, 3, 2)), toast = "팔로우를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요") }
        viewModel().test(this, initialState = initial) {
            containerHost.onAccountChanged()
            expectState {
                copy(snapshot = null, viewState = FlagViewState.Idle, generation = 1).withDirectory {
                    copy(followOverrides = emptyMap(), toast = null)
                }
            }
        }

        val other = loadedState(board(listOf(flag("a")))).copy(isVisible = false).withDirectory {
            copy(followOverrides = mapOf("a" to override(false, 1, 2)))
        }
        viewModel().test(this, initialState = other) {
            containerHost.onFestivalChanged("another")
            expectState {
                copy(festivalId = "another", snapshot = null, viewState = FlagViewState.Idle, generation = 1).withDirectory {
                    copy(followOverrides = emptyMap())
                }
            }
        }
    }

    // MARK: - 조회 제한

    @Test
    fun rateLimitKeepsPreviousFlagsAndRetriesAfterWaiting() = runTest {
        val previous = board(listOf(flag("a")))
        val next = board(listOf(flag("a"), flag("b")))
        val sleeper = TestSleeper()
        val liveBoard = sequence(listOf(null, next), firstError = FestivalLiveBoardError.RateLimited(30.seconds))
        viewModel(liveBoard = liveBoard, sleeper = sleeper).test(this, initialState = loadedState(previous)) {
            containerHost.refresh()
            expectState { copy(viewState = FlagViewState.Loading) }
            sleeper.release(30.seconds)
            expectState { copy(snapshot = next, viewState = FlagViewState.Loaded) }
            assertEquals(listOf(30.seconds), sleeper.requested)
        }
    }
}
