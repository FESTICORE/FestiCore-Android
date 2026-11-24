package io.festicore.domain.bearer.sources.outbox

import android.util.AtomicFile
import io.festicore.core.logger.FestiCoreLogCategory
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.core.network.FestiCoreJson
import io.festicore.core.utility.date.AppDateFormat
import io.festicore.domain.bearer.bearer.BearerStatusOutbox
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerOutboxEvent
import io.festicore.domain.bearer.entity.BearerStatusRequest
import io.festicore.domain.bearer.sources.model.dto.BearerOutboxRecordDto
import io.festicore.domain.bearer.sources.model.dto.toItem
import io.festicore.domain.bearer.sources.model.dto.toRecord
import java.io.File
import java.io.FileNotFoundException
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer

/** 대기열 한 건. [clockAdjusted]는 시계 오차로 `clientAt`을 이미 한 번 당긴 건이다(다시 당기지 않는다). */
internal data class BearerOutboxItem(
    val flagId: String,
    val ownerUserId: String,
    val request: BearerStatusRequest,
    val clockAdjusted: Boolean = false,
)

/**
 * 기수 상태 변경을 파일에 남겨 두고 누른 시각 순서대로 한 건씩 보낸다. iOS `BearerStatusOutboxImpl`(actor → [Mutex]).
 * - 연결 없음: 5초마다, 429: 15초 뒤 다시 보낸다. 서버 오류: 2·4·8·16·32초 뒤 다시 보내고 그래도 안 되면 멈춘다.
 * - 로그인 없음 · 401 · 약관 동의 필요: 멈추고 알린다. 대기열은 남긴다. 멈춤은 [resume]으로만 푼다.
 * - 계정 격리(iOS와 다름): 세션 S → 사용자 U → 세션 재확인 뒤 주인이 U인 건만 S로 보낸다. 다른 계정 건은 주인이 돌아올 때까지 남긴다.
 *   2xx로 확정된 건만 지우고(그 사이 세션이 바뀌었으면 알리지 않음), 취소 · 결과 모름은 같은 멱등키 · `clientAt`으로 남긴다.
 * - 만료(iOS에 없음): 서버 시각 추정값과 서울 날짜가 다르거나 24시간이 지난 건은 버리고 `Rejected(Stale)`처럼 알린다.
 * - 시계 오차(iOS에 없음): 400이면서 `clientAt`이 서버 시각보다 5분 넘게 미래면 그 깃발 건을 같은 간격으로 당겨 같은 멱등키로 한 번 다시 보낸다.
 *
 * @param file 대기열 파일(`AtomicFile`). null이면 메모리에만 둔다.
 * @param scope 보내기 작업을 돌리는 앱 범위 스코프. 보드를 닫아도 이어 보낸다.
 * @param sendStatus 정한 세션으로만 보내는 상태 전송(`BearerRepositoryImpl.sendStatus(flagId, request, expectedSessionId)`).
 * @param serverClock 서버 시각 추정 시계(`BearerServerClock`).
 */
