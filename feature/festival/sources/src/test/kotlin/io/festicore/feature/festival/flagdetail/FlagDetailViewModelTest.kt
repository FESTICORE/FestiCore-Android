package io.festicore.feature.festival.flagdetail

import io.festicore.domain.bearer.testing.FakeBearerUseCase
import io.festicore.domain.festival.entity.FestivalFlagDetail
import io.festicore.domain.festival.entity.FestivalFlagDetailError
import io.festicore.domain.festival.entity.FestivalFlagFollowError
import io.festicore.domain.festival.entity.FestivalFlagFollowResult
import io.festicore.domain.festival.entity.FestivalFlagReportError
import io.festicore.domain.festival.entity.FestivalFlagReportReason
import io.festicore.feature.festival.RecordingAnalyticsUseCase
import io.festicore.feature.festival.flagdetail.FlagDetailFixtures.NOW
import io.festicore.feature.festival.liveboard.FakeFlagUseCase
import io.festicore.feature.festival.liveboard.TestSleeper
import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.FlagFollowData
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * iOS `FestivalFlagDetailFeatureTests`. 잠금화면(대표 깃발) 테스트는 라이브 액티비티 단계로 뺐고,
 * flag_follow 분석 확인은 Android feature에 분석 경로가 없어 뺐다.
 */
class FlagDetailViewModelTest {
    private val analytics = RecordingAnalyticsUseCase()

    private fun viewModel(
        detailUseCase: FakeFlagDetailUseCase = FakeFlagDetailUseCase(),
        flagUseCase: FakeFlagUseCase = FakeFlagUseCase(setFollowing = { _, _ -> throw FestivalFlagFollowError.Unavailable() }),
        clock: MutableClock = MutableClock(NOW),
        sleep: suspend (Duration) -> Unit = { awaitCancellation() },
    ) = FlagDetailViewModel(
        flagId = "flg_1",
        context = FlagDetailFixtures.context(),
        detailUseCase = detailUseCase,
        flagUseCase = flagUseCase,
        analyticsUseCase = analytics,
        bearerUseCase = FakeBearerUseCase(),
        clock = clock,
        sleep = sleep,
    )

    @Test
    fun loadsDetailOnAppear() = runTest {
        val detail = FlagDetailFixtures.detail()
        viewModel(FakeFlagDetailUseCase(detail = { detail })).test(this, initialState = FlagDetailFixtures.state()) {
            containerHost.onAppeared()
            expectState { copy(loadGeneration = 1) }
            expectState { copy(content = FlagDetailContent.Loaded(detail), lastLoadedAt = NOW) }
            containerHost.onDisappeared()
            expectState { copy(loadGeneration = 2) }
        }
    }

    @Test
    fun clockTicksEveryMinuteUntilLeaving() = runTest {
        val sleeper = TestSleeper()
        val clock = MutableClock(NOW)
        viewModel(FakeFlagDetailUseCase(detail = { awaitCancellation() }), clock = clock, sleep = sleeper::sleep)
            .test(this, initialState = FlagDetailFixtures.state()) {
                containerHost.onAppeared()
                expectState { copy(loadGeneration = 1) }
                clock.instant = NOW.plusSeconds(60)
                sleeper.release(60.seconds)
                expectState { copy(now = NOW.plusSeconds(60)) }
                containerHost.onDisappeared()
                expectState { copy(loadGeneration = 2) }
                clock.instant = NOW.plusSeconds(120)
                sleeper.release(60.seconds)
            }
    }

    @Test
    fun firstFailureShowsNotFoundOrRetry() = runTest {
        listOf(
            FestivalFlagDetailError.NotFound() to FlagDetailFailure.NotFound,
            FestivalFlagDetailError.Unavailable() to FlagDetailFailure.Unavailable,
            FestivalFlagDetailError.InvalidResponse() to FlagDetailFailure.Unavailable,
        ).forEach { (error, failure) ->
            viewModel(FakeFlagDetailUseCase(detail = { throw error })).test(this, initialState = FlagDetailFixtures.state()) {
                containerHost.onRetryTapped()
                expectState { copy(loadGeneration = 1) }
                expectState { copy(content = FlagDetailContent.Failed(failure)) }
            }
        }
    }

