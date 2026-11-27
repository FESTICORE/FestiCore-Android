package io.festicore.feature.festival.flag

import io.festicore.domain.festival.entity.FestivalLiveBoardError
import io.festicore.domain.festival.entity.FestivalLiveBoardSnapshot
import io.festicore.feature.festival.RecordingAnalyticsUseCase
import io.festicore.feature.festival.liveboard.FakeFlagUseCase
import io.festicore.feature.festival.liveboard.FakeLiveBoardUseCase
import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.AnalyticsScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.orbitmvi.orbit.test.test

/** iOS `FestivalFlagFeatureTests`. screen_view 분석 확인은 Android feature에 분석 경로가 없어 뺐다. */
class FlagViewModelTest {
    private val analytics = RecordingAnalyticsUseCase()

    private fun viewModel(
        liveBoard: FakeLiveBoardUseCase = FakeLiveBoardUseCase(liveBoard = { awaitCancellation() }),
        festivalId: String? = "festival",
    ) = FlagViewModel(
        liveBoardUseCase = liveBoard,
        flagUseCase = FakeFlagUseCase(),
        analyticsUseCase = analytics,
        festivalId = festivalId,
        sleep = { awaitCancellation() },
    )

    private fun snapshot(festivalId: String, followed: List<String> = emptyList()) = FestivalLiveBoardSnapshot(
        festivalId = festivalId,
        activeFlags = listOf(FlagFixtures.flag("flag", zone = "A")),
        followedFlags = followed.map { FlagFixtures.flag(it) },
        crew = null,
    )

    @Test
    fun prepFollowTimeoutClearsPendingAndShowsFailureWithoutTrackingFlagScreen() = runTest {
        val vm = FlagViewModel(
            liveBoardUseCase = FakeLiveBoardUseCase(liveBoard = { snapshot("festival") }),
            flagUseCase = FakeFlagUseCase(setFollowing = { _, _ -> awaitCancellation() }),
            analyticsUseCase = analytics,
            festivalId = "festival",
            tracksScreenView = false,
            followTimeoutMillis = 15_000,
            sleep = { awaitCancellation() },
        )
        vm.test(this) {
            containerHost.onAppeared()
            awaitState()
            awaitState()
            awaitState()
            containerHost.onFollowTapped("flag", true, 1)
            org.junit.Assert.assertTrue(awaitState().directory.isPending("flag"))
            testScheduler.advanceTimeBy(15_000)
            testScheduler.runCurrent()
            val failed = awaitState()
            org.junit.Assert.assertTrue(failed.directory.pendingFollows.isEmpty())
            org.junit.Assert.assertNotNull(failed.directory.toast)
            cancelAndIgnoreRemainingItems()
        }
        org.junit.Assert.assertTrue(analytics.events.none { it is AnalyticsEvent.ScreenView })
    }

    @Test
    fun selectedFestivalLoadsItsFlags() = runTest {
        val snapshot = snapshot("festival-a")
        viewModel(FakeLiveBoardUseCase(liveBoard = { snapshot }), festivalId = "festival-a").test(this) {
            containerHost.onAppeared()
            expectState { copy(isVisible = true) }
            expectState { copy(viewState = FlagViewState.Loading) }
            expectState { copy(snapshot = snapshot, viewState = FlagViewState.Loaded) }
        }
        assertEquals(listOf(AnalyticsEvent.ScreenView(AnalyticsScreen.FestivalFlag)), analytics.events)
    }

    @Test
    fun parentZoneNamesReachDirectoryZoneChips() = runTest {
        viewModel().test(this) {
            containerHost.onZoneNamesChanged(mapOf("zone_a" to "리벳 스테이지 앞 왼쪽"))
            expectState { copy(directory = directory.copy(zoneNames = mapOf("zone_a" to "리벳 스테이지 앞 왼쪽"))) }
            containerHost.onZoneNamesChanged(emptyMap())
            expectState { copy(directory = directory.copy(zoneNames = emptyMap())) }
        }
    }

    @Test
    fun festivalChangeClearsPreviousFlagsBeforeLoadingNewFestival() = runTest {
        val old = snapshot("old")
        viewModel().test(this, initialState = FlagState(festivalId = "old", snapshot = old, viewState = FlagViewState.Loaded)) {
            containerHost.onFestivalChanged("new")
            expectState { copy(festivalId = "new", snapshot = null, viewState = FlagViewState.Idle, generation = 1) }
            containerHost.onLoaded("old", 0, old)
            containerHost.onFailed("old", 0, FlagLoadFailure.Unavailable)
        }
    }