internal class BearerStatusOutboxImpl(
    private val file: File?,
    private val scope: CoroutineScope,
    private val currentUserId: () -> String?,
    private val currentSessionId: suspend () -> String?,
    private val sendStatus: suspend (flagId: String, request: BearerStatusRequest, expectedSessionId: String) -> BearerFlagStatus,
    private val serverClock: Clock,
    private val sleep: suspend (Duration) -> Unit = { delay(it) },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : BearerStatusOutbox {
    private val mutex = Mutex()
    private val items = mutableListOf<BearerOutboxItem>()
    private var isLoaded = false

    /** 한 번이라도 보내 본 건. 서버가 이미 반영했을 수 있으므로 성공하거나 버릴 때까지 그 앞에 새 변경을 끼워 넣지 않고 본문도 바꾸지 않는다. */
    private val attemptedIds = mutableSetOf<String>()

    /** 로그인 없음 · 401 · 포기로 멈췄다. 연결 복구 같은 자동 재개로는 풀지 않는다. */
    private var paused = false

    @Volatile private var worker: Job? = null

    private val flagEvents = MutableSharedFlow<FlagEvent>(extraBufferCapacity = EVENT_BUFFER)

    /** 중간에 기다리지 않고 바로 넣는다. 도착 순서가 바뀌어도 누른 시각 순서를 지킨다. */
    override suspend fun enqueue(
        flagId: String,
        ownerUserId: String,
        request: BearerStatusRequest,
    ) {
        mutex.withLock {
            loadIfNeeded()
            val start = items.indexOfLast { it.request.id in attemptedIds } + 1
            val index = (start until items.size).firstOrNull { items[it].request.clientAt > request.clientAt } ?: items.size
            items.add(index, BearerOutboxItem(flagId, ownerUserId, request))
            save()
            emitPending(flagId, ownerUserId)
            // 사용자가 누른 변경이라 멈춤을 풀고 보낸다(iOS는 넣을 때마다 작업을 시작한다).
            paused = false
            startWorker()
        }
    }

    override suspend fun pending(flagId: String): List<BearerStatusRequest> = mutex.withLock {
        loadIfNeeded()
        val userId = currentUserId() ?: return@withLock emptyList()
        items.filter { it.flagId == flagId && it.ownerUserId == userId }.map { it.request }
    }

    override fun events(flagId: String): Flow<BearerOutboxEvent> = flagEvents.filter { it.flagId == flagId }.map { it.event }

    override suspend fun resume() {
        mutex.withLock {
            loadIfNeeded()
            paused = false
            startWorker()
        }
    }

    /** 연결 복구 같은 자동 재개. 멈춘 대기열(`NeedsLogin` · `GaveUp`)은 이어 보내지 않는다. */
    suspend fun resumeAutomatically() {
        mutex.withLock {
            loadIfNeeded()
            if (!paused) startWorker()
        }
    }

    override fun cancelWork() {
        worker?.cancel()
        worker = null
    }

    override suspend fun removeAll(ownerUserId: String) {
        mutex.withLock {
            loadIfNeeded()
            val removed = items.filter { it.ownerUserId == ownerUserId }
            if (removed.isEmpty()) return@withLock
            items.removeAll(removed)
            attemptedIds.removeAll(removed.map { it.request.id }.toSet())
            save()
        }
    }

    // 잠금 안에서만 부른다.
    private fun startWorker() {
        if (worker?.isActive == true || items.isEmpty()) return
        worker = scope.launch { run() }
    }

    private suspend fun run() {
        var serverFailures = 0
        while (true) {
            // 세션 S → 사용자 U → 세션 재확인. 그 사이 계정이 바뀌면 보내지 않고 다시 고른다(iOS는 사용자 확인과 세션 읽기가 따로다).
            val sessionId = currentSessionId()
            val userId = currentUserId()
            val confirmedSessionId = currentSessionId()
            val pick = mutex.withLock {
                dropExpired(userId)
                val next = when {
                    items.isEmpty() -> Pick.Done

                    userId == null || sessionId == null -> {
                        paused = true
                        Pick.NeedsLogin(items.first().flagId)
                    }

                    confirmedSessionId != sessionId -> Pick.Retry

                    else -> items.firstOrNull { it.ownerUserId == userId }
                        ?.also { attemptedIds += it.request.id }
                        ?.let { Pick.Send(it, sessionId) }
                        ?: Pick.Done
                }
                if (next == Pick.Done || next is Pick.NeedsLogin) finishWorker()
                next
            }
            when (pick) {
                Pick.Done -> return

                Pick.Retry -> {
                    yield()
                    continue
                }

                is Pick.NeedsLogin -> {
                    emit(pick.flagId, BearerOutboxEvent.NeedsLogin)
                    return
                }

                is Pick.Send -> Unit
            }
            val item = pick.item
            val status = try {
                sendStatus(item.flagId, item.request, pick.sessionId)
            } catch (e: CancellationException) {
                // 계정 변경 등으로 취소됐다. 서버 반영 여부를 모르므로 주인 건으로 남긴다.
                throw e
            } catch (e: Exception) {
                when (handleFailure(item, e, serverFailures + 1)) {
                    Next.Continue -> {
                        if (e.isServerFailure()) serverFailures += 1
                        continue
                    }

                    Next.Reselect -> continue

                    Next.Stop -> return
                }
            }
            serverFailures = 0
            // 2xx는 서버가 주인 세션으로 이미 반영했으므로 취소와 관계없이 지운다.
            withContext(NonCancellable) {
                mutex.withLock {
                    remove(item)
                    // 그 사이 세션이 바뀌었으면 새 계정 보드에 이전 계정 결과를 알리지 않는다.
                    if (currentSessionId() == pick.sessionId) {
                        emitPending(item.flagId, item.ownerUserId)
                        emit(item.flagId, BearerOutboxEvent.Sent(item.request.id, status))
                    }
                }
            }
        }
    }

    private suspend fun handleFailure(
        item: BearerOutboxItem,
        error: Exception,
        nextServerFailures: Int,
    ): Next = when (error) {
        is BearerError.Offline -> {
            emit(item.flagId, BearerOutboxEvent.Offline)
            sleep(OFFLINE_DELAY)
            Next.Continue
        }

        is BearerError.RateLimited -> {
            sleep(RATE_LIMIT_DELAY)
            Next.Continue
        }

        is BearerError.NotAuthenticated -> {
            stop()
            emit(item.flagId, BearerOutboxEvent.NeedsLogin)
            Next.Stop
        }

        is BearerError.TermsRequired -> {
            stop()
            emit(item.flagId, BearerOutboxEvent.GaveUp(error))
            Next.Stop
        }

        is BearerError.InvalidRequest -> if (rewriteFutureClientAt(item)) Next.Reselect else reject(item, error)

        is BearerError -> if (error.isServerFailure()) backOff(item, error, nextServerFailures) else reject(item, error)

        else -> backOff(item, BearerError.Unavailable(), nextServerFailures)
    }

    /** 멈춘다. 멈춤은 [resume] · 새 변경으로만 푼다. */
    private suspend fun stop() = mutex.withLock {
        paused = true
        finishWorker()
    }

    /**
     * 잠금 안에서만 부른다. 끝나는 작업이 지금 작업이면 비워 바로 뒤 [enqueue] · [resume]이 새 작업을 시작하게 한다.
     * [cancelWork] 뒤 새로 시작한 작업은 지우지 않는다.
     */
    private suspend fun finishWorker() {
        if (worker === currentCoroutineContext()[Job]) worker = null
    }

    private suspend fun backOff(
        item: BearerOutboxItem,
        error: BearerError,
        failures: Int,
    ): Next {
        if (failures > MAX_SERVER_RETRIES) {
            stop()
            emit(item.flagId, BearerOutboxEvent.GaveUp(error))
            return Next.Stop
        }
        sleep((1L shl failures).seconds)
        return Next.Continue
    }

    /** 서버가 받지 않는 변경이면 뒤에 쌓인 같은 깃발 · 같은 주인의 변경도 그 상태를 전제로 하므로 함께 버린다. */
    private suspend fun reject(item: BearerOutboxItem, error: BearerError): Next {
        mutex.withLock {
            val removed = items.filter { it.flagId == item.flagId && it.ownerUserId == item.ownerUserId }
            items.removeAll(removed)
            attemptedIds.removeAll(removed.map { it.request.id }.toSet())
            save()
            emitPending(item.flagId, item.ownerUserId)
        }
        emit(item.flagId, BearerOutboxEvent.Rejected(error))
        return Next.Reselect
    }

    /**
     * 400의 원인이 기기 시계가 앞선 미래 `clientAt`인지 본다. 응답 `Date`로 시계를 막 맞췄으므로 지금 추정값이 그 응답 시각이다.
     * 그렇다면 그 깃발 건(결과를 모르는 다른 시도 건은 제외)의 `clientAt`을 같은 만큼 당겨 순서 · 간격을 지키고 저장한다. 한 건에 한 번만.
     * 400은 서버가 멱등 기록 전에 던지므로 같은 키로 바뀐 본문을 보내도 `IDEMPOTENCY_CONFLICT`가 아니다(`flags.service.ts:1101 · 1150`).
     */
    private suspend fun rewriteFutureClientAt(item: BearerOutboxItem): Boolean = mutex.withLock {
        val serverNow = serverClock.instant()
        if (item.clockAdjusted || !item.request.clientAt.isAfter(serverNow.plus(FUTURE_TOLERANCE))) return@withLock false
        val shift = java.time.Duration.between(serverNow, item.request.clientAt)
        items.replaceAll { other ->
            val rewritable = other.request.id == item.request.id || other.request.id !in attemptedIds
            if (other.flagId == item.flagId && rewritable) {
                other.copy(request = other.request.copy(clientAt = other.request.clientAt.minus(shift)), clockAdjusted = true)
            } else {
                other
            }
        }
        save()
        true
    }

    /** 잠금 안에서만 부른다. 서울 날짜가 다르거나 24시간이 지난 건을 버리고, 현재 사용자 건이면 최신 상태를 다시 읽도록 알린다. */
    private suspend fun dropExpired(userId: String?) {
        val now = serverClock.instant()
        val expired = items.filter { it.request.clientAt.isExpired(now) }
        if (expired.isEmpty()) return
        items.removeAll(expired)
        attemptedIds.removeAll(expired.map { it.request.id }.toSet())
        save()
        if (userId == null) return
        expired.filter { it.ownerUserId == userId }.map { it.flagId }.distinct().forEach { flagId ->
            emitPending(flagId, userId)
            emit(flagId, BearerOutboxEvent.Rejected(BearerError.Stale()))
        }
    }

    private fun Instant.isExpired(now: Instant): Boolean =
        atZone(AppDateFormat.timeZone).toLocalDate() != now.atZone(AppDateFormat.timeZone).toLocalDate() ||
            java.time.Duration.between(this, now) > MAX_AGE

    // 잠금 안에서만 부른다.
    private suspend fun remove(item: BearerOutboxItem) {
        items.removeAll { it.request.id == item.request.id && it.flagId == item.flagId }
        attemptedIds.remove(item.request.id)
        save()
    }

    private fun emit(flagId: String, event: BearerOutboxEvent) {
        flagEvents.tryEmit(FlagEvent(flagId, event))
    }

    /** [pending]과 같이 그 사용자의 변경만 알린다. */
    private fun emitPending(flagId: String, ownerUserId: String) {
        emit(flagId, BearerOutboxEvent.Pending(items.filter { it.flagId == flagId && it.ownerUserId == ownerUserId }.map { it.request }))
    }

    private suspend fun loadIfNeeded() {
        if (isLoaded) return
        isLoaded = true
        val file = file ?: return
        val records = withContext(io) {
            try {
                FestiCoreJson.decodeFromString(RECORDS, AtomicFile(file).readFully().decodeToString())
            } catch (_: FileNotFoundException) {
                emptyList()
            } catch (e: SerializationException) {
                logError("읽지", e)
                emptyList()
            } catch (e: IllegalArgumentException) {
                logError("읽지", e)
                emptyList()
            } catch (e: java.io.IOException) {
                logError("읽지", e)
                emptyList()
            }
        }
        items.addAll(0, records.mapNotNull { it.toItem() })
        dropExpired(currentUserId())
    }

    private suspend fun save() {
        val file = file ?: return
        val json = FestiCoreJson.encodeToString(RECORDS, items.map { it.toRecord() })
        withContext(io + NonCancellable) {
            val atomic = AtomicFile(file)
            try {
                file.parentFile?.mkdirs()
                val out = atomic.startWrite()
                try {
                    out.write(json.encodeToByteArray())
                    atomic.finishWrite(out)
                } catch (e: java.io.IOException) {
                    atomic.failWrite(out)
                    throw e
                }
            } catch (e: java.io.IOException) {
                // 파일에 남기지 못해도 메모리 대기열로 전송은 이어 간다.
                logError("저장하지", e)
            }
        }
    }

    // 파일 내용(멱등키 · 존 · 깃발 ID)은 남기지 않고 오류 종류만 남긴다.
    private fun logError(action: String, error: Exception) {
        FestiCoreLogger.error("기수 대기열 파일을 $action 못함: ${error::class.simpleName}", FestiCoreLogCategory.STORAGE)
    }

    private fun Exception.isServerFailure(): Boolean =
        this is BearerError.Unavailable || this is BearerError.InvalidResponse || this !is BearerError

    private data class FlagEvent(
        val flagId: String,
        val event: BearerOutboxEvent,
    )

    private sealed interface Pick {
        data object Done : Pick

        data object Retry : Pick

        data class NeedsLogin(val flagId: String) : Pick

        data class Send(
            val item: BearerOutboxItem,
            val sessionId: String,
        ) : Pick
    }

    /** [Next.Continue]는 기다린 뒤 다시 보내기(서버 오류면 횟수를 센다), [Next.Reselect]는 대기열이 바뀌어 처음부터 다시 고르기다. */
    private enum class Next { Continue, Reselect, Stop }

    companion object {
        val OFFLINE_DELAY = 5.seconds
        val RATE_LIMIT_DELAY = 15.seconds
        const val MAX_SERVER_RETRIES = 5

        /** 서버가 받는 미래 `clientAt` 한도(`flag-rules.ts:172`). */
        val FUTURE_TOLERANCE: java.time.Duration = java.time.Duration.ofMinutes(5)

        /** 서버 멱등 응답 보관 기간과 같은 대기 건 최대 나이 */
        val MAX_AGE: java.time.Duration = java.time.Duration.ofHours(24)

        /** 앱 `filesDir` 아래 대기열 파일 경로(iOS `Application Support/bearer-status-outbox.json`). */
        const val FILE_PATH = "festicore/bearer-status-outbox.json"

        private const val EVENT_BUFFER = 64
        private val RECORDS = ListSerializer(BearerOutboxRecordDto.serializer())
    }
}