    @Test
    fun refreshFailureKeepsLastDetailAsOffline() = runTest {
        val initial = FlagDetailFixtures.loaded().copy(lastLoadedAt = NOW.minusSeconds(180))
        viewModel(FakeFlagDetailUseCase(detail = { throw FestivalFlagDetailError.Unavailable() })).test(this, initialState = initial) {
            containerHost.onRetryTapped()
            expectState { copy(loadGeneration = 1) }
            val offline = awaitState()
            assertEquals(initial.copy(loadGeneration = 1, isOffline = true), offline)
            assertEquals("오프라인 · 3분 전 정보", FlagDetailPresentation.offlineText(offline))
            assertEquals(
                FlagDetailNow.Located(zone = "리벳 스테이지 앞 왼쪽", activity = "슬램", source = "마지막으로 받은 정보예요", isMuted = true),
                FlagDetailPresentation.nowCard(offline),
            )
            // 오프라인이면 팔로우를 바꾸지 않는다.
            containerHost.onFollowTapped()
        }
    }

    @Test
    fun staleGenerationIsIgnored() = runTest {
        viewModel().test(this, initialState = FlagDetailFixtures.state()) {
            containerHost.onLoaded(7, FlagDetailFixtures.detail(), NOW)
            containerHost.onLoadFailed(7, FestivalFlagDetailError.NotFound())
        }
    }

    @Test
    fun followWithoutSessionRequestsLogin() = runTest {
        viewModel().test(this, initialState = FlagDetailFixtures.loaded(hasSession = false)) {
            containerHost.onFollowTapped()
            expectSideEffect(FlagDetailSideEffect.LoginRequested)
        }
    }

    @Test
    fun followSuccessUpdatesButtonAndCount() = runTest {
        val sleeper = TestSleeper()
        val reloaded = FlagDetailFixtures.detail(followerCount = 1_285, isFollowing = true)
        val flagUseCase = FakeFlagUseCase(setFollowing = { _, id -> FestivalFlagFollowResult(id, true, 1_285) })
        viewModel(FakeFlagDetailUseCase(detail = { reloaded }), flagUseCase, sleep = sleeper::sleep)
            .test(this, initialState = FlagDetailFixtures.loaded()) {
                containerHost.onFollowTapped()
                expectState { copy(isFollowPending = true) }
                expectState {
                    copy(
                        isFollowPending = false,
                        followOverride = FlagDetailFollowOverride(isFollowing = true, followerCount = 1_285),
                        loadGeneration = 1,
                    )
                }
                assertEquals(listOf(true to "flg_1"), flagUseCase.calls)
                assertEquals(listOf(AnalyticsEvent.FlagFollow(FlagFollowData("flg_1", true))), analytics.events)
                expectSideEffect(FlagDetailSideEffect.FollowChanged(flagId = "flg_1", isFollowing = true, followerCount = 1_285))
                expectState { copy(toast = FlagDetailToast.Followed("호락호락")) }
                val loaded = awaitState()
                assertEquals(FlagDetailContent.Loaded(reloaded), loaded.content)
                assertNull(loaded.followOverride)
                assertEquals(NOW, loaded.lastLoadedAt)
                assertEquals(true, loaded.isFollowing)
                assertEquals(1_285, loaded.followerCount)
                sleeper.release(2.seconds)
                expectState { copy(toast = null) }
            }
    }

    @Test
    fun followFailuresAreRouted() = runTest {
        listOf(
            FestivalFlagFollowError.LoginRequired() to FlagDetailSideEffect.LoginRequested,
            FestivalFlagFollowError.TermsRequired() to FlagDetailSideEffect.TermsAcceptanceRequested,
        ).forEach { (error, sideEffect) ->
            viewModel(
                flagUseCase = FakeFlagUseCase(setFollowing = { _, _ ->
                    throw error
                }),
            ).test(this, initialState = FlagDetailFixtures.loaded()) {
                containerHost.onFollowTapped()
                expectState { copy(isFollowPending = true) }
                expectState { copy(isFollowPending = false) }
                expectSideEffect(sideEffect)
            }
        }
        listOf(
            FestivalFlagFollowError.LimitReached() to FlagDetailToast.FollowLimit,
            FestivalFlagFollowError.Unavailable() to FlagDetailToast.FollowFailed,
            FestivalFlagFollowError.NotFound() to FlagDetailToast.FollowFailed,
        ).forEach { (error, toast) ->
            val sleeper = TestSleeper()
            viewModel(flagUseCase = FakeFlagUseCase(setFollowing = { _, _ -> throw error }), sleep = sleeper::sleep)
                .test(this, initialState = FlagDetailFixtures.loaded()) {
                    containerHost.onFollowTapped()
                    expectState { copy(isFollowPending = true) }
                    expectState { copy(isFollowPending = false) }
                    expectState { copy(toast = toast) }
                    sleeper.release(2.seconds)
                    expectState { copy(toast = null) }
                }
        }
    }

