package io.festicore.domain.bearer.sources

import android.app.Application
import io.festicore.domain.bearer.entity.BearerActivity
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerOutboxEvent
import io.festicore.domain.bearer.entity.BearerStatusChange
import io.festicore.domain.bearer.entity.BearerStatusRequest
import io.festicore.domain.bearer.sources.bearer.BearerUseCaseImpl
import io.festicore.domain.bearer.sources.outbox.BearerServerClock
import io.festicore.domain.bearer.sources.outbox.BearerStatusOutboxImpl
import io.festicore.domain.bearer.sources.outbox.BearerStatusOutboxResumer
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * iOS `BearerStatusOutboxTests` 10건(`dropsOtherAccount`는 Android 규칙대로 `keepsOtherAccountUntilOwnerReturns`로 바꿈)과
 * 설계가 더한 계정 격리 · 세션 · 만료 · 시계 오차 · 재개 18건. 대기 시간은 가상 시간이다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BearerStatusOutboxTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, BearerStatusOutboxImpl.FILE_PATH)

    // ── iOS 10건 ──

    @Test
    fun ordersConcurrentEnqueues() = runTest {
        val box = Box(this, results = List(5) { ok() })

        for (index in listOf(3, 0, 4, 1, 2)) {
            launch { box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-$index", offset = index.toLong())) }
        }
        advanceUntilIdle()

        assertEquals(listOf("key-0", "key-1", "key-2", "key-3", "key-4"), box.sentKeys)
    }

    @Test
    fun retriesOffline() = runTest {
        val box = Box(this, results = listOf(fail(BearerError.Offline()), ok()))
        val events = box.record()

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()

        assertEquals(listOf("key-1", "key-1"), box.sentKeys)
        assertEquals(listOf(5.seconds), box.sleeps)
        assertTrue(BearerOutboxEvent.Offline in events)
        assertEquals(BearerOutboxEvent.Pending(emptyList()), events.last { it is BearerOutboxEvent.Pending })
    }

    @Test
    fun retriesRateLimited() = runTest {
        val box = Box(this, results = listOf(fail(BearerError.RateLimited()), ok()))

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()

        assertEquals(listOf("key-1", "key-1"), box.sentKeys)
        assertEquals(listOf(15.seconds), box.sleeps)
    }

    @Test
    fun keepsAttemptedItemFirst() = runTest {
        val box = Box(this, results = listOf(fail(BearerError.Offline()), ok(), ok()))

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "late", offset = 10))
        runCurrent()
        // 첫 시도가 실패하고 다시 보내기 전(5초 대기 중)에 더 이른 변경이 들어온다.
        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.End, "early"))
        advanceUntilIdle()

        assertEquals(listOf("late", "late", "early"), box.sentKeys)
    }

    @Test
    fun keepsQueueOnUnauthorized() = runTest {
        val box = Box(this, results = listOf(fail(BearerError.NotAuthenticated()), ok()))
        val events = box.record()

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()
        assertEquals(BearerOutboxEvent.NeedsLogin, events.last())
        assertEquals(1, box.outbox.pending(FLAG).size)

        box.outbox.resume()
        advanceUntilIdle()
        assertEquals(listOf("key-1", "key-1"), box.sentKeys)
        assertTrue(box.outbox.pending(FLAG).isEmpty())
    }

    @Test
    fun backsOffThenGivesUp() = runTest {
        val box = Box(this, results = List(10) { fail(BearerError.Unavailable()) })
        val events = box.record()

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()

        assertEquals(BearerOutboxEvent.GaveUp(BearerError.Unavailable()), events.last())
        assertEquals(listOf(2, 4, 8, 16, 32).map { it.seconds }, box.sleeps)
        assertEquals(BearerStatusOutboxImpl.MAX_SERVER_RETRIES + 1, box.sent.size)
        assertEquals(1, box.outbox.pending(FLAG).size)
    }

    @Test
    fun keepsQueueOnTermsRequired() = runTest {
        val box = Box(this, results = listOf(fail(BearerError.TermsRequired())))
        val events = box.record()

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()

        assertEquals(BearerOutboxEvent.GaveUp(BearerError.TermsRequired()), events.last())
        assertEquals(1, box.outbox.pending(FLAG).size)
    }

    @Test
    fun dropsRejected() = runTest {
        val box = Box(this, results = listOf(fail(BearerError.Stale())))
        val events = box.record()

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.End, "key-2", offset = 1))
        advanceUntilIdle()

        assertEquals(listOf("key-1"), box.sentKeys)
        assertEquals(BearerOutboxEvent.Rejected(BearerError.Stale()), events.last())
        assertTrue(box.outbox.pending(FLAG).isEmpty())
    }

    /** iOS `dropsOtherAccount`. Android는 다른 계정 건을 버리지 않고 주인이 다시 로그인하면 그 세션으로 보낸다. */
    @Test
    fun keepsOtherAccountUntilOwnerReturns() = runTest {
        val box = Box(this, userId = OTHER, results = listOf(ok()))

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()
        assertTrue(box.sent.isEmpty())
        assertTrue(box.outbox.pending(FLAG).isEmpty())

        box.login(ME)
        box.outbox.resume()
        advanceUntilIdle()
        assertEquals(listOf(Sent("key-1", NOW, "ses_$ME")), box.sent)
    }

    @Test
    fun persists() = runTest {
        val restart = request(BearerStatusChange.Restart("zone_a", BearerActivity.Sing), "key-1")
        val first = Box(this, userId = null, file = file)
        first.outbox.enqueue(FLAG, ME, restart)
        advanceUntilIdle()

        assertTrue(Box(this, userId = null, file = file).outbox.pending(FLAG).isEmpty())
        assertEquals(listOf(restart), Box(this, file = file).outbox.pending(FLAG))

        file.writeText("""[{"flagID":"$FLAG","idempotencyKey":"k","clientAt":${NOW.toEpochMilli()},"kind":"rest"}]""")
        assertTrue(Box(this, file = file).outbox.pending(FLAG).isEmpty())
    }

    // ── 계정 격리 · 세션 ──

    @Test
    fun keepsCurrentUserItemsWhenOtherOwnerFirst() = runTest {
        val box = Box(this, userId = OTHER, results = listOf(ok(), ok()))

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "mine"))
        box.outbox.enqueue(FLAG, OTHER, request(BearerStatusChange.End, "theirs", offset = 1))
        advanceUntilIdle()
        assertEquals(listOf(Sent("theirs", NOW.plusSeconds(1), "ses_$OTHER")), box.sent)

        box.login(ME)
        box.outbox.resume()
        advanceUntilIdle()
        assertEquals(listOf("theirs", "mine"), box.sentKeys)
    }

    @Test
    fun sessionChangeBeforeSendDoesNotSend() = runTest {
        val box = Box(this, results = listOf(ok()))
        // 세션 S를 읽은 직후 다른 계정으로 바뀐다.
        box.afterFirstSessionRead = { box.login(OTHER) }

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()

        assertTrue(box.sent.isEmpty())
        box.login(ME)
        assertEquals(1, box.outbox.pending(FLAG).size)
    }

    @Test
    fun confirmedSendAfterSessionChangeIsRemovedSilently() = runTest {
        lateinit var box: Box
        box = Box(
            this,
            results = listOf {
                box.login(OTHER)
                RESTING
            },
        )
        val events = box.record()

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        val before = events.size
        advanceUntilIdle()

        assertEquals(1, box.sent.size)
        assertEquals(before, events.size)
        box.login(ME)
        assertTrue(box.outbox.pending(FLAG).isEmpty())
    }

    @Test
    fun unknownResultAfterAccountChangeIsKeptForOwner() = runTest {
        val never = CompletableDeferred<BearerFlagStatus>()
        val box = Box(this, results = listOf({ never.await() }, ok()))
        val original = request(BearerStatusChange.Rest, "key-1")

        box.outbox.enqueue(FLAG, ME, original)
        runCurrent()
        box.login(OTHER)
        box.outbox.cancelWork()
        advanceUntilIdle()

        box.login(ME)
        assertEquals(listOf(original), box.outbox.pending(FLAG))
        box.outbox.resume()
        advanceUntilIdle()
        assertEquals(listOf(Sent("key-1", NOW, "ses_$ME"), Sent("key-1", NOW, "ses_$ME")), box.sent)
    }

    @Test
    fun resendUsesSameKeyAndClientAt() = runTest {
        val first = Box(this, file = file, results = listOf(fail(BearerError.Unavailable())))
        first.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Move("zone_b"), "key-1", offset = 3))
        runCurrent()
        first.outbox.cancelWork()

        // 앱을 다시 켜도 같은 멱등키 · 같은 clientAt으로 보낸다.
        val second = Box(this, file = file, results = listOf(ok()))
        second.outbox.resume()
        advanceUntilIdle()

        assertEquals(first.sent.single(), second.sent.single())
        assertEquals(Sent("key-1", NOW.plusSeconds(3), "ses_$ME"), second.sent.single())
    }

    @Test
    fun withdrawalRemovesOwnerItems() = runTest {
        val box = Box(this, userId = null, results = listOf(ok(), ok()))
        box.outbox.enqueue(FLAG, OTHER, request(BearerStatusChange.Rest, "withdrawn"))
        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.End, "mine", offset = 1))
        advanceUntilIdle()

        box.outbox.removeAll(OTHER)
        box.login(OTHER)
        assertTrue(box.outbox.pending(FLAG).isEmpty())
        box.login(ME)
        box.outbox.resume()
        advanceUntilIdle()
        assertEquals(listOf("mine"), box.sentKeys)
    }

    // ── 만료 ──

    @Test
    fun dropsItemsFromPreviousSeoulDay() = runTest {
        val box = Box(this, results = listOf(ok()))
        val events = box.record()

        // 서울 12시 기준 13시간 전 = 전날 23시(24시간 안).
        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "yesterday", offset = -13 * 3600))
        advanceUntilIdle()

        assertTrue(box.sent.isEmpty())
        assertEquals(BearerOutboxEvent.Rejected(BearerError.Stale()), events.last())
        assertTrue(box.outbox.pending(FLAG).isEmpty())
    }

    @Test
    fun dropsItemsOlderThan24Hours() = runTest {
        val box = Box(this, results = listOf(ok()))

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "old", offset = -25 * 3600))
        advanceUntilIdle()

        assertTrue(box.sent.isEmpty())
        assertTrue(box.outbox.pending(FLAG).isEmpty())
    }

    @Test
    fun expiryOnLoadNotifiesPending() = runTest {
        val first = Box(this, userId = null, file = file)
        first.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()

        val next = Box(this, file = file, deviceNow = NOW.plusSeconds(24 * 3600))
        val events = next.record()
        assertTrue(next.outbox.pending(FLAG).isEmpty())

        assertEquals(
            listOf(BearerOutboxEvent.Pending(emptyList()), BearerOutboxEvent.Rejected(BearerError.Stale())),
            events,
        )
        assertFalse(file.readText().contains("key-1"))
    }

    @Test
    fun expiryUsesServerClock() = runTest {
        val box = Box(this, results = listOf(ok()))
        // 기기는 오늘 12시지만 서버는 다음 날 1시다.
        box.serverClock.record(NOW.plusSeconds(13 * 3600))

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "device-now"))
        advanceUntilIdle()

        assertTrue(box.sent.isEmpty())
        assertTrue(box.outbox.pending(FLAG).isEmpty())
    }

    // ── 시계 오차 ──

    @Test
    fun clientAtUsesServerClockOffset() = runTest {
        val serverNow = NOW.minusSeconds(600)
        val client = RecordingClient(
            json = """
                {"code":"SUCCESS","message":"OK",
                "data":{"flagId":"$FLAG","status":"resting","zoneId":"zone_a","activity":"resting","updatedAt":"2026-11-24T03:00:00.000Z"}}
            """.trimIndent(),
            serverDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(serverNow.atZone(ZoneOffset.UTC)),
        )
        val serverClock = BearerServerClock(Clock.fixed(NOW, ZoneOffset.UTC))
        makeRepository(client, serverClock = serverClock).sendStatus(FLAG, request(BearerStatusChange.Rest, "key-0"))

        val useCase = BearerUseCaseImpl(ClaimRecordingRepository(), StubPackSyncService(null), clock = serverClock, outbox = UnusedOutbox())

        assertEquals(serverNow, useCase.makeStatusRequest(BearerStatusChange.Rest).clientAt)
    }

    @Test
    fun futureClientAtRewritesAndResendsWithSameKey() = runTest {
        // 기기 시계가 서버보다 10분 앞선다.
        lateinit var box: Box
        box = Box(this, deviceNow = NOW.plusSeconds(600), results = listOf({ box.futureRejected() }, ok()))

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1", offset = 600))
        advanceUntilIdle()

        assertEquals(listOf(Sent("key-1", NOW.plusSeconds(600), "ses_$ME"), Sent("key-1", NOW, "ses_$ME")), box.sent)
        assertTrue(box.outbox.pending(FLAG).isEmpty())
    }

    @Test
    fun futureClientAtKeepsLaterOfflineItems() = runTest {
        lateinit var box: Box
        box = Box(this, deviceNow = NOW.plusSeconds(600), results = listOf({ box.futureRejected() }, ok(), ok()))

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1", offset = 600))
        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.End, "key-2", offset = 630))
        advanceUntilIdle()

        assertEquals(
            listOf(
                Sent("key-1", NOW.plusSeconds(600), "ses_$ME"),
                Sent("key-1", NOW, "ses_$ME"),
                Sent("key-2", NOW.plusSeconds(30), "ses_$ME"),
            ),
            box.sent,
        )
    }

    @Test
    fun secondFutureRejectionDropsAsRejected() = runTest {
        lateinit var box: Box
        box = Box(this, deviceNow = NOW.plusSeconds(600), results = listOf({ box.futureRejected() }, { box.futureRejected() }))
        val events = box.record()

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1", offset = 600))
        advanceUntilIdle()

        assertEquals(2, box.sent.size)
        assertEquals(BearerOutboxEvent.Rejected(BearerError.InvalidRequest()), events.last())
        assertTrue(box.outbox.pending(FLAG).isEmpty())
    }

    @Test
    fun nonClockValidationStillRejects() = runTest {
        val box = Box(this, results = listOf(fail(BearerError.InvalidRequest())))
        val events = box.record()

        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()

        assertEquals(listOf("key-1"), box.sentKeys)
        assertEquals(BearerOutboxEvent.Rejected(BearerError.InvalidRequest()), events.last())
    }

    // ── 재개 시점 ──

    @Test
    fun resumesAfterLaunchWhenUserKnown() = runTest {
        Box(this, userId = null, file = file).outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()

        // 앱을 다시 켜고 AppCoordinator가 현재 사용자를 확인한다. 보드는 열지 않는다.
        val relaunched = Box(this, userId = null, file = file, results = listOf(ok()))
        val user = relaunched.startResumer()
        advanceUntilIdle()
        assertTrue(relaunched.sent.isEmpty())

        relaunched.login(ME)
        user.value = ME
        advanceUntilIdle()
        assertEquals(listOf("key-1"), relaunched.sentKeys)
    }

    @Test
    fun resumesOnLogin() = runTest {
        val box = Box(this, userId = null, results = listOf(ok()))
        val user = box.startResumer()
        box.outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()
        assertTrue(box.sent.isEmpty())

        box.login(ME)
        user.value = ME
        advanceUntilIdle()
        assertEquals(listOf("key-1"), box.sentKeys)
    }

    @Test
    fun doesNotResumeWithoutUser() = runTest {
        Box(this, userId = null, file = file).outbox.enqueue(FLAG, ME, request(BearerStatusChange.Rest, "key-1"))
        advanceUntilIdle()

        val relaunched = Box(this, userId = null, file = file, results = listOf(ok()))
        relaunched.startResumer()
        relaunched.reconnected.emit(Unit)
        advanceUntilIdle()

        assertTrue(relaunched.sent.isEmpty())
    }

    // ── 도우미 ──

    private data class Sent(
        val key: String,
        val clientAt: Instant,
        val sessionId: String,
    )

    /** 대기열 하나와 현재 사용자 · 세션 · 스크립트한 전송 결과. 세션은 `ses_<사용자>`다. */
    private inner class Box(
        private val scope: TestScope,
        userId: String? = ME,
        file: File? = null,
        deviceNow: Instant = NOW,
        results: List<suspend () -> BearerFlagStatus> = emptyList(),
    ) {
        var userId: String? = userId
        var sessionId: String? = userId?.let { "ses_$it" }
        var afterFirstSessionRead: (() -> Unit)? = null
        val serverClock = BearerServerClock(Clock.fixed(deviceNow, ZoneOffset.UTC))
        val sent = mutableListOf<Sent>()
        val sentKeys: List<String> get() = sent.map { it.key }
        val sleeps = mutableListOf<Duration>()
        val reconnected = MutableSharedFlow<Unit>()
        private val results = ArrayDeque(results)

        val outbox = BearerStatusOutboxImpl(
            file = file,
            // advanceUntilIdle은 backgroundScope 작업을 기다리지 않으므로 같은 스케줄러의 별도 스코프에서 보낸다.
            scope = CoroutineScope(StandardTestDispatcher(scope.testScheduler) + Job()),
            currentUserId = { this.userId },
            currentSessionId = {
                val current = sessionId
                afterFirstSessionRead?.let {
                    afterFirstSessionRead = null
                    it()
                }
                current
            },
            sendStatus = { _, request, session ->
                sent += Sent(request.idempotencyKey, request.clientAt, session)
                // 결과를 다 쓰면 iOS 테스트처럼 401로 멈춘다.
                this.results.removeFirstOrNull()?.invoke() ?: throw BearerError.NotAuthenticated()
            },
            serverClock = serverClock,
            sleep = {
                sleeps += it
                kotlinx.coroutines.delay(it)
            },
            io = UnconfinedTestDispatcher(scope.testScheduler),
        )

        fun login(user: String?) {
            userId = user
            sessionId = user?.let { "ses_$it" }
        }

        /** 서버 시각이 [NOW]인 400(미래 clientAt). 저장소처럼 응답 `Date`로 시계를 맞춘다. */
        fun futureRejected(): BearerFlagStatus {
            serverClock.record(NOW)
            throw BearerError.InvalidRequest()
        }

        fun record(): List<BearerOutboxEvent> {
            val events = mutableListOf<BearerOutboxEvent>()
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { outbox.events(FLAG).collect { events += it } }
            return events
        }

        fun startResumer(): MutableStateFlow<String?> {
            val user = MutableStateFlow(userId)
            BearerStatusOutboxResumer(outbox, user, reconnected)
                .start(CoroutineScope(scope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(scope.testScheduler)))
            return user
        }
    }

    private fun ok(): suspend () -> BearerFlagStatus = { RESTING }

    private fun fail(error: BearerError): suspend () -> BearerFlagStatus = { throw error }

    private fun request(
        change: BearerStatusChange,
        key: String,
        offset: Long = 0,
    ) = BearerStatusRequest(change, key, NOW.plusSeconds(offset))

    private companion object {
        const val FLAG = "flg_1"
        const val ME = "usr_me"
        const val OTHER = "usr_other"

        /** 서울 2026-11-24 12:00 */
        val NOW: Instant = Instant.parse("2026-11-24T03:00:00Z")

        val RESTING = BearerFlagStatus(FLAG, BearerFlagLifecycle.Resting, "zone_a", null, NOW)
    }
}
