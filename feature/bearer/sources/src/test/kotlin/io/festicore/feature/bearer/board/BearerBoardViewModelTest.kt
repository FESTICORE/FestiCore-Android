package io.festicore.feature.bearer.board

import io.festicore.domain.bearer.entity.BearerActivity
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerCoBearer
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerOutboxEvent
import io.festicore.domain.bearer.entity.BearerStatusChange
import io.festicore.domain.bearer.entity.BearerStatusRequest
import io.festicore.domain.bearer.entity.BearerZone
import io.festicore.feature.bearer.claim.FakeBearerUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.Item
import org.orbitmvi.orbit.test.OrbitTestContext
import org.orbitmvi.orbit.test.test

/**
 * iOS `BearerBoardFeatureTests` 13건(함수 이름을 iOS와 같게 둔다) + Android 추가 `reloadsOnForegroundKeepingPending` · `reloadsWhenConnectionReturns`(설계 B-3).
 * 상태 전이가 많아 각 단계 뒤 마지막 상태를 비교하고(iOS 일부 테스트의 `exhaustivity = .off`와 같은 뜻), 시간은 가상 시간으로 옮긴다.
 */
private typealias BoardTestContext = OrbitTestContext<BearerBoardState, BearerBoardSideEffect, BearerBoardViewModel>

class BearerBoardViewModelTest {
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private val zones = listOf(BearerZone(id = "zone_a", name = "리벳 스테이지 앞 왼쪽"), BearerZone(id = "zone_b", name = "리벳 스테이지 중앙"))

    private fun board(
        lifecycle: BearerFlagLifecycle,
        activity: BearerActivity? = BearerActivity.Slam,
        restartUsedToday: Boolean = false,
        updatedBy: BearerCoBearer? = null,
    ): BearerBoard {
        val live = lifecycle == BearerFlagLifecycle.Active || lifecycle == BearerFlagLifecycle.Resting
        return BearerBoard(
            profile = BearerFlagProfile(id = "flg_1", name = "호락호락", homeZoneId = "zone_a"),
            status =
                BearerFlagStatus(
                    flagId = "flg_1",
                    lifecycle = lifecycle,
                    zoneId = if (live) "zone_a" else null,
                    activity = if (lifecycle == BearerFlagLifecycle.Active) activity else null,
                    updatedAt = now,
                    updatedBy = updatedBy,
                ),
            followerCount = 1284,
            restartUsedToday = restartUsedToday,
        )
    }

    private fun loaded(board: BearerBoard, viewer: String? = "usr_me"): BearerBoardState =
        BearerBoardState(festivalId = "fst_1", flagId = "flg_1", viewerUserId = viewer).copy(
            board = board,
            festival = BearerFestival(id = "fst_1", name = "원더리벳 2026", zones = zones),
            current = BearerBoardCurrent.of(board.status),
            lastActivity = board.status.activity,
        )

    /** 부를 때마다 key-1, key-2 … 를 주는 상태 요청 생성기 */
    private fun requests(): (BearerStatusChange) -> BearerStatusRequest {
        var counter = 0
        return { change ->
            counter += 1
            BearerStatusRequest(change = change, idempotencyKey = "key-$counter", clientAt = now)
        }
    }

    private fun viewModel(useCase: FakeBearerUseCase = FakeBearerUseCase(), viewer: String? = "usr_me") = BearerBoardViewModel(
        festivalId = "fst_1",
        flagId = "flg_1",
        viewerUserId = viewer,
        bearerUseCase = useCase,
        clock = Clock.fixed(now, ZoneId.of("Asia/Seoul")),
    )

    private val BoardTestContext.state: BearerBoardState
        get() = containerHost.container.stateFlow.value

    /** 사이의 상태 알림은 건너뛰고 다음 SideEffect를 받는다. */
    private suspend fun BoardTestContext.awaitEffect(): BearerBoardSideEffect {
        while (true) {
            val item = awaitItem()
            if (item is Item.SideEffectItem) return item.value
        }
    }