    @Test
    fun leavingCancelsFollowAndIgnoresLateResponse() = runTest {
        viewModel().test(this, initialState = FlagDetailFixtures.loaded().copy(isFollowPending = true)) {
            containerHost.onDisappeared()
            expectState { copy(loadGeneration = 1, isFollowPending = false) }
            // 로그인 요청 응답이 늦게 와도 부모에게 알리지 않는다.
            containerHost.onFollowFinished(Result.failure(FestivalFlagFollowError.LoginRequired()))
        }
    }

    @Test
    fun tagTitlesUseDirectoryNames() {
        assertEquals(emptyList<String>(), FlagDetailPresentation.tagTitles(FlagDetailFixtures.state()))
        assertEquals(listOf("슬램", "떼창", "초보 환영"), FlagDetailPresentation.tagTitles(FlagDetailFixtures.loaded()))
    }

    @Test
    fun reportWithoutSessionRequestsLogin() = runTest {
        viewModel().test(this, initialState = FlagDetailFixtures.loaded(hasSession = false)) {
            containerHost.onReportTapped()
            expectSideEffect(FlagDetailSideEffect.ReportLoginRequested)
        }
    }

    @Test
    fun reportSubmitsReason() = runTest {
        val sleeper = TestSleeper()
        val detailUseCase = FakeFlagDetailUseCase()
        viewModel(detailUseCase, sleep = sleeper::sleep).test(this, initialState = FlagDetailFixtures.loaded()) {
            containerHost.onReportTapped()
            expectState { copy(reportSheet = FlagReportSheetState()) }
            // 사유를 고르기 전에는 신고하지 않는다.
            containerHost.onReportSubmitTapped()
            containerHost.onReportReasonSelected(FestivalFlagReportReason.Inappropriate)
            expectState { copy(reportSheet = FlagReportSheetState(selectedReason = FestivalFlagReportReason.Inappropriate)) }
            containerHost.onReportSubmitTapped()
            expectState { copy(reportSheet = null, isReportPending = true) }
            expectState { copy(isReportPending = false) }
            expectState { copy(toast = FlagDetailToast.Reported) }
            assertEquals(listOf(FestivalFlagReportReason.Inappropriate), detailUseCase.reports)
            sleeper.release(2.seconds)
            expectState { copy(toast = null) }
        }
    }

    @Test
    fun reportFailuresAreRouted() = runTest {
        val sheet = FlagReportSheetState(selectedReason = FestivalFlagReportReason.Fake)
        listOf(
            FestivalFlagReportError.AlreadyReported() to FlagDetailToast.AlreadyReported,
            FestivalFlagReportError.Unavailable() to FlagDetailToast.ReportFailed,
            FestivalFlagReportError.NotFound() to FlagDetailToast.ReportFailed,
        ).forEach { (error, toast) ->
            val sleeper = TestSleeper()
            viewModel(FakeFlagDetailUseCase(report = { _, _ -> throw error }), sleep = sleeper::sleep)
                .test(this, initialState = FlagDetailFixtures.loaded().copy(reportSheet = sheet)) {
                    containerHost.onReportSubmitTapped()
                    expectState { copy(reportSheet = null, isReportPending = true) }
                    expectState { copy(isReportPending = false) }
                    expectState { copy(toast = toast) }
                    sleeper.release(2.seconds)
                    expectState { copy(toast = null) }
                }
        }
        listOf(
            FestivalFlagReportError.LoginRequired() to FlagDetailSideEffect.ReportLoginRequested,
            FestivalFlagReportError.TermsRequired() to FlagDetailSideEffect.TermsAcceptanceRequested,
        ).forEach { (error, sideEffect) ->
            viewModel(
                FakeFlagDetailUseCase(report = { _, _ ->
                    throw error
                }),
            ).test(this, initialState = FlagDetailFixtures.loaded().copy(reportSheet = sheet)) {
                containerHost.onReportSubmitTapped()
                expectState { copy(reportSheet = null, isReportPending = true) }
                expectState { copy(isReportPending = false) }
                expectSideEffect(sideEffect)
            }
        }
    }

    @Test
    fun leavingCancelsReportAndIgnoresLateResult() = runTest {
        viewModel().test(this, initialState = FlagDetailFixtures.loaded().copy(isReportPending = true)) {
            containerHost.onDisappeared()
            expectState { copy(loadGeneration = 1, isReportPending = false) }
            containerHost.onReportFailed(FestivalFlagReportError.LoginRequired())
        }
    }

