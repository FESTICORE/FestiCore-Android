package io.festicore.feature.festival.flagdetail

import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerCoBearer
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerOwnedFlag
import io.festicore.domain.bearer.testing.FakeBearerUseCase
import io.festicore.domain.festival.entity.FestivalFlagFollowError
import io.festicore.feature.festival.RecordingAnalyticsUseCase
import io.festicore.feature.festival.flagdetail.FlagDetailFixtures.NOW
import io.festicore.feature.festival.liveboard.FakeFlagUseCase
import io.festicore.feature.festival.liveboard.TestSleeper
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * 깃발 관리 메뉴(공유 · 다른 페스티벌로 옮기기)와 옮긴 뒤 다시 불러오기. Android 먼저(백엔드 #409).
 * 메뉴는 내 깃발 · 원래 기수 · 공동 기수 없음일 때만 보인다.
 */
class FlagDetailMenuTest {
    private val bearerDetail = FlagDetailFixtures.detail().copy(isBearer = true)
    private val eligibleState = FlagDetailFixtures.loaded(bearerDetail).copy(canTransferFestival = true)

    private val profile = BearerFlagProfile(id = "flg_1", name = "호락호락")

    private fun owned(isOwner: Boolean = true) = BearerOwnedFlag(
        festivalId = "fst_1",
        isOwner = isOwner,
        joinedAt = null,
        profile = profile,
        lifecycle = BearerFlagLifecycle.Scheduled,
        isHidden = false,
    )

    private fun board(bearers: Int) = BearerBoard(
        profile = profile,
        status = BearerFlagStatus("flg_1", BearerFlagLifecycle.Scheduled, null, null, NOW),
        followerCount = 0,
        coBearers = List(bearers) { BearerCoBearer("usr_$it", "기수$it") },
    )

    private fun bearerUseCase(isOwner: Boolean = true, bearers: Int = 1) =
        FakeBearerUseCase(onFetchBearerFlags = { listOf(owned(isOwner)) }, onFetchBoard = { board(bearers) })

    private fun viewModel(
        detailUseCase: FakeFlagDetailUseCase = FakeFlagDetailUseCase(detail = { bearerDetail }),
        bearerUseCase: FakeBearerUseCase = bearerUseCase(),
        sleeper: TestSleeper = TestSleeper(),
    ) = FlagDetailViewModel(
        flagId = "flg_1",
        context = FlagDetailFixtures.context(),
        detailUseCase = detailUseCase,
        flagUseCase = FakeFlagUseCase(setFollowing = { _, _ -> throw FestivalFlagFollowError.Unavailable() }),
        analyticsUseCase = RecordingAnalyticsUseCase(),
        bearerUseCase = bearerUseCase,
        clock = MutableClock(NOW),
        sleep = sleeper::sleep,
    )

    @Test
    fun ownerWithoutCoBearersGetsMenu() = runTest {
        viewModel().test(this, initialState = FlagDetailFixtures.state()) {
            containerHost.onAppeared()
            expectState { copy(loadGeneration = 1) }
            expectState { copy(content = FlagDetailContent.Loaded(bearerDetail), lastLoadedAt = NOW) }
            expectState { copy(canTransferFestival = true) }
            containerHost.onDisappeared()
            expectState { copy(loadGeneration = 2) }
        }
    }

    /** 공동 기수 · 원래 기수 아님 · 조회 실패 · 기수 아님이면 메뉴를 두지 않는다(상단 바는 공유 그대로). */
    @Test
    fun noMenuUnlessOwnerWithoutCoBearers() = runTest {
        listOf(
            bearerDetail to bearerUseCase(bearers = 2),
            bearerDetail to bearerUseCase(isOwner = false),
            bearerDetail to FakeBearerUseCase(onFetchBearerFlags = { throw BearerError.Offline() }),
            FlagDetailFixtures.detail() to FakeBearerUseCase(),
        ).forEach { (detail, useCase) ->
            viewModel(FakeFlagDetailUseCase(detail = { detail }), useCase).test(this, initialState = FlagDetailFixtures.state()) {
                containerHost.onAppeared()
                expectState { copy(loadGeneration = 1) }
                expectState { copy(content = FlagDetailContent.Loaded(detail), lastLoadedAt = NOW) }
                containerHost.onMenuTapped()
                containerHost.onDisappeared()
                expectState { copy(loadGeneration = 2) }
            }
        }
    }

    @Test
    fun menuRequestsFestivalTransfer() = runTest {
        viewModel().test(this, initialState = eligibleState) {
            containerHost.onMenuTapped()
            expectState { copy(isMenuPresented = true) }
            containerHost.onMenuTransferTapped()
            expectState { copy(isMenuPresented = false) }
            expectSideEffect(FlagDetailSideEffect.FestivalTransferRequested(flagId = "flg_1"))
        }
    }

    @Test
    fun menuShareClosesAndShares() = runTest {
        viewModel().test(this, initialState = eligibleState) {
            containerHost.onMenuTapped()
            expectState { copy(isMenuPresented = true) }
            containerHost.onMenuShareTapped()
            expectState { copy(isMenuPresented = false) }
            expectSideEffect(FlagDetailSideEffect.ShareRequested(url = "festicore://flag/flg_1", title = "호락호락"))
            containerHost.onMenuTapped()
            expectState { copy(isMenuPresented = true) }
            containerHost.onMenuDismissed()
            expectState { copy(isMenuPresented = false) }
        }
    }

    @Test
    fun transferredReloadsDetailAndShowsToast() = runTest {
        val sleeper = TestSleeper()
        val moved = bearerDetail.copy(planSetIds = emptyList(), homeZoneId = null, zoneId = null)
        viewModel(FakeFlagDetailUseCase(detail = { moved }), sleeper = sleeper).test(this, initialState = eligibleState) {
            containerHost.onFestivalTransferred()
            expectState { copy(loadGeneration = 1) }
            expectState { copy(toast = FlagDetailToast.FestivalTransferred) }
            expectState { copy(content = FlagDetailContent.Loaded(moved), lastLoadedAt = NOW) }
            sleeper.release(2.seconds)
            expectState { copy(toast = null) }
        }
    }
}