    // 기수 보드를 열면 대기열을 먼저 구독한 뒤 상태·존·팔로워 수를 읽고, 닫으면 구독을 끝낸다
    @Test
    fun loads() = runTest {
        val board = board(BearerFlagLifecycle.Active)
        val festival = BearerFestival(id = "fst_1", name = null, zones = zones)
        val order = mutableListOf<String>()
        var unsubscribed = false
        val useCase =
            FakeBearerUseCase(
                onFetchFestival = { festival },
                onFetchBoard = {
                    order += "fetchBoard"
                    board
                },
                onPendingStatus = {
                    order += "pending"
                    emptyList()
                },
                onStatusEvents = {
                    flow<BearerOutboxEvent> {
                        order += "subscribe"
                        awaitCancellation()
                    }.onCompletion { unsubscribed = true }
                },
                onRetryPendingStatus = { order += "retry" },
            )
        viewModel(useCase).test(this) {
            containerHost.onAppear()
            testScheduler.runCurrent()
            assertEquals(listOf("subscribe", "pending"), order.take(2))
            assertTrue(order.containsAll(listOf("retry", "fetchBoard")))
            assertEquals(
                BearerBoardState(festivalId = "fst_1", flagId = "flg_1", viewerUserId = "usr_me").copy(
                    board = board,
                    festival = festival,
                    lastSyncedAt = now,
                    current = BearerBoardCurrent.of(board.status),
                    lastActivity = BearerActivity.Slam,
                ),
                state,
            )
            assertEquals("리벳 스테이지 앞 왼쪽", state.zoneName("zone_a"))
            assertFalse(state.showsSkeleton)

            containerHost.onDisappear()
            testScheduler.runCurrent()
            assertTrue(unsubscribed)
            cancelAndIgnoreRemainingItems()
        }
    }

    // 화면이 사라지면 멈춘 로딩과 체크 표시를 지운다
    @Test
    fun disappearClearsTransientState() = runTest {
        val initial =
            loaded(board(BearerFlagLifecycle.Active)).copy(
                isLoading = true,
                check = BearerBoardCheck(id = 1, title = "쉬는 중으로 바뀌었어요", message = "팔로워에게 알렸어요"),
            )
        viewModel().test(this, initialState = initial) {
            containerHost.onDisappear()
            expectState { copy(isLoading = false, check = null) }
        }
    }

    // 누른 사용자를 모르면 대기열에 넣지 않는다
    @Test
    fun requiresViewer() = runTest {
        viewModel(viewer = null).test(this, initialState = loaded(board(BearerFlagLifecycle.Active), viewer = null)) {
            containerHost.onRestTapped()
            expectState { copy(errorMessage = "로그인한 뒤에 바꿀 수 있어요") }
        }
    }

    // 쉬는 중을 누르면 바로 바꾸고 1초 체크를 보인 뒤 대기열에 넣는다
    @Test
    fun rest() = runTest {
        val queued = mutableListOf<BearerStatusRequest>()
        val useCase =
            FakeBearerUseCase(
                onMakeStatusRequest = requests(),
                onEnqueueStatus = { flagId, owner, request ->
                    assertEquals("flg_1", flagId)
                    assertEquals("usr_me", owner)
                    queued += request
                },
            )
        val initial = loaded(board(BearerFlagLifecycle.Active))
        viewModel(useCase).test(this, initialState = initial) {
            val request = BearerStatusRequest(change = BearerStatusChange.Rest, idempotencyKey = "key-1", clientAt = now)
            containerHost.onRestTapped()
            testScheduler.runCurrent()
            assertEquals(
                initial.copy(
                    pending = listOf(request),
                    current = initial.current?.copy(lifecycle = BearerFlagLifecycle.Resting, activity = null),
                    checkSequence = 1,
                    check = BearerBoardCheck(id = 1, title = "쉬는 중으로 바뀌었어요", message = "팔로워에게 알렸어요"),
                ),
                state,
            )
            assertEquals(BearerBoardSideEffect.CheckHaptic, awaitEffect())
            assertEquals(listOf(request), queued)

            testScheduler.advanceTimeBy(999)
            testScheduler.runCurrent()
            assertNotNull(state.check)
            testScheduler.advanceTimeBy(1)
            testScheduler.runCurrent()
            assertNull(state.check)
            cancelAndIgnoreRemainingItems()
        }
    }