    @Test
    fun sameAccountContextChangeKeepsDetail() = runTest {
        val initial = FlagDetailFixtures.loaded(FlagDetailFixtures.detail(isFollowing = true))
        viewModel().test(this, initialState = initial) {
            val context = initial.context.copy(mySetIds = emptySet(), zoneNames = emptyMap())
            containerHost.onContextChanged(context)
            expectState { copy(context = context) }
        }
    }

    @Test
    fun accountChangeReloadsDetail() = runTest {
        val fresh = FlagDetailFixtures.detail(isFollowing = false)
        val initial =
            FlagDetailFixtures.loaded(FlagDetailFixtures.detail(isFollowing = true)).copy(
                followOverride = FlagDetailFollowOverride(isFollowing = true, followerCount = 1_285),
                reportSheet = FlagReportSheetState(),
            )
        viewModel(FakeFlagDetailUseCase(detail = { fresh })).test(this, initialState = initial) {
            // 다른 값은 같고 계정만 바뀐 문맥(A → B)
            val context = initial.context.copy(accountId = "user-b")
            containerHost.onContextChanged(context)
            expectState {
                copy(context = context, followOverride = null, reportSheet = null, content = FlagDetailContent.Loading, loadGeneration = 1)
            }
            expectState { copy(content = FlagDetailContent.Loaded(fresh), lastLoadedAt = NOW) }
            assertFalse(containerHost.container.stateFlow.value.isFollowing)
        }
    }

    @Test
    fun shareUrlUsesFlagDeeplinkOnlyAfterDetailLoaded() = runTest {
        assertEquals("festicore://flag/flg_1", FlagDetailFixtures.state().shareUrl)
        viewModel().test(this, initialState = FlagDetailFixtures.state()) {
            containerHost.onShareTapped()
        }
        viewModel().test(this, initialState = FlagDetailFixtures.loaded()) {
            containerHost.onShareTapped()
            expectSideEffect(FlagDetailSideEffect.ShareRequested(url = "festicore://flag/flg_1", title = "호락호락"))
        }
    }

    @Test
    fun nowCardFollowsStatusAndAge() {
        fun now(detail: FestivalFlagDetail) = FlagDetailPresentation.nowCard(FlagDetailFixtures.loaded(detail))
        assertEquals(
            FlagDetailNow.Located("리벳 스테이지 앞 왼쪽", "슬램", "기수가 4분 전에 알렸어요", isMuted = false),
            now(FlagDetailFixtures.detail(updatedMinutesAgo = 4)),
        )
        assertEquals(
            FlagDetailNow.Located("리벳 스테이지 앞 왼쪽", "슬램", "32분 전 정보예요 · 지금은 다를 수 있어요", isMuted = true),
            now(FlagDetailFixtures.detail(updatedMinutesAgo = 32)),
        )
        assertEquals(
            FlagDetailNow.Located("리벳 스테이지 앞 왼쪽", "쉬는 중", "기수가 12분 전에 알렸어요", isMuted = false),
            now(
                FlagDetailFixtures.detail(
                    status = FestivalFlagDetail.Status.Resting,
                    activity = FestivalFlagDetail.Activity.Resting,
                    updatedMinutesAgo = 12,
                ),
            ),
        )
        assertEquals(FlagDetailNow.Ended, now(FlagDetailFixtures.detail(status = FestivalFlagDetail.Status.Ended)))
        assertEquals(
            FlagDetailNow.Located("위치를 아직 알리지 않았어요", "예정", "기수가 방금 알렸어요", isMuted = false),
            now(FlagDetailFixtures.detail(status = FestivalFlagDetail.Status.Scheduled, updatedMinutesAgo = 0, zoneId = null)),
        )
    }

    @Test
    fun planRowsKeepBearerOrder() {
        val rows = FlagDetailPresentation.planRows(
            FlagDetailFixtures.loaded(FlagDetailFixtures.detail(plan = listOf("set_late", "set_missing", "set_now", "set_past"))),
        )
        assertEquals(listOf("set_late", "set_now", "set_past"), rows.map { it.id })
        assertEquals(
            listOf(FlagDetailPlanRow.Timing.Upcoming, FlagDetailPlanRow.Timing.Now, FlagDetailPlanRow.Timing.Past),
            rows.map {
                it.timing
            },
        )
        assertEquals(listOf(false, true, false), rows.map { it.isMine })
        assertEquals("23:00", rows.first().time)
        val ended = FlagDetailPresentation.planRows(
            FlagDetailFixtures.loaded(
                FlagDetailFixtures.detail(status = FestivalFlagDetail.Status.Ended, plan = listOf("set_late", "set_now")),
            ),
        )
        assertEquals(listOf(FlagDetailPlanRow.Timing.Past, FlagDetailPlanRow.Timing.Past), ended.map { it.timing })
    }
}