    @Test
    fun invalidResponseKeepsExistingFlagsForRecoveryDisplay() = runTest {
        val cached = snapshot("festival")
        val liveBoard = FakeLiveBoardUseCase(liveBoard = { throw FestivalLiveBoardError.InvalidResponse() })
        viewModel(liveBoard).test(this, initialState = FlagFixtures.loadedState(cached)) {
            containerHost.refresh()
            expectState { copy(viewState = FlagViewState.Loading) }
            expectState { copy(viewState = FlagViewState.InvalidResponse, snapshot = cached) }
        }
    }

    /** Android 라이브 보드 오류에는 기기 미등록이 없어, 같은 실패 값을 직접 보내 표시 상태만 확인한다. */
    @Test
    fun missingDeviceRegistrationShowsRecoveryState() = runTest {
        viewModel().test(this, initialState = FlagState(festivalId = "festival", viewState = FlagViewState.Loading, isVisible = true)) {
            containerHost.onFailed("festival", 0, FlagLoadFailure.DeviceNotRegistered)
            expectState { copy(viewState = FlagViewState.DeviceNotRegistered) }
        }
    }

    @Test
    fun contractErrorsAreNotShownAsNetworkFailures() = runTest {
        listOf(
            FestivalLiveBoardError.InvalidFestivalId() to FlagViewState.InvalidFestivalId,
            FestivalLiveBoardError.InvalidResponse() to FlagViewState.InvalidResponse,
        ).forEach { (error, expected) ->
            viewModel(FakeLiveBoardUseCase(liveBoard = { throw error })).test(this) {
                containerHost.onAppeared()
                expectState { copy(isVisible = true) }
                expectState { copy(viewState = FlagViewState.Loading) }
                expectState { copy(viewState = expected) }
            }
        }
    }

    @Test
    fun accountChangeWhileHiddenClearsFlagsWithoutLoading() = runTest {
        viewModel().test(
            this,
            initialState = FlagState(festivalId = "festival", snapshot = snapshot("festival"), viewState = FlagViewState.Loaded),
        ) {
            containerHost.onAccountChanged()
            expectState { copy(snapshot = null, viewState = FlagViewState.Idle, generation = 1) }
        }
    }

    @Test
    fun accountChangeWhileVisibleDropsPreviousAccountResponseAndReloads() = runTest {
        val previous = snapshot("festival", followed = listOf("followed"))
        val current = snapshot("festival")
        var signedIn = true
        val started = CompletableDeferred<Unit>()
        val liveBoard =
            FakeLiveBoardUseCase(liveBoard = {
                if (signedIn) {
                    started.complete(Unit)
                    awaitCancellation()
                }
                current
            })
        viewModel(liveBoard).test(this, initialState = FlagFixtures.loadedState(previous)) {
            containerHost.refresh()
            expectState { copy(viewState = FlagViewState.Loading) }
            started.await()
            signedIn = false
            containerHost.onAccountChanged()
            expectState { copy(snapshot = null, viewState = FlagViewState.Idle, generation = 1) }
            expectState { copy(viewState = FlagViewState.Loading) }
            expectState { copy(snapshot = current, viewState = FlagViewState.Loaded) }
            containerHost.onLoaded("festival", 0, previous)
            containerHost.onFailed("festival", 0, FlagLoadFailure.Unavailable)
        }
    }

    @Test
    fun previousFestivalGenerationIsIgnoredAfterReturningToSameFestival() = runTest {
        val previous = snapshot("festival", followed = listOf("previous"))
        val current = snapshot("festival")
        viewModel(FakeLiveBoardUseCase(liveBoard = { current })).test(this) {
            containerHost.onFestivalChanged("other")
            expectState { copy(festivalId = "other", generation = 1) }
            containerHost.onFestivalChanged("festival")
            expectState { copy(festivalId = "festival", generation = 2) }
            containerHost.onAppeared()
            expectState { copy(isVisible = true) }
            expectState { copy(viewState = FlagViewState.Loading) }
            expectState { copy(snapshot = current, viewState = FlagViewState.Loaded) }
            containerHost.onLoaded("festival", 0, previous)
            containerHost.onFailed("festival", 0, FlagLoadFailure.Unavailable)
        }
    }
}