    // 대기열에 두 건이 쌓여 있으면 서버 상태 위에 차례로 얹어 보이고, 모두 보내면 서버 상태로 맞춘다
    @Test
    fun twoPendingChanges() = runTest {
        val events = Channel<BearerOutboxEvent>(Channel.UNLIMITED)
        var retried = 0
        val pending =
            listOf(
                BearerStatusRequest(change = BearerStatusChange.Move(zoneId = "zone_b"), idempotencyKey = "key-1", clientAt = now),
                BearerStatusRequest(change = BearerStatusChange.Rest, idempotencyKey = "key-2", clientAt = now.plusSeconds(1)),
            )
        val active = board(BearerFlagLifecycle.Active)
        val useCase =
            FakeBearerUseCase(
                onPendingStatus = { pending },
                onStatusEvents = { events.receiveAsFlow() },
                onRetryPendingStatus = { retried += 1 },
            )
        viewModel(useCase).test(this, initialState = loaded(active)) {
            containerHost.onAppear()
            testScheduler.runCurrent()
            assertEquals(pending, state.pending)
            assertEquals("zone_b", state.current?.zoneId)
            assertEquals(BearerFlagLifecycle.Resting, state.current?.lifecycle)
            // 서버 상태를 다시 읽어도 보내지 못한 두 변경을 다시 얹는다.
            containerHost.onLoaded(active, null)
            testScheduler.runCurrent()
            assertEquals("zone_b", state.current?.zoneId)
            assertEquals(BearerFlagLifecycle.Resting, state.current?.lifecycle)

            val resting =
                BearerFlagStatus(
                    flagId = "flg_1",
                    lifecycle = BearerFlagLifecycle.Resting,
                    zoneId = "zone_b",
                    activity = null,
                    updatedAt = now,
                )
            events.send(BearerOutboxEvent.Pending(listOf(pending[1])))
            events.send(BearerOutboxEvent.Sent(id = "key-1", status = resting))
            testScheduler.runCurrent()
            assertEquals(listOf(pending[1]), state.pending)
            assertEquals(1, retried)
            assertEquals(BearerFlagLifecycle.Resting, state.current?.lifecycle)

            events.send(BearerOutboxEvent.Pending(emptyList()))
            events.send(BearerOutboxEvent.Sent(id = "key-2", status = resting))
            testScheduler.runCurrent()
            assertEquals(emptyList<BearerStatusRequest>(), state.pending)
            assertEquals(BearerBoardCurrent.of(resting), state.current)
            events.close()
            cancelAndIgnoreRemainingItems()
        }
    }

    // 오프라인·로그인 필요·재시도 한도는 알리고, 다시 보내기를 누르면 대기열을 다시 시작한다
    @Test
    fun outboxStates() = runTest {
        var retried = 0
        val useCase = FakeBearerUseCase(onRetryPendingStatus = { retried += 1 })
        viewModel(useCase).test(this, initialState = loaded(board(BearerFlagLifecycle.Active))) {
            containerHost.onOutboxEvent(BearerOutboxEvent.Offline)
            expectState { copy(isOffline = true) }
            containerHost.onOutboxEvent(BearerOutboxEvent.NeedsLogin)
            expectState { copy(isSendPaused = true, errorMessage = "로그인이 풀렸어요. 다시 로그인하면 이어서 보내요") }
            containerHost.onRetrySendTapped()
            expectState { copy(isSendPaused = false, errorMessage = null) }
            testScheduler.runCurrent()
            assertEquals(1, retried)
            containerHost.onOutboxEvent(BearerOutboxEvent.GaveUp(BearerError.Unavailable()))
            expectState { copy(isSendPaused = true, errorMessage = "서버에 보내지 못했어요. 잠시 뒤 다시 보내기를 눌러 주세요") }
        }
    }

    // 서버가 받지 않으면(409) 최신 상태를 다시 읽는다
    @Test
    fun rejectedReloads() = runTest {
        val latest = board(BearerFlagLifecycle.Active, activity = BearerActivity.Sing)
        val useCase =
            FakeBearerUseCase(
                onFetchFestival = { BearerFestival(id = "fst_1", name = null, zones = zones) },
                onFetchBoard = { latest },
            )
        viewModel(useCase).test(this, initialState = loaded(board(BearerFlagLifecycle.Active))) {
            containerHost.onOutboxEvent(BearerOutboxEvent.Rejected(BearerError.Stale()))
            testScheduler.runCurrent()
            assertEquals("다른 기기에서 먼저 바뀌어 최신 상태로 돌렸어요", state.errorMessage)
            assertEquals(BearerBoardCurrent.of(latest.status), state.current)
            assertEquals(BearerActivity.Sing, state.lastActivity)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingItems()
        }
    }

    // 못 보낸 변경이 있으면 닫기 전에 확인을 받는다
    @Test
    fun closeWithPending() = runTest {
        val initial =
            loaded(board(BearerFlagLifecycle.Active)).copy(
                pending = listOf(BearerStatusRequest(change = BearerStatusChange.Rest, idempotencyKey = "key-1", clientAt = now)),
            )
        viewModel().test(this, initialState = initial) {
            containerHost.onCloseTapped()
            expectState { copy(destination = BearerBoardDestination.CloseConfirm(count = 1)) }
            containerHost.onCloseConfirmed()
            expectState { copy(destination = null) }
            expectSideEffect(BearerBoardSideEffect.CloseRequested)
        }

        viewModel().test(this, initialState = loaded(board(BearerFlagLifecycle.Active))) {
            containerHost.onCloseTapped()
            expectSideEffect(BearerBoardSideEffect.CloseRequested)
        }
    }

    // 시작 전에는 존과 활동을 차례로 골라 깃발을 올린다
    @Test
    fun raise() = runTest {
        val useCase = FakeBearerUseCase(onMakeStatusRequest = requests(), onEnqueueStatus = { _, _, _ -> })
        viewModel(useCase).test(this, initialState = loaded(board(BearerFlagLifecycle.Scheduled))) {
            containerHost.onRaiseTapped()
            expectState {
                copy(
                    sheet = BearerBoardSheet.StartZone,
                    destination = BearerBoardDestination.ZonePicker(zones = zones, selectedZoneId = "zone_a"),
                )
            }
            containerHost.onZonePicked("zone_a")
            expectState {
                copy(
                    sheet = BearerBoardSheet.StartActivity(zoneId = "zone_a"),
                    destination = BearerBoardDestination.ActivityPicker(selected = null),
                )
            }
            containerHost.onActivityPicked(BearerActivity.Sing)
            testScheduler.runCurrent()
            assertNull(state.destination)
            assertNull(state.sheet)
            assertEquals(BearerFlagLifecycle.Active, state.current?.lifecycle)
            assertEquals("zone_a", state.current?.zoneId)
            assertEquals(BearerActivity.Sing, state.current?.activity)
            assertEquals(BearerActivity.Sing, state.lastActivity)
            assertEquals(
                listOf(
                    BearerStatusRequest(
                        change = BearerStatusChange.Start(zoneId = "zone_a", activity = BearerActivity.Sing),
                        idempotencyKey = "key-1",
                        clientAt = now,
                    ),
                ),
                state.pending,
            )
            assertEquals("깃발을 올렸어요", state.check?.title)
            cancelAndIgnoreRemainingItems()
        }
    }

    // 종료는 확인 팝업을 거치고, 오늘 재시작을 쓴 뒤에는 다시 시작하지 않는다
    @Test
    fun endAndRestart() = runTest {
        val useCase = FakeBearerUseCase(onMakeStatusRequest = requests(), onEnqueueStatus = { _, _, _ -> })
        viewModel(useCase).test(this, initialState = loaded(board(BearerFlagLifecycle.Active))) {
            containerHost.onEndTapped()
            expectState { copy(destination = BearerBoardDestination.EndConfirm) }
            containerHost.onEndConfirmed()
            testScheduler.runCurrent()
            assertNull(state.destination)
            assertEquals(BearerFlagLifecycle.Ended, state.current?.lifecycle)
            assertNull(state.current?.zoneId)
            assertNull(state.current?.activity)
            assertFalse(state.restartUsedToday)
            cancelAndIgnoreRemainingItems()
        }

        val used = loaded(board(BearerFlagLifecycle.Ended, restartUsedToday = true))
        assertTrue(used.restartUsedToday)
        viewModel().test(this, initialState = used) {
            containerHost.onRestartTapped()
            testScheduler.runCurrent()
            expectNoItems()
        }
    }

    // 쉬는 중에서 다시 시작하면 마지막 활동으로 돌아간다
    @Test
    fun resume() = runTest {
        val useCase = FakeBearerUseCase(onMakeStatusRequest = requests(), onEnqueueStatus = { _, _, _ -> })
        val initial = loaded(board(BearerFlagLifecycle.Resting)).copy(lastActivity = BearerActivity.Train)
        viewModel(useCase).test(this, initialState = initial) {
            containerHost.onResumeTapped()
            testScheduler.runCurrent()
            assertEquals(BearerFlagLifecycle.Active, state.current?.lifecycle)
            assertEquals(BearerActivity.Train, state.current?.activity)
            assertEquals(
                listOf(
                    BearerStatusRequest(
                        change = BearerStatusChange.Activity(BearerActivity.Train),
                        idempotencyKey = "key-1",
                        clientAt = now,
                    ),
                ),
                state.pending,
            )
            cancelAndIgnoreRemainingItems()
        }
    }

    // 다른 공동 기수가 바꿨을 때만 바꾼 사람을 보인다
    @Test
    fun coBearerChange() {
        val other = BearerCoBearer(userId = "usr_co", nickname = "민지")
        assertEquals(other, loaded(board(BearerFlagLifecycle.Active, updatedBy = other)).changedByOtherBearer)
        assertNull(
            loaded(board(BearerFlagLifecycle.Active, updatedBy = BearerCoBearer(userId = "usr_me", nickname = "로이"))).changedByOtherBearer,
        )
        assertNull(loaded(board(BearerFlagLifecycle.Active)).changedByOtherBearer)
    }

    // 받침에 맞춰 조사를 붙인다
    @Test
    fun josa() {
        assertEquals("리벳 스테이지 중앙으로", "리벳 스테이지 중앙".withEuro())
        assertEquals("슬램 존으로", "슬램 존".withEuro())
        assertEquals("떼창으로", "떼창".withEuro())
        assertEquals("기차놀이로", "기차놀이".withEuro())
        assertEquals("정문이", "정문".withIga())
        assertEquals("민지가", "민지".withIga())
    }

    // Android 추가(설계 B-3): 포그라운드로 돌아오면 지금 보드를 둔 채 다시 읽고, 남은 대기 건을 결과 위에 다시 얹는다
    @Test
    fun reloadsOnForegroundKeepingPending() = runTest {
        val other = BearerCoBearer(userId = "usr_co", nickname = "민지")
        val latest = board(BearerFlagLifecycle.Active, activity = BearerActivity.Sing, updatedBy = other)
        val gate = CompletableDeferred<Unit>()
        var fetches = 0
        val useCase =
            FakeBearerUseCase(
                onFetchFestival = { BearerFestival(id = "fst_1", name = null, zones = zones) },
                onFetchBoard = {
                    fetches += 1
                    gate.await()
                    latest
                },
            )
        val move = BearerStatusRequest(change = BearerStatusChange.Move(zoneId = "zone_b"), idempotencyKey = "key-1", clientAt = now)
        val initial = loaded(board(BearerFlagLifecycle.Active)).copy(pending = listOf(move)).applyingPending()
        viewModel(useCase).test(this, initialState = initial) {
            containerHost.onForeground()
            testScheduler.runCurrent()
            assertEquals(1, fetches)
            // 읽는 동안 스켈레톤으로 바꾸지 않는다.
            assertTrue(state.isLoading)
            assertFalse(state.showsSkeleton)
            assertEquals("zone_b", state.current?.zoneId)

            gate.complete(Unit)
            testScheduler.runCurrent()
            assertFalse(state.isLoading)
            assertEquals(latest, state.board)
            assertEquals(BearerActivity.Sing, state.current?.activity)
            assertEquals("zone_b", state.current?.zoneId)
            assertEquals(listOf(move), state.pending)
            assertEquals(BearerActivity.Sing, state.lastActivity)
            cancelAndIgnoreRemainingItems()
        }

        // 첫 읽기 전에는 onAppear가 읽으므로 포그라운드 알림으로 읽지 않는다.
        viewModel(useCase).test(this) {
            containerHost.onForeground()
            testScheduler.runCurrent()
            assertEquals(1, fetches)
            expectNoItems()
        }
    }

    // Android 추가(설계 B-3): 오프라인 표시 중 첫 전송 성공(연결 복구)이면 다시 읽고 남은 대기 건을 다시 얹는다. 연결된 동안의 전송 성공은 다시 읽지 않는다
    @Test
    fun reloadsWhenConnectionReturns() = runTest {
        val latest = board(BearerFlagLifecycle.Active, activity = BearerActivity.Calm)
        var fetches = 0
        val useCase =
            FakeBearerUseCase(
                onFetchFestival = { BearerFestival(id = "fst_1", name = null, zones = zones) },
                onFetchBoard = {
                    fetches += 1
                    latest
                },
            )
        val rest = BearerStatusRequest(change = BearerStatusChange.Rest, idempotencyKey = "key-2", clientAt = now)
        val initial = loaded(board(BearerFlagLifecycle.Active)).copy(pending = listOf(rest), isOffline = true).applyingPending()
        viewModel(useCase).test(this, initialState = initial) {
            val sent =
                BearerFlagStatus(
                    flagId = "flg_1",
                    lifecycle = BearerFlagLifecycle.Active,
                    zoneId = "zone_a",
                    activity = BearerActivity.Slam,
                    updatedAt = now,
                )
            containerHost.onOutboxEvent(BearerOutboxEvent.Sent(id = "key-1", status = sent))
            testScheduler.runCurrent()
            assertEquals(1, fetches)
            assertFalse(state.isOffline)
            assertEquals(latest, state.board)
            assertEquals(BearerFlagLifecycle.Resting, state.current?.lifecycle)
            assertEquals(BearerActivity.Calm, state.lastActivity)

            containerHost.onOutboxEvent(BearerOutboxEvent.Sent(id = "key-2", status = sent))
            testScheduler.runCurrent()
            assertEquals(1, fetches)
            cancelAndIgnoreRemainingItems()
        }
    }
}
